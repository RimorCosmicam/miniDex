package com.minidex.app.input.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.minidex.app.domain.model.CursorMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import io.github.muntashirakon.adb.AdbStream
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

enum class AdbConnectionStatus {
    DISCONNECTED,
    SEARCHING_MDNS,
    PAIRING,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Orchestrates on-device Wireless ADB connection, Shizuku bridge, and high-speed shell streaming.
 */
class AdbConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "AdbConnectionManager"
        private const val MDNS_CONNECT_TIMEOUT_MS = 12_000L
        private const val HID_REGISTER_DELAY_MS = 80
        private const val HID_CLICK_DELAY_MS = 8
        private const val HID_DEVICE_ID = 1
        const val DEFAULT_PORT = 5555

        // Standard relative USB mouse: 3 buttons, X/Y, vertical wheel, horizontal pan.
        private val HID_MOUSE_DESCRIPTOR = intArrayOf(
            0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x09, 0x01, 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
            0x95, 0x03, 0x75, 0x01, 0x81, 0x02, 0x95, 0x01, 0x75, 0x05,
            0x81, 0x01, 0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
            0x05, 0x0C, 0x0A, 0x38, 0x02, 0x15, 0x81, 0x25, 0x7F,
            0x75, 0x08, 0x95, 0x01, 0x81, 0x06, 0xC0, 0xC0
        )
    }

    val mdnsDiscovery = AdbMdnsDiscovery(context)
    val pairingClient = AdbPairingClient(context)

    private val _status = MutableStateFlow(AdbConnectionStatus.DISCONNECTED)
    val status: StateFlow<AdbConnectionStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Disconnected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    private var shizukuProcess: Process? = null
    private var shizukuOutputStream: OutputStream? = null

    private val hidLock = Any()
    private var hidProcess: Process? = null
    private var hidAdbStream: AdbStream? = null
    private var hidOutputStream: OutputStream? = null
    private var hidButtonMask = 0
    private var nativeHidProtocol = false
    @Volatile private var mouseService: IMouseControl? = null
    @Volatile private var binderWaiter: CompletableDeferred<IMouseControl>? = null
    @Volatile private var requestedCursorMode = CursorMode.AUTO_NATIVE

    private val isConnecting = AtomicBoolean(false)

    private val mouseBinderReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != PrivilegedMouseService.ACTION_BINDER_READY) return
            val container = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    PrivilegedMouseService.EXTRA_BINDER,
                    BinderContainer::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(PrivilegedMouseService.EXTRA_BINDER)
            } ?: return
            val service = IMouseControl.Stub.asInterface(container.binder) ?: return
            mouseService = service
            binderWaiter?.complete(service)
            Log.i(TAG, "Privileged UHID Binder received")
        }
    }

    init {
        val filter = IntentFilter(PrivilegedMouseService.ACTION_BINDER_READY)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(mouseBinderReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(mouseBinderReceiver, filter)
        }
        checkShizukuStatus()
        try {
            Shizuku.addBinderReceivedListenerSticky {
                checkShizukuStatus()
            }
            Shizuku.addRequestPermissionResultListener { _, grantResult ->
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    scope.launch { connect() }
                }
            }
        } catch (_: Throwable) {}
    }

    fun checkShizukuStatus(): Boolean {
        return try {
            val ping = Shizuku.pingBinder()
            val hasPerm = if (ping) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else false
            _isShizukuAvailable.value = ping
            hasPerm
        } catch (_: Throwable) {
            _isShizukuAvailable.value = false
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = 1001) {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    scope.launch { connect() }
                } else {
                    Shizuku.requestPermission(requestCode)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    private fun spawnShizukuProcess(cmd: Array<String>): Process? {
        return try {
            val method: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, cmd, null, null) as? Process
        } catch (e: Throwable) {
            Log.w(TAG, "spawnShizukuProcess reflection exception", e)
            null
        }
    }

    fun openWirelessDebuggingSettings() {
        val intents = listOf(
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$WirelessDebuggingActivity"
            ),
            Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun startMdnsDiscovery() {
        _status.value = AdbConnectionStatus.SEARCHING_MDNS
        _statusMessage.value = "Searching for Wireless Debugging..."
        mdnsDiscovery.startDiscovery()
    }

    fun stopMdnsDiscovery() {
        mdnsDiscovery.stopDiscovery()
        if (_status.value == AdbConnectionStatus.SEARCHING_MDNS) {
            _status.value = AdbConnectionStatus.DISCONNECTED
            _statusMessage.value = "Disconnected"
        }
    }

    suspend fun pairWithCode(
        port: Int,
        code: String,
        host: String = mdnsDiscovery.discoveredHost.value
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        _status.value = AdbConnectionStatus.PAIRING
        _statusMessage.value = "Pairing with code $code on port $port..."

        val result = pairingClient.pairDevice(host, port, code)
        if (result.isSuccess) {
            _statusMessage.value = "Paired successfully! Connecting..."
            Log.i(TAG, "Pairing succeeded! Attempting auto-connect...")

            // Pairing and connection use different randomized ports. Never reuse
            // the pairing port: wait for the connect service advertised by adbd.
            val connectPort = mdnsDiscovery.discoveredConnectPort.value
                ?: withTimeoutOrNull(MDNS_CONNECT_TIMEOUT_MS) {
                    mdnsDiscovery.discoveredConnectPort.filterNotNull().first()
                }

            if (connectPort == null) {
                val error = IllegalStateException(
                    "Paired, but the ADB connection port was not discovered. " +
                        "Keep Wireless debugging enabled and try Connect again."
                )
                _status.value = AdbConnectionStatus.ERROR
                _statusMessage.value = error.message.orEmpty()
                Result.failure(error)
            } else {
                connect(mdnsDiscovery.discoveredHost.value, connectPort)
            }
        } else {
            _status.value = AdbConnectionStatus.ERROR
            val err = result.exceptionOrNull()?.message ?: "Unknown pairing error"
            _statusMessage.value = "Pairing failed: $err"
            Result.failure(result.exceptionOrNull() ?: Exception(err))
        }
    }

    suspend fun connect(host: String = "127.0.0.1", port: Int = DEFAULT_PORT): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConnecting.compareAndSet(false, true)) {
            return@withContext Result.success(status.value == AdbConnectionStatus.CONNECTED)
        }

        try {
            _status.value = AdbConnectionStatus.CONNECTING
            _statusMessage.value = "Connecting to ADB on $host:$port..."

            // Priority 1: Shizuku if available and granted
            if (checkShizukuStatus()) {
                try {
                    Log.i(TAG, "Connecting via Shizuku binder process...")
                    val process = spawnShizukuProcess(arrayOf("sh"))
                    if (process != null) {
                        shizukuProcess = process
                        shizukuOutputStream = process.outputStream
                        grantOverlayPermission(useShizuku = true)
                        startCursorTransport(useShizuku = true)
                        _status.value = AdbConnectionStatus.CONNECTED
                        _statusMessage.value = "Connected via Shizuku"
                        isConnecting.set(false)
                        return@withContext Result.success(true)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Shizuku connection failed, falling back to TLS ADB", e)
                }
            }

            // Wireless Debugging is TLS ADB. Use the same libadb session and
            // identity that performed pairing; Dadb only speaks legacy TCP ADB.
            val connectResult = pairingClient.connectAdb(host, port)
            connectResult.getOrThrow()

            val testOutput = pairingClient.executeShell("echo minidex_adb_ready")
            check(testOutput.contains("minidex_adb_ready")) {
                "ADB connected but the shell readiness check failed"
            }
            Log.i(TAG, "ADB shell test output: $testOutput")

            grantOverlayPermission(useShizuku = false)
            startCursorTransport(useShizuku = false)

            _status.value = AdbConnectionStatus.CONNECTED
            _statusMessage.value = "Connected to ADB"
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "ADB connect error on $host:$port", e)
            _status.value = AdbConnectionStatus.ERROR
            _statusMessage.value = "Connect failed: ${e.message ?: "Could not connect"}"
            Result.failure(e)
        } finally {
            isConnecting.set(false)
        }
    }

    private suspend fun grantOverlayPermission(useShizuku: Boolean): Boolean {
        val packageName = context.packageName
        val command =
            "appops set '$packageName' android:system_alert_window allow 2>/dev/null || " +
                "appops set '$packageName' SYSTEM_ALERT_WINDOW allow"
        return runCatching {
            val output = if (useShizuku) {
                val process = spawnShizukuProcess(arrayOf("sh", "-c", command))
                    ?: return false
                val text = process.inputStream.bufferedReader().readText()
                check(process.waitFor() == 0) { text }
                text
            } else {
                pairingClient.executeShell(command)
            }
            Log.i(TAG, "Fake cursor overlay permission granted: ${output.trim()}")
            true
        }.getOrElse {
            Log.e(TAG, "Could not grant fake cursor overlay permission", it)
            false
        }
    }

    fun sendCommand(command: String): Boolean {
        if (_status.value != AdbConnectionStatus.CONNECTED) return false

        try {
            // Send to active persistent shizuku shell stream if available
            val formatted = if (command.endsWith("\n")) command else "$command\n"
            val bytes = formatted.toByteArray(Charsets.UTF_8)

            shizukuOutputStream?.let {
                it.write(bytes)
                it.flush()
                return true
            }

            // Fallback to one-shot TLS ADB shell execution.
            scope.launch(Dispatchers.IO) {
                try {
                    pairingClient.executeShell(command)
                } catch (e: Exception) {
                    Log.e(TAG, "Error running shell command: $command", e)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command to ADB stream: $command", e)
            disconnect()
        }
        return false
    }

    /** Sends relative motion through a real virtual HID mouse so Android renders its cursor. */
    fun sendHidPointerMove(dx: Int, dy: Int): Boolean = synchronized(hidLock) {
        mouseService?.let { service ->
            return@synchronized runCatching {
                check(service.isReady)
                service.moveCursor(dx, dy)
                true
            }.getOrElse {
                Log.w(TAG, "Privileged mouse movement failed", it)
                mouseService = null
                false
            }
        }
        if (hidOutputStream == null) return@synchronized false
        if (dx == 0 && dy == 0) return@synchronized true
        var remainingX = dx
        var remainingY = dy
        while (remainingX != 0 || remainingY != 0) {
            val stepX = remainingX.coerceIn(-127, 127)
            val stepY = remainingY.coerceIn(-127, 127)
            if (!writeHidReportLocked(hidButtonMask, stepX, stepY, 0, 0)) {
                closeHidMouseLocked()
                return@synchronized false
            }
            remainingX -= stepX
            remainingY -= stepY
        }
        true
    }

    fun setCursorMode(mode: CursorMode) {
        if (requestedCursorMode == mode) return
        requestedCursorMode = mode
        if (_status.value == AdbConnectionStatus.CONNECTED) {
            scope.launch(Dispatchers.IO) {
                startCursorTransport(useShizuku = shizukuProcess != null)
            }
        }
    }

    fun sendHidPointerButton(button: Int, isDown: Boolean): Boolean = synchronized(hidLock) {
        mouseService?.let { service ->
            return@synchronized runCatching {
                check(service.isReady)
                service.setButton(button, isDown)
                true
            }.getOrElse {
                Log.w(TAG, "Privileged mouse button failed", it)
                mouseService = null
                false
            }
        }
        if (hidOutputStream == null) return@synchronized false
        val bit = when (button) {
            2 -> 0x02
            3 -> 0x04
            else -> 0x01
        }
        hidButtonMask = if (isDown) hidButtonMask or bit else hidButtonMask and bit.inv()
        if (writeHidReportLocked(hidButtonMask, 0, 0, 0, 0)) true else {
            closeHidMouseLocked()
            false
        }
    }

    fun sendHidPointerClick(button: Int): Boolean = synchronized(hidLock) {
        mouseService?.let { service ->
            return@synchronized runCatching {
                check(service.isReady)
                service.setButton(button, true)
                Thread.sleep(50)
                service.setButton(button, false)
                true
            }.getOrElse {
                Log.w(TAG, "Privileged mouse click failed", it)
                mouseService = null
                false
            }
        }
        if (hidOutputStream == null) return@synchronized false
        val bit = when (button) {
            2 -> 0x02
            3 -> 0x04
            else -> 0x01
        }
        val originalMask = hidButtonMask
        val down = writeHidReportLocked(originalMask or bit, 0, 0, 0, 0)
        val delayed = down && if (nativeHidProtocol) {
            writeHidLineLocked("D $HID_CLICK_DELAY_MS")
        } else {
            writeHidJsonLocked(
                "{\"id\":$HID_DEVICE_ID,\"command\":\"delay\",\"duration\":$HID_CLICK_DELAY_MS}"
            )
        }
        val up = delayed && writeHidReportLocked(originalMask, 0, 0, 0, 0)
        if (!up) closeHidMouseLocked()
        up
    }

    fun sendHidScroll(horizontal: Int, vertical: Int): Boolean = synchronized(hidLock) {
        mouseService?.let { service ->
            return@synchronized runCatching {
                check(service.isReady)
                service.scroll(vertical, horizontal)
                true
            }.getOrElse {
                Log.w(TAG, "Privileged mouse scroll failed", it)
                mouseService = null
                false
            }
        }
        if (hidOutputStream == null) return@synchronized false
        var remainingH = horizontal
        var remainingV = vertical
        while (remainingH != 0 || remainingV != 0) {
            val stepH = remainingH.coerceIn(-127, 127)
            val stepV = remainingV.coerceIn(-127, 127)
            if (!writeHidReportLocked(hidButtonMask, 0, 0, stepV, stepH)) {
                closeHidMouseLocked()
                return@synchronized false
            }
            remainingH -= stepH
            remainingV -= stepV
        }
        true
    }

    private suspend fun startCursorTransport(useShizuku: Boolean): Boolean {
        // All previous compatibility modes were speculative. The supplied APK's
        // proven app_process + Binder + legacy UHID path is now authoritative.
        return startPrivilegedBinderMouse(useShizuku) || startPlatformHidMouse(useShizuku)
    }

    private suspend fun startPrivilegedBinderMouse(useShizuku: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            synchronized(hidLock) { closeHidMouseLocked() }
            try {
                val externalDir = checkNotNull(context.getExternalFilesDir(null)) {
                    "External files directory unavailable"
                }
                val stagingFile = File(externalDir, "libminidex_uhid.so")
                context.assets.open("libminidex_uhid.so").use { source ->
                    FileOutputStream(stagingFile).use { target -> source.copyTo(target) }
                }
                stagingFile.setReadable(true, false)

                val remoteLibrary = PrivilegedMouseService.NATIVE_LIBRARY_PATH
                val copyCommand =
                    "cp '${stagingFile.absolutePath}' '$remoteLibrary' && " +
                        "chmod 755 '$remoteLibrary' && test -r '$remoteLibrary' && echo READY"
                val copyOutput = if (useShizuku) {
                    val process = spawnShizukuProcess(arrayOf("sh", "-c", copyCommand))
                        ?: error("Could not start privileged library installer")
                    val output = process.inputStream.bufferedReader().readText()
                    check(process.waitFor() == 0) { output.ifBlank { "Library copy failed" } }
                    output
                } else {
                    pairingClient.executeShell(copyCommand)
                }
                check(copyOutput.contains("READY")) {
                    "Native library was not installed: ${copyOutput.trim()}"
                }
                stagingFile.delete()

                val waiter = CompletableDeferred<IMouseControl>()
                binderWaiter = waiter
                val sourceApk = context.applicationInfo.sourceDir
                val className = PrivilegedMouseService::class.java.name
                val launchCommand =
                    "export CLASSPATH='$sourceApk'; " +
                        "export LD_LIBRARY_PATH=/data/local/tmp:/system/lib64:/system/lib; " +
                        "exec /system/bin/app_process /system/bin '$className' '${context.packageName}'"

                if (useShizuku) {
                    val process = spawnShizukuProcess(arrayOf("sh", "-c", launchCommand))
                        ?: error("Could not launch privileged mouse service")
                    synchronized(hidLock) { hidProcess = process }
                    scope.launch(Dispatchers.IO) {
                        runCatching { drainHidOutput(process.inputStream) }
                    }
                } else {
                    val stream = pairingClient.openShellStream(launchCommand)
                    synchronized(hidLock) { hidAdbStream = stream }
                    scope.launch(Dispatchers.IO) {
                        runCatching { drainHidOutput(stream.openInputStream()) }
                    }
                }

                val service = withTimeoutOrNull(5_000L) { waiter.await() }
                    ?: error("Privileged mouse service did not return its Binder")
                check(service.isReady) { "Privileged mouse service failed its UHID health check" }
                synchronized(hidLock) {
                    mouseService = service
                    hidButtonMask = 0
                }
                Log.i(TAG, "Exact legacy UHID mouse is ready through Binder")
                true
            } catch (error: Throwable) {
                Log.e(TAG, "Exact privileged UHID mouse failed", error)
                synchronized(hidLock) { closeHidMouseLocked() }
                false
            } finally {
                binderWaiter = null
            }
        }

    private suspend fun startPlatformHidMouse(useShizuku: Boolean): Boolean = withContext(Dispatchers.IO) {
        synchronized(hidLock) {
            closeHidMouseLocked()
            try {
                if (useShizuku) {
                    val process = spawnShizukuProcess(arrayOf("sh", "-c", "hid -"))
                        ?: return@synchronized false
                    hidProcess = process
                    hidOutputStream = process.outputStream
                    scope.launch(Dispatchers.IO) {
                        runCatching { drainHidOutput(process.inputStream) }
                    }
                } else {
                    val stream = pairingClient.openShellStream("hid -")
                    hidAdbStream = stream
                    hidOutputStream = stream.openOutputStream()
                    scope.launch(Dispatchers.IO) {
                        runCatching { drainHidOutput(stream.openInputStream()) }
                    }
                }

                val descriptor = HID_MOUSE_DESCRIPTOR.joinToString(",")
                val registered = writeHidJsonLocked(
                    "{\"id\":$HID_DEVICE_ID,\"command\":\"register\",\"name\":\"MiniDex Virtual Mouse\",\"vid\":6353,\"pid\":20002,\"bus\":\"usb\",\"descriptor\":[$descriptor]}"
                ) && writeHidJsonLocked(
                    "{\"id\":$HID_DEVICE_ID,\"command\":\"delay\",\"duration\":$HID_REGISTER_DELAY_MS}"
                )
                if (!registered) closeHidMouseLocked()
                nativeHidProtocol = false
                registered
            } catch (e: Exception) {
                Log.w(TAG, "Virtual HID mouse is unavailable; using shell input fallback", e)
                closeHidMouseLocked()
                false
            }
        }
    }.also { ready ->
        if (ready) delay(HID_REGISTER_DELAY_MS.toLong())
    }

    private fun writeHidReportLocked(buttons: Int, dx: Int, dy: Int, wheel: Int, horizontalWheel: Int): Boolean {
        if (nativeHidProtocol) {
            return writeHidLineLocked("R $buttons $dx $dy $wheel $horizontalWheel")
        }
        return writeHidJsonLocked(
            "{\"id\":$HID_DEVICE_ID,\"command\":\"report\",\"report\":[${buttons and 0xFF},${dx and 0xFF},${dy and 0xFF},${wheel and 0xFF},${horizontalWheel and 0xFF}]}"
        )
    }

    private fun writeHidJsonLocked(json: String): Boolean {
        return writeHidLineLocked(json)
    }

    private fun writeHidLineLocked(line: String): Boolean {
        val output = hidOutputStream ?: return false
        return runCatching {
            output.write("$line\n".toByteArray(Charsets.UTF_8))
            output.flush()
            true
        }.getOrElse {
            Log.w(TAG, "Virtual HID mouse write failed", it)
            false
        }
    }

    private fun drainHidOutput(input: InputStream) {
        input.use {
            val buffer = ByteArray(1024)
            while (it.read(buffer) != -1) {
                // Keep the persistent shell's output window clear.
            }
        }
    }

    private fun closeHidMouseLocked() {
        runCatching { mouseService?.destroy() }
        mouseService = null
        runCatching { hidOutputStream?.close() }
        runCatching { hidAdbStream?.close() }
        runCatching { hidProcess?.destroy() }
        hidOutputStream = null
        hidAdbStream = null
        hidProcess = null
        hidButtonMask = 0
        nativeHidProtocol = false
    }

    suspend fun executeShellSync(command: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            if (checkShizukuStatus()) {
                val proc = spawnShizukuProcess(arrayOf("sh", "-c", command))
                proc?.inputStream?.bufferedReader()?.readText() ?: ""
            } else {
                pairingClient.executeShell(command)
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeShellSync failed for: $command", e)
            "Error: ${e.message}"
        }
    }

    fun disconnect() {
        synchronized(hidLock) { closeHidMouseLocked() }
        try {
            pairingClient.disconnect()
        } catch (_: Exception) {}

        try {
            shizukuOutputStream?.close()
            shizukuProcess?.destroy()
        } catch (_: Exception) {}
        shizukuOutputStream = null
        shizukuProcess = null

        _status.value = AdbConnectionStatus.DISCONNECTED
        _statusMessage.value = "Disconnected"
    }
}

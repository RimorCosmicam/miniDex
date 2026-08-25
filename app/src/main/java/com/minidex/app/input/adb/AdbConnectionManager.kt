package com.minidex.app.input.adb

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
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
        const val DEFAULT_PORT = 5555
    }

    val mdnsDiscovery = AdbMdnsDiscovery(context)
    val pairingClient = AdbPairingClient(context)

    private val _status = MutableStateFlow(AdbConnectionStatus.DISCONNECTED)
    val status: StateFlow<AdbConnectionStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Disconnected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    private var dadbInstance: Dadb? = null
    private var shizukuProcess: Process? = null
    private var shizukuOutputStream: OutputStream? = null

    private val isConnecting = AtomicBoolean(false)

    init {
        checkShizukuStatus()
        try {
            Shizuku.addBinderReceivedListenerSticky {
                checkShizukuStatus()
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
                Shizuku.requestPermission(requestCode)
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
            Intent("android.settings.WIFI_IP_SETTINGS"),
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
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

    suspend fun pairWithCode(port: Int, code: String, host: String = "127.0.0.1"): Result<Boolean> = withContext(Dispatchers.IO) {
        _status.value = AdbConnectionStatus.PAIRING
        _statusMessage.value = "Pairing with code $code on port $port..."

        val result = pairingClient.pair(host, port, code)
        if (result.isSuccess) {
            _statusMessage.value = "Paired successfully! Connecting..."
            Log.i(TAG, "Pairing succeeded! Attempting auto-connect...")

            // Wait briefly for daemon to register pairing and attempt connection
            kotlinx.coroutines.delay(1000)
            val connectPort = mdnsDiscovery.discoveredConnectPort.value ?: port
            connect(host, connectPort)
            Result.success(true)
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
                        _status.value = AdbConnectionStatus.CONNECTED
                        _statusMessage.value = "Connected via Shizuku (Zero-Latency)"
                        isConnecting.set(false)
                        return@withContext Result.success(true)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Shizuku connection failed, falling back to Dadb socket", e)
                }
            }

            // Priority 2: Direct Dadb TCP connection
            val keyPair = pairingClient.getAdbKeyPair()
            val dadb = Dadb.create(host, port, keyPair)
            dadbInstance = dadb

            // Test shell execution
            val testResp = dadb.shell("echo minidex_adb_ready")
            Log.i(TAG, "ADB shell test output: ${testResp.output}")

            _status.value = AdbConnectionStatus.CONNECTED
            _statusMessage.value = "Connected to ADB on $host:$port"
            isConnecting.set(false)
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "ADB connect error on $host:$port", e)
            _status.value = AdbConnectionStatus.ERROR
            _statusMessage.value = "Connect failed: ${e.message ?: "Could not connect"}"
            isConnecting.set(false)
            Result.failure(e)
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

            // Fallback to one-shot dadb shell execution
            dadbInstance?.let {
                scope.launch(Dispatchers.IO) {
                    try {
                        it.shell(command)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error running shell command: $command", e)
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command to ADB stream: $command", e)
            disconnect()
        }
        return false
    }

    suspend fun executeShellSync(command: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            if (checkShizukuStatus()) {
                val proc = spawnShizukuProcess(arrayOf("sh", "-c", command))
                proc?.inputStream?.bufferedReader()?.readText() ?: ""
            } else {
                dadbInstance?.shell(command)?.output ?: ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeShellSync failed for: $command", e)
            "Error: ${e.message}"
        }
    }

    fun disconnect() {
        try {
            dadbInstance?.close()
        } catch (_: Exception) {}
        dadbInstance = null

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

package com.minidex.app.input.adb

import android.content.Intent
import android.app.ActivityManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.os.Process
import android.util.Log

/** Entry point launched by app_process as the ADB shell user. */
class PrivilegedMouseService private constructor() : IMouseControl.Stub() {
    private var ready = false
    private var mouseReady = false
    private var nativeLibraryLoaded = false
    private var buttons = 0
    private val handler = Handler(Looper.getMainLooper())
    private var launchGuardGeneration = 0
    private var appPackageName = ""

    private external fun nativeCreateMouse(): Boolean
    private external fun nativeSendReport(
        buttons: Int,
        deltaX: Int,
        deltaY: Int,
        wheel: Int
    ): Boolean
    private external fun nativeDestroyMouse()

    override fun isReady(): Boolean = ready

    override fun moveCursor(deltaX: Int, deltaY: Int) {
        if (!mouseReady) return
        var x = deltaX
        var y = deltaY
        while (x != 0 || y != 0) {
            val stepX = x.coerceIn(-127, 127)
            val stepY = y.coerceIn(-127, 127)
            if (!nativeSendReport(buttons, stepX, stepY, 0)) {
                mouseReady = false
                return
            }
            x -= stepX
            y -= stepY
        }
    }

    override fun setButton(button: Int, pressed: Boolean) {
        if (!mouseReady) return
        val bit = when (button) {
            2 -> 0x02
            3 -> 0x04
            else -> 0x01
        }
        buttons = if (pressed) buttons or bit else buttons and bit.inv()
        if (!nativeSendReport(buttons, 0, 0, 0)) mouseReady = false
    }

    override fun scroll(vertical: Int, horizontal: Int) {
        if (!mouseReady || vertical == 0) return
        var remaining = vertical
        while (remaining != 0) {
            val step = remaining.coerceIn(-127, 127)
            if (!nativeSendReport(buttons, 0, 0, step)) {
                mouseReady = false
                return
            }
            remaining -= step
        }
    }

    override fun guardNextLaunch(displayId: Int) {
        if (displayId < 0) return
        val before = getRunningTasks()
        val beforeIds = before.mapTo(HashSet()) { it.taskId }
        val beforeFocusedId = before.firstOrNull(::taskIsFocused)?.taskId ?: -1
        val generation = ++launchGuardGeneration
        longArrayOf(250L, 600L, 1_100L).forEach { delayMs ->
            handler.postDelayed({
                if (generation != launchGuardGeneration) return@postDelayed
                val tasks = getRunningTasks()
                val candidate = tasks.firstOrNull { task ->
                    taskDisplayId(task) != displayId &&
                        task.topActivity?.packageName != appPackageName &&
                        (task.taskId !in beforeIds ||
                            (taskIsFocused(task) && task.taskId != beforeFocusedId))
                }
                if (candidate != null && moveTaskToDisplay(candidate.taskId, displayId)) {
                    launchGuardGeneration++
                    Log.i(
                        TAG,
                        "Moved task ${candidate.taskId} from display ${taskDisplayId(candidate)} to $displayId"
                    )
                }
            }, delayMs)
        }
    }

    private fun reflectedField(instance: Any, name: String): Any? {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            val currentType = type
            val value = runCatching {
                currentType.getDeclaredField(name).apply { isAccessible = true }.get(instance)
            }
            if (value.isSuccess) return value.getOrNull()
            type = currentType.superclass
        }
        return null
    }

    private fun taskDisplayId(task: ActivityManager.RunningTaskInfo): Int =
        reflectedField(task, "displayId") as? Int ?: -1

    private fun taskIsFocused(task: ActivityManager.RunningTaskInfo): Boolean =
        reflectedField(task, "isFocused") as? Boolean ?: false

    private fun activityTaskManagerService(): Any {
        return Class.forName("android.app.ActivityTaskManager")
            .getDeclaredMethod("getService")
            .invoke(null)
    }

    private fun getRunningTasks(): List<ActivityManager.RunningTaskInfo> {
        return runCatching {
            val service = activityTaskManagerService()
            val method = Class.forName("android.app.IActivityTaskManager")
                .methods
                .filter { it.name == "getTasks" }
                .maxByOrNull { it.parameterCount }
                ?: return emptyList()
            var integerIndex = 0
            val args = method.parameterTypes.map { type ->
                when (type) {
                    Int::class.javaPrimitiveType -> {
                        if (integerIndex++ == 0) 32 else -1
                    }
                    Boolean::class.javaPrimitiveType -> false
                    else -> null
                }
            }.toTypedArray()
            @Suppress("UNCHECKED_CAST")
            method.invoke(service, *args) as? List<ActivityManager.RunningTaskInfo> ?: emptyList()
        }.getOrElse {
            Log.e(TAG, "Could not read running tasks", it)
            emptyList()
        }
    }

    private fun moveTaskToDisplay(taskId: Int, displayId: Int): Boolean {
        val movedByBinder = runCatching {
            val service = activityTaskManagerService()
            val method = Class.forName("android.app.IActivityTaskManager")
                .getMethod(
                    "moveRootTaskToDisplay",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            method.invoke(service, taskId, displayId)
            true
        }.getOrElse {
            Log.w(TAG, "Binder task move failed; trying shell command", it)
            false
        }
        if (movedByBinder) return true

        val commands = listOf(
            "am display move-stack $taskId $displayId",
            "cmd activity display move-root-task $taskId $displayId"
        )
        return commands.any { command ->
            runCatching {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command)).waitFor() == 0
            }.getOrDefault(false)
        }.also { moved ->
            if (!moved) Log.e(TAG, "Could not move task $taskId to display $displayId")
        }
    }

    override fun destroy() {
        ready = false
        mouseReady = false
        if (nativeLibraryLoaded) runCatching { nativeDestroyMouse() }
        Looper.getMainLooper().quitSafely()
    }

    private fun initialize(): Boolean {
        mouseReady = runCatching {
            System.load(NATIVE_LIBRARY_PATH)
            nativeLibraryLoaded = true
            nativeCreateMouse()
        }.getOrElse {
            // Task placement is independent of UHID. Keep the shell Binder alive
            // even when a device rejects creation of the optional native mouse.
            Log.w(TAG, "UHID mouse unavailable; task placement remains active", it)
            false
        }
        ready = true
        return true
    }

    private fun sendBinder(packageName: String) {
        val intent = Intent(ACTION_BINDER_READY)
            .setPackage(packageName)
            .putExtra(EXTRA_BINDER, BinderContainer(asBinder()))
        sendSystemBroadcast(intent)
    }

    private fun sendSystemBroadcast(intent: Intent) {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val activityBinder = serviceManager
            .getDeclaredMethod("getService", String::class.java)
            .invoke(null, "activity") as IBinder
        val activityManager = Class.forName("android.app.IActivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, activityBinder)
        val appThread = Class.forName("android.app.IApplicationThread")
        val intentReceiver = Class.forName("android.content.IIntentReceiver")
        val method = Class.forName("android.app.IActivityManager").getDeclaredMethod(
            "broadcastIntent",
            appThread,
            Intent::class.java,
            String::class.java,
            intentReceiver,
            Int::class.javaPrimitiveType,
            String::class.java,
            android.os.Bundle::class.java,
            Array<String>::class.java,
            Int::class.javaPrimitiveType,
            android.os.Bundle::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.invoke(
            activityManager,
            null,
            intent,
            null,
            null,
            -1,
            null,
            null,
            null,
            0,
            null,
            false,
            false,
            -1
        )
    }

    companion object {
        const val ACTION_BINDER_READY = "com.minidex.app.MOUSE_BINDER_READY"
        const val EXTRA_BINDER = "mouse_binder"
        const val NATIVE_LIBRARY_PATH = "/data/local/tmp/libminidex_uhid.so"
        private const val TAG = "MiniDexMouseService"

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val uid = Process.myUid()
                require(uid == Process.SHELL_UID || uid == 0) {
                    "Mouse service must run as shell/root; uid=$uid"
                }
                require(args.isNotEmpty()) { "Missing target package" }
                if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
                val service = PrivilegedMouseService()
                service.appPackageName = args[0]
                check(service.initialize()) { "UHID creation failed" }
                service.sendBinder(args[0])
                Log.i(TAG, "READY")
                Looper.loop()
            } catch (error: Throwable) {
                Log.e(TAG, "START_FAILED", error)
                System.err.println("START_FAILED ${error.javaClass.simpleName}: ${error.message}")
                kotlin.system.exitProcess(1)
            }
        }
    }
}

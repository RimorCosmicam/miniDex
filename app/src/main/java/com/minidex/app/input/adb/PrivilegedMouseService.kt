package com.minidex.app.input.adb

import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log

/** Entry point launched by app_process as the ADB shell user. */
class PrivilegedMouseService private constructor() : IMouseControl.Stub() {
    private var ready = false
    private var buttons = 0

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
        if (!ready) return
        var x = deltaX
        var y = deltaY
        while (x != 0 || y != 0) {
            val stepX = x.coerceIn(-127, 127)
            val stepY = y.coerceIn(-127, 127)
            if (!nativeSendReport(buttons, stepX, stepY, 0)) {
                ready = false
                return
            }
            x -= stepX
            y -= stepY
        }
    }

    override fun setButton(button: Int, pressed: Boolean) {
        if (!ready) return
        val bit = when (button) {
            2 -> 0x02
            3 -> 0x04
            else -> 0x01
        }
        buttons = if (pressed) buttons or bit else buttons and bit.inv()
        if (!nativeSendReport(buttons, 0, 0, 0)) ready = false
    }

    override fun scroll(vertical: Int, horizontal: Int) {
        if (!ready || vertical == 0) return
        var remaining = vertical
        while (remaining != 0) {
            val step = remaining.coerceIn(-127, 127)
            if (!nativeSendReport(buttons, 0, 0, step)) {
                ready = false
                return
            }
            remaining -= step
        }
    }

    override fun destroy() {
        ready = false
        nativeDestroyMouse()
        Looper.getMainLooper().quitSafely()
    }

    private fun initialize(): Boolean {
        System.load(NATIVE_LIBRARY_PATH)
        ready = nativeCreateMouse()
        return ready
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

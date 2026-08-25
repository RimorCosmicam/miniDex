package com.minidex.app.input.ime

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Official Android Input Method Service (IME) for MiniDex.
 * Enables direct, 100% native typing and shortcut dispatch to any window focused on Samsung DeX.
 */
class MiniDexInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "MiniDexIME"
        var instance: MiniDexInputMethodService? = null
            private set

        fun isImeActive(): Boolean = instance != null

        fun commitText(text: String): Boolean {
            val ic = instance?.currentInputConnection ?: return false
            return ic.commitText(text, 1)
        }

        fun sendKeyEvent(keyCode: Int, metaState: Int = 0): Boolean {
            val ic = instance?.currentInputConnection ?: return false
            val down = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
            val up = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState)
            ic.sendKeyEvent(down)
            ic.sendKeyEvent(up)
            return true
        }

        fun sendDelete(): Boolean {
            val ic = instance?.currentInputConnection ?: return false
            return ic.deleteSurroundingText(1, 0)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "MiniDex IME service created")
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        instance = this
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        instance = this
    }

    override fun onCreateInputView(): View? {
        return null // Input view is handled on the cover screen by MainCoverActivity
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}

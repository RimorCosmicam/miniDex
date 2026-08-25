package com.minidex.app

import android.app.Application
import android.util.Log

class MiniDexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("MiniDexApplication", "MiniDex Application Started")
    }
}

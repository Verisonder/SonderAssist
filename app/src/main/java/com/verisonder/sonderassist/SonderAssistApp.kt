package com.verisonder.sonderassist

import android.app.Application

class SonderAssistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

package com.qtekfun.ultimatephone.spike

import android.app.Application
import android.os.Process

class SpikeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLog.init(this)
        // A new line here after "remove from recents" proves the system restarted the process to handle a call.
        EventLog.add("app", "process started pid=${Process.myPid()}")
    }
}

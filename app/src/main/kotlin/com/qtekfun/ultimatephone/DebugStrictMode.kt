package com.qtekfun.ultimatephone

import android.os.StrictMode

/**
 * Development aid, active in debug builds only. Logs (never crashes) disk and network access on the main thread and
 * leaked closeables or SQLite objects, so a regression of the start-up budget shows in `adb logcat -s StrictMode`.
 * Release builds skip it: `BuildConfig.DEBUG` is a compile-time false there and R8 removes the body.
 */
object DebugStrictMode {
    fun install() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
    }
}

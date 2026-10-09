package com.qtekfun.ultimatephone.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

/**
 * A crash log that never leaves the phone. When the app dies from an uncaught exception, the trace is written to a file
 * (phone numbers masked), and the next start shows it in [CrashReportActivity] with Copy and Share buttons, so a crash can
 * be reported without a computer. The same file is copied to the app's external files directory, where
 * `adb pull /sdcard/Android/data/<package>/files/last-crash.txt` can fetch it. Nothing is sent anywhere.
 */
object CrashReporter {
    private const val FILE = "last-crash.txt"

    // Runs of 6 or more digits, with the usual separators: a phone number must not end up in a log file.
    private val NUMBER = Regex("""\+?\d[\d\s().-]{4,}\d""")

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, format(app, thread.name, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report, or null when the last run did not crash. */
    fun pending(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
        runCatching { context.getExternalFilesDir(null)?.let { File(it, FILE).delete() } }
    }

    /** Pure and testable: numbers become `[number]`. */
    internal fun sanitize(text: String): String = text.replace(NUMBER, "[number]")

    /** What identifies the run that crashed; everything that is not the stack trace itself. */
    internal data class Run(val appVersion: String, val versionCode: Long, val androidRelease: String, val sdk: Int, val device: String, val thread: String)

    /** The header is ours and safe; only the trace, which can quote exception messages, is scrubbed of numbers. */
    internal fun format(run: Run, trace: String, at: Instant): String = buildString {
        appendLine("UltimatePhone ${run.appVersion} (${run.versionCode})")
        appendLine("Android ${run.androidRelease} (SDK ${run.sdk})")
        appendLine("Device: ${run.device}")
        appendLine("Thread: ${run.thread}")
        appendLine("Time: $at")
        appendLine()
        append(sanitize(trace))
    }

    private fun format(context: Context, thread: String, error: Throwable): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val run = Run(
            appVersion = info?.versionName.orEmpty(),
            versionCode = info?.longVersionCode ?: -1,
            androidRelease = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            thread = thread
        )
        return format(run, error.stackTraceToString(), Instant.now())
    }

    private fun write(context: Context, text: String) {
        File(context.filesDir, FILE).writeText(text)
        runCatching { context.getExternalFilesDir(null)?.let { File(it, FILE).writeText(text) } }
    }
}

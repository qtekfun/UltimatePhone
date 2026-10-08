package com.qtekfun.ultimatephone.spike

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArraySet

/**
 * In-process event log shown on the spike screen and appended to `files/spike-log.txt`, so it survives the process
 * being killed. Callers pass already-masked numbers (see [PhoneMask]).
 */
object EventLog {
    private const val MAX_LINES = 800
    private val format = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
    private val lines = ArrayDeque<String>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val target = File(context.applicationContext.filesDir, "spike-log.txt")
        file = target
        if (target.exists()) {
            val kept = target.readLines()
            kept.takeLast(MAX_LINES).forEach { lines.addLast(it) }
            if (kept.size > MAX_LINES) target.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }

    fun add(tag: String, message: String) {
        val line = "${format.format(LocalDateTime.now())} [$tag] $message"
        synchronized(this) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            file?.appendText(line + "\n")
        }
        main.post { listeners.forEach { it() } }
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        file?.writeText("")
        main.post { listeners.forEach { it() } }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}

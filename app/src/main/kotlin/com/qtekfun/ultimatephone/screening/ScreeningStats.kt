package com.qtekfun.ultimatephone.screening

/** The largest of the last [capacity] samples; memory only, nothing about the numbers is kept. */
class RollingMax(private val capacity: Int = DEFAULT_CAPACITY) {
    private val samples = LongArray(capacity)
    private var count = 0
    private var next = 0

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun add(value: Long) {
        samples[next] = value
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** Null until the first sample. */
    @Synchronized
    fun max(): Long? = if (count == 0) null else (0 until count).maxOf { samples[it] }

    companion object {
        const val DEFAULT_CAPACITY = 50
    }
}

/** How long recent screenings took, to check we stay far below the system's deadline. In memory only. */
class ScreeningStats(private val rolling: RollingMax = RollingMax()) {
    @Volatile
    var lastMillis: Long? = null
        private set

    fun record(elapsedMillis: Long) {
        lastMillis = elapsedMillis
        rolling.add(elapsedMillis)
    }

    /** Slowest of the recent screenings, or null if none ran since the process started. */
    fun slowestRecentMillis(): Long? = rolling.max()
}

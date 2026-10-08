package com.qtekfun.ultimatephone.spike

import android.content.Context
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Test 8: schedules the same kind of job the real app will run daily, and logs when it really ran. */
object BackgroundProbe {
    private const val UNIQUE_DAILY = "spike-daily"
    private const val UNIQUE_FAST = "spike-15min"
    private const val FAST_MINUTES = 15L

    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<DownloadWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
        EventLog.add("bg", "scheduled daily job, Wi-Fi only")
    }

    fun scheduleFast(context: Context) {
        val request = PeriodicWorkRequestBuilder<DownloadWorker>(FAST_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_FAST, ExistingPeriodicWorkPolicy.UPDATE, request)
        EventLog.add("bg", "scheduled $FAST_MINUTES-minute job, any network")
    }

    fun runOnce(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<DownloadWorker>().build())
        EventLog.add("bg", "queued one-off job")
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_DAILY)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_FAST)
        EventLog.add("bg", "cancelled scheduled jobs")
    }
}

/** Downloads a few hundred bytes over HTTPS. The point is the execution time and standby bucket, not the data. */
class DownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        EventLog.init(applicationContext)
        val started = SystemClock.elapsedRealtime()
        val bucket = CapabilityProbe.standbyBucketName(applicationContext)
        return try {
            val connection = URL(SAMPLE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val bytes = try {
                connection.inputStream.use { it.readBytes().size }
            } finally {
                connection.disconnect()
            }
            val took = SystemClock.elapsedRealtime() - started
            EventLog.add("bg", "ran ok bytes=$bytes took=${took}ms attempt=$runAttemptCount bucket=$bucket")
            Result.success()
        } catch (e: java.io.IOException) {
            EventLog.add("bg", "ran with network error ${e.javaClass.simpleName} attempt=$runAttemptCount bucket=$bucket")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val SAMPLE_URL = "https://raw.githubusercontent.com/qtekfun/UltimatePhone/main/.gitignore"
        const val TIMEOUT_MS = 15_000
        const val MAX_ATTEMPTS = 3
    }
}

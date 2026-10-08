package com.qtekfun.ultimatephone.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Gives the worker (which WorkManager creates, not Hilt) access to the singletons it needs. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataWorkerEntryPoint {
    fun dataManager(): DataManager
}

/**
 * The daily update: manifest, packs of the selected regions and custom sources. Network traffic only goes to the manifest
 * host, the pack URLs of the signed manifest and the user's own source URLs, and carries no phone number.
 */
class DataUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(applicationContext, DataWorkerEntryPoint::class.java).dataManager()
        return WorkResults.of(manager.updateAll(), runAttemptCount)
    }
}

/** How an [UpdateOutcome] ends a work run. */
object WorkResults {
    const val MAX_ATTEMPTS = 5

    enum class Kind { SUCCESS, RETRY, FAILURE }

    fun kind(outcome: UpdateOutcome, attempt: Int): Kind = when (outcome) {
        is UpdateOutcome.Success -> Kind.SUCCESS
        is UpdateOutcome.Retry, is UpdateOutcome.Blocked -> if (attempt + 1 < MAX_ATTEMPTS) Kind.RETRY else Kind.FAILURE
        is UpdateOutcome.Failed -> Kind.FAILURE
    }

    fun of(outcome: UpdateOutcome, attempt: Int): androidx.work.ListenableWorker.Result = when (kind(outcome, attempt)) {
        Kind.SUCCESS -> androidx.work.ListenableWorker.Result.success()
        Kind.RETRY -> androidx.work.ListenableWorker.Result.retry()
        // A failed periodic run does not cancel the schedule: the next day's run starts again.
        Kind.FAILURE -> androidx.work.ListenableWorker.Result.failure()
    }
}

/** What the app asks of the scheduler. */
interface UpdateScheduler {
    /** Makes the daily work match the settings: scheduled when automatic updates are on, cancelled otherwise. */
    fun apply(settings: DataSettings)

    /** Runs one update as soon as the constraints allow, for data that has gone stale. */
    fun runSoon(settings: DataSettings)
}

class WorkManagerUpdateScheduler(private val context: Context) : UpdateScheduler {
    override fun apply(settings: DataSettings) {
        val work = WorkManager.getInstance(context)
        if (!settings.autoUpdate) {
            work.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DataUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints(settings.wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        // UPDATE keeps the schedule's phase when only the constraints changed.
        work.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun runSoon(settings: DataSettings) {
        val request = OneTimeWorkRequestBuilder<DataUpdateWorker>()
            .setConstraints(constraints(settings.wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(EXPEDITED_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val PERIODIC_NAME = "data-update"
        const val EXPEDITED_NAME = "data-update-expired"
        private const val BACKOFF_MINUTES = 15L

        fun networkType(wifiOnly: Boolean): NetworkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        fun constraints(wifiOnly: Boolean): Constraints = Constraints.Builder().setRequiredNetworkType(networkType(wifiOnly)).build()
    }
}

/** App start: schedules the daily work and, when the data is stale, asks for an update right away. */
class DataStartup(
    private val settings: DataSettingsRepository,
    private val manager: DataManager,
    private val scheduler: UpdateScheduler,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun run() {
        val current = settings.current()
        scheduler.apply(current)
        manager.load()
        if (UpdatePlanner.shouldRunAtStart(current, manager.catalog.value.installed, clock())) scheduler.runSoon(current)
    }
}

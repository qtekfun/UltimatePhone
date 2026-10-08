package com.qtekfun.ultimatephone.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncRepository(): SyncRepository
}

/**
 * Runs one sync in the background. Plain WorkManager worker that reaches Hilt through an entry point, so it works
 * with the default WorkManager initialisation.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerEntryPoint::class.java).syncRepository()
        val result = repository.syncIfEnabled() ?: return Result.success()
        // Only failures that can fix themselves are retried with WorkManager's backoff. A wrong password waits for the user.
        return if (result.error?.isRetryable == true) Result.retry() else Result.success()
    }
}

/**
 * Keeps sync running while it is turned on: a periodic job about every 6 hours (when online) and a sync a short time
 * after the lists change. Changes made offline stay in the lists and are uploaded by the first sync that connects.
 */
class SyncScheduler(
    private val context: Context,
    private val repository: SyncRepository,
    private val lists: SpamListsRepository,
    private val scope: CoroutineScope
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        scope.launch {
            repository.state.map { it.enabled && it.isConfigured }.distinctUntilChanged().flatMapLatest { active ->
                if (active) {
                    schedulePeriodic()
                    enqueueNow()
                    combine(lists.observe(ListType.OWN), lists.observe(ListType.WHITELIST)) { _, _ -> Unit }.debounce(CHANGE_DELAY_MS)
                } else {
                    cancelAll()
                    flowOf<Unit>()
                }
            }.collect {
                if (repository.refreshPending()) enqueueNow()
            }
        }
    }

    /** Asks for a sync as soon as the network allows (used after enabling and after a manual save). */
    fun requestSync() = enqueueNow()

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(connected())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun enqueueNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connected())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        // Appended, not replaced: a change that lands while a sync runs gets its own run right after it.
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun cancelAll() {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(PERIODIC_NAME)
        manager.cancelUniqueWork(ONCE_NAME)
    }

    private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private companion object {
        const val PERIODIC_NAME = "ultimatephone-sync-periodic"
        const val ONCE_NAME = "ultimatephone-sync-once"
        const val PERIOD_HOURS = 6L
        const val BACKOFF_MINUTES = 5L
        const val CHANGE_DELAY_MS = 15_000L
    }
}

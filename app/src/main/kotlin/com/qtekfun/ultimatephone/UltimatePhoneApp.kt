package com.qtekfun.ultimatephone

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.LibPhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.CallerLabel
import com.qtekfun.ultimatephone.core.telecom.CallerLabelResolver
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.TelecomCalls
import com.qtekfun.ultimatephone.crash.CrashReportActivity
import com.qtekfun.ultimatephone.crash.CrashReporter
import com.qtekfun.ultimatephone.data.BusinessFinder
import com.qtekfun.ultimatephone.data.CallerLabelProvider
import com.qtekfun.ultimatephone.data.DataStartup
import com.qtekfun.ultimatephone.data.DefaultInstalledPackLookups
import com.qtekfun.ultimatephone.di.ApplicationScope
import com.qtekfun.ultimatephone.recording.RecordingCoordinator
import com.qtekfun.ultimatephone.screening.DecisionEngine
import com.qtekfun.ultimatephone.sync.SyncScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Process start. The main thread does the bare minimum: Hilt builds only the application scope, everything else is a
 * [Lazy] that the background warm-up (or the first real user) creates. Nothing here opens a database, reads a
 * DataStore, loads libphonenumber or touches the network before the first frame.
 *
 * A call that arrives straight after the process starts is still handled: the in-call service and the screening
 * service reach the same singletons, which build what they need on first use (see `DefaultInstalledPackLookups` and
 * `LazyPrefixRules`); the warm-up only moves that cost off the critical path.
 */
@HiltAndroidApp
class UltimatePhoneApp : Application() {
    @Inject
    lateinit var contacts: Lazy<ContactsRepository>

    @Inject
    lateinit var businesses: Lazy<BusinessFinder>

    @Inject
    lateinit var lookups: Lazy<DefaultInstalledPackLookups>

    @Inject
    lateinit var dataStartup: Lazy<DataStartup>

    @Inject
    lateinit var syncScheduler: Lazy<SyncScheduler>

    @Inject
    lateinit var recordingCoordinator: Lazy<RecordingCoordinator>

    @Inject
    lateinit var engine: Lazy<DecisionEngine>

    @Inject
    lateinit var regions: Lazy<RegionProvider>

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // First thing, before Hilt builds anything: if the process dies, the next start can show why.
        CrashReporter.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        showPreviousCrash()
        DebugStrictMode.install()
        // Callers are named from the contacts first, then from the installed business packs. The spam verdict plugs in later.
        // Only the wiring is done here; the repositories behind it are created when the first call needs a name.
        TelecomCalls.labelResolver = LazyCallerLabelResolver { CallerLabelProvider(contacts.get(), businesses.get()) }
        appScope.launch { warmUp() }
    }

    /** If the last run crashed, put its report in front of the user. Starting an activity from a background start can be refused; that is fine. */
    @Suppress("TooGenericExceptionCaught") // Never let the crash screen itself take the app down.
    private fun showPreviousCrash() {
        try {
            if (CrashReporter.pending(this) != null) {
                startActivity(Intent(this, CrashReportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not show the crash report: ${e.javaClass.simpleName}")
        }
    }

    /** Runs on the application scope (Dispatchers.Default). Each step is independent, so one failing does not stop the rest. */
    private fun warmUp() {
        // The heaviest one-off cost of the process: libphonenumber's metadata, loaded here instead of in a call.
        appScope.launch { safely("phone numbers") { LibPhoneNormalizer.shared.warmUp(regions.get().defaultRegion()) } }
        // Packs are opened in the background so the first call finds them ready; stale data is refreshed in WorkManager.
        safely("packs") { lookups.get().warmUp() }
        appScope.launch { safely("engine") { engine.get().warmUp() } }
        safely("sync") { syncScheduler.get().start() }
        safely("recording") { recordingCoordinator.get().start() }
        appScope.launch { safely("data") { dataStartup.get().run() } }
    }

    @Suppress("TooGenericExceptionCaught") // A failed warm-up must never take the app down; the same work is done on demand later.
    private inline fun safely(step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up step '$step' failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "UltimatePhoneApp"
    }
}

/** A [CallerLabelResolver] that builds the real one the first time a call needs a name. */
private class LazyCallerLabelResolver(create: () -> CallerLabelResolver) : CallerLabelResolver {
    private val delegate by lazy(create)

    override suspend fun resolve(number: String): CallerLabel? = delegate.resolve(number)
}

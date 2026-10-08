package com.qtekfun.ultimatephone

import android.app.Application
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.telecom.TelecomCalls
import com.qtekfun.ultimatephone.data.BusinessFinder
import com.qtekfun.ultimatephone.data.CallerLabelProvider
import com.qtekfun.ultimatephone.data.DataStartup
import com.qtekfun.ultimatephone.data.DefaultInstalledPackLookups
import com.qtekfun.ultimatephone.di.ApplicationScope
import com.qtekfun.ultimatephone.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class UltimatePhoneApp : Application() {
    @Inject
    lateinit var contacts: ContactsRepository

    @Inject
    lateinit var businesses: BusinessFinder

    @Inject
    lateinit var lookups: DefaultInstalledPackLookups

    @Inject
    lateinit var dataStartup: DataStartup

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        syncScheduler.start()
        // Callers are named from the contacts first, then from the installed business packs. The spam verdict plugs in later.
        TelecomCalls.labelResolver = CallerLabelProvider(contacts, businesses)
        // Packs are opened in the background so the first call finds them ready; stale data is refreshed in WorkManager.
        lookups.warmUp()
        appScope.launch { dataStartup.run() }
    }
}

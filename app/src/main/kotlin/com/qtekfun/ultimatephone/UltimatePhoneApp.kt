package com.qtekfun.ultimatephone

import android.app.Application
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.telecom.CallerLabel
import com.qtekfun.ultimatephone.core.telecom.TelecomCalls
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UltimatePhoneApp : Application() {
    @Inject
    lateinit var contacts: ContactsRepository

    override fun onCreate() {
        super.onCreate()
        // Callers are named from the contacts. Businesses and the spam verdict plug in here in later phases.
        TelecomCalls.labelResolver = com.qtekfun.ultimatephone.core.telecom.CallerLabelResolver { number ->
            contacts.lookupByNumber(number)?.let { CallerLabel(it.displayName, it.photoThumbUri) }
        }
    }
}

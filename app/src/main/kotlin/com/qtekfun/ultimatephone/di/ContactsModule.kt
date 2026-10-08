package com.qtekfun.ultimatephone.di

import android.content.Context
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.contacts.SystemContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The ContactsContract-backed contacts implementation, exposed both as the shared read-only contract and as the full manager. */
@Module
@InstallIn(SingletonComponent::class)
object ContactsModule {
    @Provides
    @Singleton
    fun contactsManager(@ApplicationContext context: Context, normalizer: PhoneNormalizer, regionProvider: RegionProvider): ContactsManager =
        SystemContactsRepository(context, normalizer, regionProvider = { regionProvider.defaultRegion() })

    @Provides
    fun contactsRepository(manager: ContactsManager): ContactsRepository = manager
}

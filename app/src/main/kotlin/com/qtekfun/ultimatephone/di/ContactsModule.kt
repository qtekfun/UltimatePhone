package com.qtekfun.ultimatephone.di

import com.qtekfun.ultimatephone.core.contacts.ContactMatch
import com.qtekfun.ultimatephone.core.contacts.ContactSummary
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.contacts.DialerSuggestion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Placeholder binding; the contacts feature replaces it with the ContactsContract implementation. */
@Module
@InstallIn(SingletonComponent::class)
object ContactsModule {
    @Provides
    @Singleton
    fun contactsRepository(): ContactsRepository = object : ContactsRepository {
        override fun observeContacts(): Flow<List<ContactSummary>> = flowOf(emptyList())

        override suspend fun lookupByNumber(number: String): ContactMatch? = null

        override suspend fun suggest(query: String, limit: Int): List<DialerSuggestion> = emptyList()
    }
}

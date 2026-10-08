package com.qtekfun.ultimatephone.di

import android.content.Context
import com.qtekfun.ultimatephone.core.spam.lists.SpamDatabase
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.sync.KeystoreSecretCipher
import com.qtekfun.ultimatephone.core.sync.LocalLists
import com.qtekfun.ultimatephone.core.sync.OkHttpWebDav
import com.qtekfun.ultimatephone.core.sync.SecretVault
import com.qtekfun.ultimatephone.sync.DataStoreSyncSettings
import com.qtekfun.ultimatephone.sync.DefaultSyncRepository
import com.qtekfun.ultimatephone.sync.RoomLocalLists
import com.qtekfun.ultimatephone.sync.SyncRepository
import com.qtekfun.ultimatephone.sync.SyncScheduler
import com.qtekfun.ultimatephone.sync.SyncSettings
import com.qtekfun.ultimatephone.sync.WebDavFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** Nextcloud sync of the spam list and the allowed list. */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun syncSettings(@ApplicationContext context: Context): SyncSettings = DataStoreSyncSettings(context)

    @Provides
    @Singleton
    fun secretVault(): SecretVault = SecretVault(KeystoreSecretCipher())

    @Provides
    @Singleton
    fun localLists(database: SpamDatabase): LocalLists = RoomLocalLists(database.listEntryDao())

    @Provides
    @Singleton
    fun syncRepository(settings: SyncSettings, vault: SecretVault, lists: LocalLists): SyncRepository =
        DefaultSyncRepository(settings, vault, lists, WebDavFactory { root, credentials -> OkHttpWebDav(root, credentials) })

    @Provides
    @Singleton
    fun syncScheduler(
        @ApplicationContext context: Context,
        repository: SyncRepository,
        lists: SpamListsRepository,
        @ApplicationScope scope: CoroutineScope
    ): SyncScheduler = SyncScheduler(context, repository, lists, scope)
}

package com.qtekfun.ultimatephone.di

import android.content.Context
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.lists.RoomSpamListsRepository
import com.qtekfun.ultimatephone.core.spam.lists.SpamDatabase
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.spam.prefix.BuiltInRules
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRuleSet
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.RoleController
import com.qtekfun.ultimatephone.data.InstalledPackLookups
import com.qtekfun.ultimatephone.feature.spam.ListsSpamNumberActions
import com.qtekfun.ultimatephone.feature.spam.RoleControllerScreeningRole
import com.qtekfun.ultimatephone.feature.spam.ScreeningRole
import com.qtekfun.ultimatephone.feature.spam.SpamHistory
import com.qtekfun.ultimatephone.feature.spam.SpamNumberActions
import com.qtekfun.ultimatephone.feature.spam.StoreSpamHistory
import com.qtekfun.ultimatephone.screening.DataStoreSpamSettingsRepository
import com.qtekfun.ultimatephone.screening.DecisionEngine
import com.qtekfun.ultimatephone.screening.DecisionStore
import com.qtekfun.ultimatephone.screening.RoomDecisionStore
import com.qtekfun.ultimatephone.screening.ScreeningStats
import com.qtekfun.ultimatephone.screening.SpamSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/** Spam filtering: the database, the user's lists and settings, and the decision engine shared by every caller. */
@Module
@InstallIn(SingletonComponent::class)
object SpamModule {
    @Provides
    @Singleton
    fun spamDatabase(@ApplicationContext context: Context): SpamDatabase = SpamDatabase.create(context)

    @Provides
    @Singleton
    fun spamSettings(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): SpamSettingsRepository =
        DataStoreSpamSettingsRepository(context, scope)

    @Provides
    @Singleton
    fun spamLists(database: SpamDatabase, settings: SpamSettingsRepository): SpamListsRepository {
        // One tiny DataStore read, the first time the lists are needed; after that the id is fixed for this repository.
        val deviceId = runBlocking { settings.deviceId() }
        return RoomSpamListsRepository(database.listEntryDao(), deviceId)
    }

    @Provides
    @Singleton
    fun decisionStore(database: SpamDatabase): DecisionStore = RoomDecisionStore(database.callDecisionDao())

    @Provides
    @Singleton
    fun screeningStats(): ScreeningStats = ScreeningStats()

    @Suppress("LongParameterList") // Wiring of the engine's collaborators.
    @Provides
    @Singleton
    fun decisionEngine(
        contacts: ContactsRepository,
        lists: SpamListsRepository,
        packs: InstalledPackLookups,
        settings: SpamSettingsRepository,
        normalizer: PhoneNormalizer,
        regions: RegionProvider,
        store: DecisionStore,
        @ApplicationScope scope: CoroutineScope
    ): DecisionEngine = DecisionEngine(
        contacts = contacts,
        lists = lists,
        packs = packs,
        builtInRules = PrefixRuleSet(BuiltInRules.load().rules),
        settings = settings,
        normalizer = normalizer,
        regions = regions,
        store = store,
        scope = scope
    )

    @Provides
    @Singleton
    fun spamHistory(store: DecisionStore): SpamHistory = StoreSpamHistory(store)

    @Provides
    @Singleton
    fun spamNumberActions(
        lists: SpamListsRepository,
        engine: DecisionEngine,
        history: SpamHistory,
        normalizer: PhoneNormalizer,
        regions: RegionProvider
    ): SpamNumberActions = ListsSpamNumberActions(lists, engine, history, normalizer, regions)

    @Provides
    @Singleton
    fun screeningRole(roles: RoleController): ScreeningRole = RoleControllerScreeningRole(roles)
}

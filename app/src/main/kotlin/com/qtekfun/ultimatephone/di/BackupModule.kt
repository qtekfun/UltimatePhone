package com.qtekfun.ultimatephone.di

import com.qtekfun.ultimatephone.backup.AppSettingsSection
import com.qtekfun.ultimatephone.backup.BackupSection
import com.qtekfun.ultimatephone.backup.DataSettingsSection
import com.qtekfun.ultimatephone.backup.ListSection
import com.qtekfun.ultimatephone.backup.NextcloudSection
import com.qtekfun.ultimatephone.backup.SpamSettingsSection
import com.qtekfun.ultimatephone.core.settings.BackupService
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.data.DataManager
import com.qtekfun.ultimatephone.data.DataSettingsRepository
import com.qtekfun.ultimatephone.screening.SpamSettingsRepository
import com.qtekfun.ultimatephone.settings.SettingsRepository
import com.qtekfun.ultimatephone.sync.SyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Settings export and import. Each feature that owns settings contributes a [BackupSection] with `@Provides @IntoSet`
 * (here or in its own module); the screens and the file format pick it up with no other change.
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @IntoSet
    fun appSettingsSection(settings: SettingsRepository): BackupSection = AppSettingsSection(settings)

    @Provides
    @IntoSet
    fun spamSettingsSection(settings: SpamSettingsRepository): BackupSection = SpamSettingsSection(settings)

    @Provides
    @IntoSet
    fun spamListSection(lists: SpamListsRepository): BackupSection = ListSection(ListType.OWN, lists)

    @Provides
    @IntoSet
    fun whitelistSection(lists: SpamListsRepository): BackupSection = ListSection(ListType.WHITELIST, lists)

    @Provides
    @IntoSet
    fun nextcloudSection(sync: SyncRepository): BackupSection = NextcloudSection(sync)

    @Provides
    @IntoSet
    fun dataSettingsSection(settings: DataSettingsRepository, manager: DataManager, @ApplicationScope scope: CoroutineScope): BackupSection =
        DataSettingsSection(settings, refresher = { ids -> scope.launch { ids.forEach { manager.refreshSource(it) } } })

    @Provides
    @Singleton
    fun backupService(sections: @JvmSuppressWildcards Set<BackupSection>): BackupService = BackupService(sections.sortedBy { it.id })
}

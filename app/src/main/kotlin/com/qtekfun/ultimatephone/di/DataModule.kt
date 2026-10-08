package com.qtekfun.ultimatephone.di

import android.content.Context
import com.qtekfun.ultimatephone.core.datapacks.DataPackRepository
import com.qtekfun.ultimatephone.core.datapacks.HttpFetcher
import com.qtekfun.ultimatephone.core.datapacks.HttpUrlFetcher
import com.qtekfun.ultimatephone.core.datapacks.ManifestVerifier
import com.qtekfun.ultimatephone.core.datapacks.PackStore
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.data.BusinessFinder
import com.qtekfun.ultimatephone.data.BusinessNameSearch
import com.qtekfun.ultimatephone.data.BusinessResolver
import com.qtekfun.ultimatephone.data.ConnectivityNetworkChecker
import com.qtekfun.ultimatephone.data.CustomSourceUpdater
import com.qtekfun.ultimatephone.data.DataManager
import com.qtekfun.ultimatephone.data.DataSettingsRepository
import com.qtekfun.ultimatephone.data.DataStartup
import com.qtekfun.ultimatephone.data.DataStoreDataSettingsRepository
import com.qtekfun.ultimatephone.data.DefaultInstalledPackLookups
import com.qtekfun.ultimatephone.data.HttpsSourceFetcher
import com.qtekfun.ultimatephone.data.InstalledPackLookups
import com.qtekfun.ultimatephone.data.NetworkChecker
import com.qtekfun.ultimatephone.data.PackBackend
import com.qtekfun.ultimatephone.data.PackLookupsReloader
import com.qtekfun.ultimatephone.data.RepositoryPackBackend
import com.qtekfun.ultimatephone.data.SourceFetcher
import com.qtekfun.ultimatephone.data.UpdateScheduler
import com.qtekfun.ultimatephone.data.WorkManagerUpdateScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * Data packs, custom sources and the lookups the call screening reads. Files live under `filesDir/packs`; the verified
 * manifest is cached in `cacheDir/data-manifest`. The same [DefaultInstalledPackLookups] instance serves as the
 * [InstalledPackLookups], the reloader after a change and the business name search for the dialer.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private const val PACKS_DIR = "packs"
    private const val MANIFEST_DIR = "data-manifest"

    @Provides
    @Singleton
    fun dataSettings(@ApplicationContext context: Context): DataSettingsRepository = DataStoreDataSettingsRepository(context)

    @Provides
    @Singleton
    fun packStore(@ApplicationContext context: Context): PackStore = PackStore(File(context.filesDir, PACKS_DIR))

    @Provides
    @Singleton
    fun httpFetcher(): HttpFetcher = HttpUrlFetcher()

    @Provides
    @Singleton
    fun packBackend(@ApplicationContext context: Context, store: PackStore, fetcher: HttpFetcher): PackBackend {
        val repository = DataPackRepository(fetcher, ManifestVerifier(), store, File(context.cacheDir, MANIFEST_DIR))
        return RepositoryPackBackend(repository, store, File(context.filesDir, PACKS_DIR))
    }

    @Provides
    @Singleton
    fun sourceFetcher(): SourceFetcher = HttpsSourceFetcher()

    @Provides
    @Singleton
    fun networkChecker(@ApplicationContext context: Context): NetworkChecker = ConnectivityNetworkChecker(context)

    @Provides
    @Singleton
    fun customSourceUpdater(
        @ApplicationContext context: Context,
        fetcher: SourceFetcher,
        normalizer: PhoneNormalizer,
        regions: RegionProvider
    ): CustomSourceUpdater = CustomSourceUpdater(fetcher, normalizer, { regions.defaultRegion() }, File(context.filesDir, PACKS_DIR))

    @Provides
    @Singleton
    fun defaultLookups(
        @ApplicationContext context: Context,
        store: PackStore,
        settings: DataSettingsRepository,
        @ApplicationScope scope: CoroutineScope
    ): DefaultInstalledPackLookups = DefaultInstalledPackLookups(
        store = store,
        packsDir = File(context.filesDir, PACKS_DIR),
        customSources = { settings.current().customSources },
        scope = scope
    )

    @Provides
    @Singleton
    fun installedPackLookups(lookups: DefaultInstalledPackLookups): InstalledPackLookups = lookups

    @Provides
    @Singleton
    fun packLookupsReloader(lookups: DefaultInstalledPackLookups): PackLookupsReloader = lookups

    @Provides
    @Singleton
    fun businessNameSearch(lookups: DefaultInstalledPackLookups): BusinessNameSearch = lookups

    @Provides
    @Singleton
    fun businessFinder(lookups: InstalledPackLookups, normalizer: PhoneNormalizer, regions: RegionProvider): BusinessFinder =
        BusinessResolver({ lookups.businesses }, normalizer, regions)

    @Provides
    @Singleton
    fun updateScheduler(@ApplicationContext context: Context): UpdateScheduler = WorkManagerUpdateScheduler(context)

    @Provides
    @Singleton
    fun dataManager(
        backend: PackBackend,
        settings: DataSettingsRepository,
        reloader: PackLookupsReloader,
        sources: CustomSourceUpdater,
        network: NetworkChecker,
        @ApplicationScope scope: CoroutineScope
    ): DataManager = DataManager(backend, settings, reloader, sources, network, scope)

    @Provides
    @Singleton
    fun dataStartup(settings: DataSettingsRepository, manager: DataManager, scheduler: UpdateScheduler): DataStartup = DataStartup(settings, manager, scheduler)
}

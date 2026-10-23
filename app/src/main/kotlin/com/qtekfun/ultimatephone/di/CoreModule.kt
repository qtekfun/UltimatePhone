package com.qtekfun.ultimatephone.di

import android.content.Context
import com.qtekfun.ultimatephone.core.phonenumber.LibPhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.CallController
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import com.qtekfun.ultimatephone.core.telecom.EmergencyNumbers
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.RoleController
import com.qtekfun.ultimatephone.core.telecom.SimRegionProvider
import com.qtekfun.ultimatephone.core.telecom.SimRepository
import com.qtekfun.ultimatephone.core.telecom.TelecomCallPlacer
import com.qtekfun.ultimatephone.core.telecom.TelecomCalls
import com.qtekfun.ultimatephone.core.telecom.TelecomSimRepository
import com.qtekfun.ultimatephone.core.telecom.TelephonyEmergencyNumbers
import com.qtekfun.ultimatephone.settings.CallSimPreferences
import com.qtekfun.ultimatephone.settings.DataStoreSettingsRepository
import com.qtekfun.ultimatephone.settings.PreferredSimCallPlacer
import com.qtekfun.ultimatephone.settings.SettingsCallSimPreferences
import com.qtekfun.ultimatephone.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Scope that lives as long as the process, for work that must not die with a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Wires the interfaces of the core modules to their Android implementations. Feature modules add their own bindings. */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun phoneNormalizer(): PhoneNormalizer = LibPhoneNormalizer.shared

    @Provides
    @Singleton
    fun simRepository(@ApplicationContext context: Context): SimRepository = TelecomSimRepository(context)

    @Provides
    @Singleton
    fun emergencyNumbers(@ApplicationContext context: Context): EmergencyNumbers = TelephonyEmergencyNumbers(context)

    /** Every call the app starts goes through the SIM preferences (remembered, last used, default). */
    @Provides
    @Singleton
    fun callPlacer(
        @ApplicationContext context: Context,
        sims: SimRepository,
        settings: SettingsRepository,
        normalizer: PhoneNormalizer,
        regions: RegionProvider,
        emergency: EmergencyNumbers
    ): CallPlacer = PreferredSimCallPlacer(TelecomCallPlacer(context), sims, settings, normalizer, regions, emergency)

    @Provides
    @Singleton
    fun callController(): CallController = TelecomCalls

    @Provides
    @Singleton
    fun roleController(@ApplicationContext context: Context): RoleController = RoleController(context)

    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): SettingsRepository =
        DataStoreSettingsRepository(context, scope)

    @Provides
    @Singleton
    fun callSimPreferences(settings: SettingsRepository, normalizer: PhoneNormalizer, regions: RegionProvider): CallSimPreferences =
        SettingsCallSimPreferences(settings, normalizer, regions)

    /** The SIM's country, unless the user chose a region in Settings. */
    @Provides
    @Singleton
    fun regionProvider(@ApplicationContext context: Context, settings: SettingsRepository): RegionProvider {
        val base = SimRegionProvider(context)
        return RegionProvider { settings.settings.value.regionOverride ?: base.defaultRegion() }
    }
}

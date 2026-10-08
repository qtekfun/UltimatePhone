package com.qtekfun.ultimatephone.di

import android.content.Context
import com.qtekfun.ultimatephone.core.phonenumber.LibPhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.SimRegionProvider
import com.qtekfun.ultimatephone.core.telecom.SimRepository
import com.qtekfun.ultimatephone.core.telecom.TelecomCallPlacer
import com.qtekfun.ultimatephone.core.telecom.TelecomSimRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires the interfaces of the core modules to their Android implementations. Feature modules add their own bindings. */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun phoneNormalizer(): PhoneNormalizer = LibPhoneNormalizer()

    @Provides
    @Singleton
    fun simRepository(@ApplicationContext context: Context): SimRepository = TelecomSimRepository(context)

    @Provides
    @Singleton
    fun callPlacer(@ApplicationContext context: Context): CallPlacer = TelecomCallPlacer(context)

    @Provides
    @Singleton
    fun regionProvider(@ApplicationContext context: Context): RegionProvider = SimRegionProvider(context)
}

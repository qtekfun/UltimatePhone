package com.qtekfun.ultimatephone.di

import com.qtekfun.ultimatephone.data.EmptyInstalledPackLookups
import com.qtekfun.ultimatephone.data.InstalledPackLookups
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Placeholder binding; the data feature replaces it with the real implementation. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun installedPackLookups(): InstalledPackLookups = EmptyInstalledPackLookups
}

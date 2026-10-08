package com.qtekfun.ultimatephone.di

import android.content.Context
import com.qtekfun.ultimatephone.core.calllog.CallLogRepository
import com.qtekfun.ultimatephone.core.calllog.ContentResolverCallLogRepository
import com.qtekfun.ultimatephone.core.calllog.NumberKeys
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.SimRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the call history to the system call log. */
@Module
@InstallIn(SingletonComponent::class)
object CallLogModule {
    @Provides
    @Singleton
    fun callLogRepository(@ApplicationContext context: Context, simRepository: SimRepository): CallLogRepository =
        ContentResolverCallLogRepository(context, simRepository)

    @Provides
    @Singleton
    fun numberKeys(normalizer: PhoneNormalizer, regionProvider: RegionProvider): NumberKeys = NumberKeys(normalizer, regionProvider)
}

package com.qtekfun.ultimatephone.di

import android.content.Context
import com.qtekfun.ultimatephone.core.recording.RecordingStorage
import com.qtekfun.ultimatephone.recording.CallRecording
import com.qtekfun.ultimatephone.recording.DataStoreRecordingSettings
import com.qtekfun.ultimatephone.recording.RecordingCoordinator
import com.qtekfun.ultimatephone.recording.RecordingSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Call recording: its settings and the recordings folder. The engine and the coordinator are injected by constructor. */
@Module
@InstallIn(SingletonComponent::class)
object RecordingModule {
    @Provides
    @Singleton
    fun recordingSettings(@ApplicationContext context: Context): RecordingSettings = DataStoreRecordingSettings(context)

    @Provides
    @Singleton
    fun callRecording(coordinator: RecordingCoordinator): CallRecording = coordinator

    @Provides
    @Singleton
    fun recordingStorage(@ApplicationContext context: Context): RecordingStorage = RecordingStorage(context)
}

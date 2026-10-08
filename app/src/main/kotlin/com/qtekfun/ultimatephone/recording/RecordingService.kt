package com.qtekfun.ultimatephone.recording

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.qtekfun.ultimatephone.core.recording.SessionState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive (and allowed to use the microphone) while a call is recorded with the screen off. It does no
 * recording itself: [RecordingEngine] does, and this service only follows the engine's state and closes when it ends.
 */
@AndroidEntryPoint
class RecordingService : Service() {
    @Inject
    lateinit var engine: RecordingEngine

    @Inject
    lateinit var notifications: RecordingNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> engine.stop()
            else -> start()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        // Must be the first thing: the system gives a service started with startForegroundService a few seconds.
        if (!promote()) {
            engine.onForegroundDenied()
            stopSelf()
            return
        }
        if (!engine.claimForService()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        notifications.cancelPrompt()
        engine.begin()
        if (watcher == null) watcher = scope.launch { follow() }
    }

    /** Becomes a foreground service of type microphone. False when the system does not allow it right now. */
    private fun promote(): Boolean = runCatching {
        // ForegroundServiceStartNotAllowedException when started from the background without an exemption; SecurityException
        // when the microphone type is refused (it needs RECORD_AUDIO and an allowed state).
        startForeground(RecordingNotifications.ID_ONGOING, notifications.ongoing(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }.isSuccess

    private suspend fun follow() {
        engine.state.collect { state ->
            when (state) {
                is SessionState.Recording -> notifications.notifyOngoing(state.startedAtMillis)
                is SessionState.Starting, is SessionState.Stopping -> Unit
                SessionState.Idle, is SessionState.Failed -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        // If the system ends the service, the file must still be closed properly.
        engine.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.qtekfun.ultimatephone.recording.START"
        const val ACTION_STOP = "com.qtekfun.ultimatephone.recording.STOP"

        /** Throws the framework's exception when the system does not allow the start; [RecordingEngine] handles it. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java).setAction(ACTION_START))
        }
    }
}

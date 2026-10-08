package com.qtekfun.ultimatephone.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.telecom.InCallIntents
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two notifications of call recording: the ongoing one (the foreground service's, with a clock and a Stop action) and
 * the prompt that offers "Record" when the system did not let an automatic recording start. Neither shows a number or a name.
 */
@Singleton
class RecordingNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    /** The notification of the foreground service. [startedAtMillis] null while it is still starting. */
    fun ongoing(startedAtMillis: Long?): Notification {
        ensureChannel()
        val stop = Notification.Action.Builder(
            null,
            context.getString(R.string.recording_stop),
            servicePending(RecordingService.ACTION_STOP, REQUEST_STOP, false)
        )
            .build()
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.recording_notification_title))
            .setContentText(context.getString(R.string.recording_notification_text))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(openCall())
            .addAction(stop)
        if (startedAtMillis != null) builder.setWhen(startedAtMillis).setShowWhen(true).setUsesChronometer(true)
        return builder.build()
    }

    fun notifyOngoing(startedAtMillis: Long) {
        manager.notify(ID_ONGOING, ongoing(startedAtMillis))
    }

    /** Shown when an automatic recording could not start from the background: one tap on Record starts it. */
    fun showPrompt() {
        ensureChannel()
        val record = Notification.Action.Builder(
            null,
            context.getString(R.string.recording_record),
            servicePending(RecordingService.ACTION_START, REQUEST_START, true)
        )
            .build()
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.recording_prompt_title))
            .setContentText(context.getString(R.string.recording_prompt_text))
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openCall())
            .addAction(record)
            .build()
        manager.notify(ID_PROMPT, notification)
    }

    fun cancelPrompt() = manager.cancel(ID_PROMPT)

    private fun openCall(): PendingIntent {
        val intent = Intent(InCallIntents.ACTION_IN_CALL)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, REQUEST_OPEN, intent, FLAGS)
    }

    /** A tap on a notification action is one of the moments the system lets an app start a foreground service. */
    private fun servicePending(action: String, request: Int, foreground: Boolean): PendingIntent {
        val intent = Intent(context, RecordingService::class.java).setAction(action)
        return if (foreground) {
            PendingIntent.getForegroundService(context, request, intent, FLAGS)
        } else {
            PendingIntent.getService(context, request, intent, FLAGS)
        }
    }

    companion object {
        const val ID_ONGOING = 2001
        private const val ID_PROMPT = 2002
        private const val CHANNEL = "call_recording"
        private const val REQUEST_OPEN = 20
        private const val REQUEST_STOP = 21
        private const val REQUEST_START = 22
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

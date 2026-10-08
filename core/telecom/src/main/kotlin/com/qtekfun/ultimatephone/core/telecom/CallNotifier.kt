package com.qtekfun.ultimatephone.core.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent

/**
 * Notifications for calls. The incoming one is a full-screen intent, so a locked phone shows the call screen; while the
 * phone is in use the system turns it into a heads-up notification. The ringtone is played by the system, not by us.
 */
internal class CallNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.telecom_channel_incoming), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.telecom_channel_ongoing), NotificationManager.IMPORTANCE_LOW)
        )
    }

    /** Idempotent: call it with the current calls every time they change. */
    fun update(calls: List<CallInfo>) {
        val ringing = calls.firstOrNull { it.status == CallStatus.RINGING }
        val ongoing = calls.firstOrNull { it.status != CallStatus.RINGING && it.status != CallStatus.DISCONNECTED }
        if (ringing != null) showIncoming(ringing) else manager.cancel(ID_INCOMING)
        if (ongoing != null) showOngoing(ongoing) else manager.cancel(ID_ONGOING)
    }

    fun cancelAll() {
        manager.cancel(ID_INCOMING)
        manager.cancel(ID_ONGOING)
    }

    private fun showIncoming(call: CallInfo) {
        val open = openCall(answer = false)
        val person = Person.Builder().setName(call.title).setImportant(true).build()
        val notification = Notification.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(call.title)
            .setContentText(context.getString(R.string.telecom_incoming_call))
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setStyle(Notification.CallStyle.forIncomingCall(person, broadcast(CallActionReceiver.ACTION_DECLINE), openCall(answer = true)))
            .build()
        manager.notify(ID_INCOMING, notification)
    }

    private fun showOngoing(call: CallInfo) {
        val hangup = Notification.Action.Builder(null, context.getString(R.string.telecom_hang_up), broadcast(CallActionReceiver.ACTION_HANGUP))
            .build()
        val notification = Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(call.title)
            .setContentText(context.getString(R.string.telecom_ongoing_call))
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openCall(answer = false))
            .addAction(hangup)
            .build()
        manager.notify(ID_ONGOING, notification)
    }

    private fun openCall(answer: Boolean): PendingIntent {
        val intent = Intent(InCallIntents.ACTION_IN_CALL)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(InCallIntents.EXTRA_ANSWER, answer)
        return PendingIntent.getActivity(context, if (answer) REQUEST_ANSWER else REQUEST_OPEN, intent, FLAGS)
    }

    private fun broadcast(action: String): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, FLAGS)
    }

    /** Starts the in-call screen directly, for calls we place ourselves. */
    fun openCallScreen() {
        context.startActivity(
            Intent(InCallIntents.ACTION_IN_CALL).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private companion object {
        const val CHANNEL_INCOMING = "incoming_call"
        const val CHANNEL_ONGOING = "ongoing_call"
        const val ID_INCOMING = 1001
        const val ID_ONGOING = 1002
        const val REQUEST_OPEN = 1
        const val REQUEST_ANSWER = 2
        const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

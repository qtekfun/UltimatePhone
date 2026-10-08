package com.qtekfun.ultimatephone.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.qtekfun.ultimatephone.R

/**
 * Tests 1, 2, 3, 9: receives every call once the app holds the dialer role. An incoming call is announced with a
 * full-screen notification (lock screen) that degrades to a heads-up notification while the phone is in use.
 */
class SpikeInCallService : InCallService() {
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            EventLog.add("incall", "state=${CallNames.state(state)} ${describe(call)}")
            if (state != Call.STATE_RINGING) cancelNotification(NOTIFICATION_INCOMING)
            if (state == Call.STATE_ACTIVE) showOngoing(call)
            CallStore.changed()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        EventLog.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                // The system plays the ringtone itself; this notification must stay silent.
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallStore.add(call)
        call.registerCallback(callback)
        val sinceCreated = System.currentTimeMillis() - call.details.creationTimeMillis
        EventLog.add(
            "incall",
            "added ${CallNames.direction(call.details.callDirection)} state=${CallNames.state(call.details.state)} " +
                "${describe(call)} delivered_after=${sinceCreated}ms"
        )
        if (call.details.state == Call.STATE_RINGING) {
            showIncoming(call)
        } else {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallStore.remove(call)
        cancelNotification(NOTIFICATION_INCOMING)
        cancelNotification(NOTIFICATION_ONGOING)
        EventLog.add("incall", "removed ${describe(call)} cause=${call.details.disconnectCause}")
    }

    @Deprecated("Replaced by onCallEndpointChanged on newer releases, still delivered on Android 12+")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallStore.audioState = audioState
        EventLog.add("incall", "audio route=${audioState.route} muted=${audioState.isMuted}")
        CallStore.changed()
    }

    private fun describe(call: Call): String {
        val number = call.details.handle?.schemeSpecificPart
        return "${PhoneMask.mask(number)} ${SimInfo.describe(this, call.details.accountHandle)}"
    }

    private fun showIncoming(call: Call) {
        val label = getString(R.string.incoming_call, PhoneMask.mask(call.details.handle?.schemeSpecificPart))
        val open = activityIntent(null)
        val answer = activityIntent(InCallActivity.ACTION_ANSWER)
        val decline = broadcastIntent(CallActionReceiver.ACTION_DECLINE)
        val person = Person.Builder().setName(label).setImportant(true).build()
        val notification = Notification.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(label)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setStyle(Notification.CallStyle.forIncomingCall(person, decline, answer))
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_INCOMING, notification)
    }

    private fun showOngoing(call: Call) {
        val label = getString(R.string.ongoing_call, PhoneMask.mask(call.details.handle?.schemeSpecificPart))
        val hangup = Notification.Action.Builder(null, getString(R.string.btn_hangup), broadcastIntent(CallActionReceiver.ACTION_HANGUP)).build()
        val notification = Notification.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(label)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(activityIntent(null))
            .addAction(hangup)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ONGOING, notification)
    }

    private fun cancelNotification(id: Int) {
        getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun activityIntent(action: String?): PendingIntent {
        val intent = Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (action != null) intent.putExtra(InCallActivity.EXTRA_ACTION, action)
        return PendingIntent.getActivity(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun broadcastIntent(action: String): PendingIntent {
        val intent = Intent(this, CallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val CHANNEL_INCOMING = "incoming_call"
        private const val CHANNEL_ONGOING = "ongoing_call"
        private const val NOTIFICATION_INCOMING = 1001
        private const val NOTIFICATION_ONGOING = 1002

        @Volatile
        var instance: SpikeInCallService? = null
            private set
    }
}

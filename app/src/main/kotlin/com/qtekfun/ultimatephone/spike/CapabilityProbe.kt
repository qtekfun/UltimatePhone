package com.qtekfun.ultimatephone.spike

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.role.RoleManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.telecom.TelecomManager
import com.qtekfun.ultimatephone.BuildConfig

/**
 * Test 11. Asks the device what it can do instead of assuming it from the brand. The result is a list of
 * "name = value" lines, dumped to the event log, that must match what the manual tests show.
 */
object CapabilityProbe {
    /** System feature flag set on devices with Google Mobile Services; detection only, never a requirement. */
    private const val GOOGLE_EXPERIENCE_FEATURE = "com.google.android.feature.GOOGLE_EXPERIENCE"

    @SuppressLint("MissingPermission")
    fun run(context: Context): List<String> {
        val roles = context.getSystemService(RoleManager::class.java)
        val telecom = context.getSystemService(TelecomManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val pm = context.packageManager
        val hasPhone = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val lines = mutableListOf<String>()
        lines += "device = ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
        lines += "android = ${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} patch=${Build.VERSION.SECURITY_PATCH}"
        lines += "build = ${Build.DISPLAY}"
        lines += "app = ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME}"
        lines += "google_mobile_services_feature = ${pm.hasSystemFeature(GOOGLE_EXPERIENCE_FEATURE)}"
        lines += "feature.telephony = ${pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)}"
        lines += "feature.telephony.calling = ${pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING)}"
        lines += "feature.telephony.subscription = ${pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)}"
        lines += "role.dialer available=${roles.isRoleAvailable(RoleManager.ROLE_DIALER)} held=${roles.isRoleHeld(RoleManager.ROLE_DIALER)}"
        lines += "role.call_screening available=${roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)} " +
            "held=${roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)}"
        lines += "default_dialer = ${telecom.defaultDialerPackage}"
        lines += "system_dialer = ${telecom.systemDialerPackage}"
        lines += "notifications_enabled = ${notifications.areNotificationsEnabled()}"
        lines += "full_screen_intent_allowed = ${fullScreenIntentAllowed(notifications)}"
        lines += "ignoring_battery_optimizations = ${power.isIgnoringBatteryOptimizations(context.packageName)}"
        lines += "standby_bucket = ${standbyBucket(context)}"
        lines += if (hasPhone) {
            val sims = SimInfo.accounts(context)
            "sim_accounts = ${sims.size} " + sims.joinToString { "${it.label}@slot${it.slot}" }
        } else {
            "sim_accounts = unknown (READ_PHONE_STATE not granted)"
        }
        lines += "audio_sources_to_try = ${AudioSources.all.joinToString { it.first }} (use the recording buttons)"
        return lines
    }

    private fun fullScreenIntentAllowed(notifications: NotificationManager): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        notifications.canUseFullScreenIntent().toString()
    } else {
        "granted at install (before Android 14)"
    }

    private fun standbyBucket(context: Context): String {
        val bucket = context.getSystemService(UsageStatsManager::class.java).appStandbyBucket
        val name = when (bucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
            else -> "OTHER"
        }
        return "$name($bucket)"
    }

    fun standbyBucketName(context: Context): String = standbyBucket(context)
}

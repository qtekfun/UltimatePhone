package com.qtekfun.ultimatephone.core.telecom

import android.app.NotificationManager
import android.app.role.RoleManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.telecom.TelecomManager

/** What this device lets the app do, detected at run time instead of guessed from the brand. */
data class DeviceCapabilities(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val googleMobileServices: Boolean,
    val hasTelephony: Boolean,
    val dialerRoleAvailable: Boolean,
    val dialerRoleHeld: Boolean,
    val screeningRoleAvailable: Boolean,
    val screeningRoleHeld: Boolean,
    val defaultDialerPackage: String?,
    val systemDialerPackage: String?,
    val notificationsEnabled: Boolean,
    val fullScreenIntentAllowed: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val standbyBucket: Int,
    val simCount: Int
) {
    /** Plain `name = value` lines, for the diagnostics screen. Contains no personal data. */
    fun lines(): List<Pair<String, String>> = listOf(
        "Device" to "$manufacturer $model",
        "Android" to "$androidVersion (SDK $sdk)",
        "Google Mobile Services" to googleMobileServices.toString(),
        "Telephony" to hasTelephony.toString(),
        "Phone role" to "available=$dialerRoleAvailable held=$dialerRoleHeld",
        "Call screening role" to "available=$screeningRoleAvailable held=$screeningRoleHeld",
        "Default phone app" to defaultDialerPackage.orEmpty(),
        "System phone app" to systemDialerPackage.orEmpty(),
        "Notifications" to notificationsEnabled.toString(),
        "Full-screen call screen" to fullScreenIntentAllowed.toString(),
        "Unrestricted battery" to ignoringBatteryOptimizations.toString(),
        "Standby bucket" to standbyBucket.toString(),
        "SIM accounts" to simCount.toString()
    )
}

object CapabilityProbe {
    /** System feature flag set on devices with Google Mobile Services; detection only, never a requirement. */
    private const val GOOGLE_EXPERIENCE_FEATURE = "com.google.android.feature.GOOGLE_EXPERIENCE"

    fun run(context: Context, sims: SimRepository = TelecomSimRepository(context)): DeviceCapabilities {
        val roles = context.getSystemService(RoleManager::class.java)
        val telecom = context.getSystemService(TelecomManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val pm = context.packageManager
        return DeviceCapabilities(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            googleMobileServices = pm.hasSystemFeature(GOOGLE_EXPERIENCE_FEATURE),
            hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
            dialerRoleAvailable = roles.isRoleAvailable(RoleManager.ROLE_DIALER),
            dialerRoleHeld = roles.isRoleHeld(RoleManager.ROLE_DIALER),
            screeningRoleAvailable = roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING),
            screeningRoleHeld = roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
            defaultDialerPackage = telecom.defaultDialerPackage,
            systemDialerPackage = telecom.systemDialerPackage,
            notificationsEnabled = notifications.areNotificationsEnabled(),
            fullScreenIntentAllowed = fullScreenIntentAllowed(notifications),
            ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
            standbyBucket = context.getSystemService(UsageStatsManager::class.java).appStandbyBucket,
            simCount = sims.accounts().size
        )
    }

    private fun fullScreenIntentAllowed(notifications: NotificationManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notifications.canUseFullScreenIntent()
}

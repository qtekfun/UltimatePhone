package com.qtekfun.ultimatephone.core.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** The Android roles the app asks for. The phone role and the call screening role. */
class RoleController(private val context: Context) {
    private val roles: RoleManager get() = context.getSystemService(RoleManager::class.java)

    fun isDefaultDialer(): Boolean = roles.isRoleAvailable(RoleManager.ROLE_DIALER) && roles.isRoleHeld(RoleManager.ROLE_DIALER)

    fun isDialerRoleAvailable(): Boolean = roles.isRoleAvailable(RoleManager.ROLE_DIALER)

    fun isScreeningRoleAvailable(): Boolean = roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)

    fun isScreeningRoleHeld(): Boolean = isScreeningRoleAvailable() && roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    /** Intent for the system dialog that asks the user to let us screen calls; null if the device has no such role. */
    fun createScreeningRoleIntent(): Intent? = if (isScreeningRoleAvailable()) roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING) else null

    /** Intent for the system dialog that asks the user to make us the phone app; null if the device has no such role. */
    fun createDialerRoleIntent(): Intent? = if (isDialerRoleAvailable()) roles.createRequestRoleIntent(RoleManager.ROLE_DIALER) else null
}

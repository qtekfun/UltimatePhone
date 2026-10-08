package com.qtekfun.ultimatephone.core.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** The Android roles the app asks for. Only the phone role is needed in Phase 1; call screening comes with the spam engine. */
class RoleController(private val context: Context) {
    private val roles: RoleManager get() = context.getSystemService(RoleManager::class.java)

    fun isDefaultDialer(): Boolean = roles.isRoleAvailable(RoleManager.ROLE_DIALER) && roles.isRoleHeld(RoleManager.ROLE_DIALER)

    fun isDialerRoleAvailable(): Boolean = roles.isRoleAvailable(RoleManager.ROLE_DIALER)

    /** Intent for the system dialog that asks the user to make us the phone app; null if the device has no such role. */
    fun createDialerRoleIntent(): Intent? = if (isDialerRoleAvailable()) roles.createRequestRoleIntent(RoleManager.ROLE_DIALER) else null
}

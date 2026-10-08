package com.qtekfun.ultimatephone.feature.spam

import android.content.Intent
import com.qtekfun.ultimatephone.core.telecom.RoleController

/** The call screening role, as the spam settings need to see it. */
interface ScreeningRole {
    val isAvailable: Boolean
    val isHeld: Boolean

    /** Intent of the system dialog that asks for the role; null if the device has none. */
    fun requestIntent(): Intent?
}

class RoleControllerScreeningRole(private val roles: RoleController) : ScreeningRole {
    override val isAvailable: Boolean get() = roles.isScreeningRoleAvailable()
    override val isHeld: Boolean get() = roles.isScreeningRoleHeld()

    override fun requestIntent(): Intent? = roles.createScreeningRoleIntent()
}

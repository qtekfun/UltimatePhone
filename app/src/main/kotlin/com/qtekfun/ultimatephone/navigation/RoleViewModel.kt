package com.qtekfun.ultimatephone.navigation

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.qtekfun.ultimatephone.core.telecom.RoleController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Gives screens a way to launch the system "make this your phone app" dialog. */
@HiltViewModel
class RoleViewModel @Inject constructor(private val roles: RoleController) : ViewModel() {
    fun phoneRoleIntent(): Intent? = roles.createDialerRoleIntent()
}

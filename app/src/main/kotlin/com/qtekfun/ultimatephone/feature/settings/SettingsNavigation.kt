package com.qtekfun.ultimatephone.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.settingsGraph(@Suppress("UNUSED_PARAMETER") navController: NavController, onRequestPhoneRole: () -> Unit) {
    composable("settings") { SettingsScreen(onRequestPhoneRole = onRequestPhoneRole) }
}

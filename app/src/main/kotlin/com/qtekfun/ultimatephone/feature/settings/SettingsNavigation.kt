package com.qtekfun.ultimatephone.feature.settings

import androidx.compose.material3.Text
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.settingsGraph(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    composable("settings") { Text("Settings") }
}

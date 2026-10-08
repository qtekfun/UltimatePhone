package com.qtekfun.ultimatephone.feature.data

import androidx.compose.material3.Text
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.qtekfun.ultimatephone.navigation.SETTINGS_DATA_ROUTE

/** Data packs and sources screens, under Settings. Graph route [SETTINGS_DATA_ROUTE]. Placeholder until built. */
fun NavGraphBuilder.dataGraph(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    navigation(startDestination = "$SETTINGS_DATA_ROUTE/home", route = SETTINGS_DATA_ROUTE) {
        composable("$SETTINGS_DATA_ROUTE/home") { Text("Data") }
    }
}

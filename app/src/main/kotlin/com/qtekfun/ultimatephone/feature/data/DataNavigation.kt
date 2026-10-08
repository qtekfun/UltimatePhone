package com.qtekfun.ultimatephone.feature.data

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.qtekfun.ultimatephone.navigation.SETTINGS_DATA_ROUTE

/** Data packs and sources screens, under Settings. Graph route [SETTINGS_DATA_ROUTE]. */
fun NavGraphBuilder.dataGraph(navController: NavController) {
    navigation(startDestination = "$SETTINGS_DATA_ROUTE/home", route = SETTINGS_DATA_ROUTE) {
        composable("$SETTINGS_DATA_ROUTE/home") { DataRoute(onBack = { navController.popBackStack() }) }
    }
}

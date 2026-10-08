package com.qtekfun.ultimatephone.feature.sync

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.qtekfun.ultimatephone.navigation.SETTINGS_SYNC_ROUTE

/** Nextcloud sync screen, under Settings. Graph route [SETTINGS_SYNC_ROUTE]. */
fun NavGraphBuilder.syncGraph(navController: NavController) {
    navigation(startDestination = "$SETTINGS_SYNC_ROUTE/home", route = SETTINGS_SYNC_ROUTE) {
        composable("$SETTINGS_SYNC_ROUTE/home") { SyncRoute(onBack = { navController.popBackStack() }) }
    }
}

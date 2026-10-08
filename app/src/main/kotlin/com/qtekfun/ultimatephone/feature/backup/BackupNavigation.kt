package com.qtekfun.ultimatephone.feature.backup

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.qtekfun.ultimatephone.navigation.SETTINGS_BACKUP_ROUTE

/** Backup export and import, under Settings. Graph route [SETTINGS_BACKUP_ROUTE]. */
fun NavGraphBuilder.backupGraph(navController: NavController) {
    navigation(startDestination = "$SETTINGS_BACKUP_ROUTE/home", route = SETTINGS_BACKUP_ROUTE) {
        composable("$SETTINGS_BACKUP_ROUTE/home") { BackupRoute(onBack = { navController.popBackStack() }) }
    }
}

package com.qtekfun.ultimatephone.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.qtekfun.ultimatephone.feature.backup.backupGraph
import com.qtekfun.ultimatephone.feature.data.dataGraph
import com.qtekfun.ultimatephone.feature.spam.spamGraph
import com.qtekfun.ultimatephone.feature.sync.syncGraph

fun NavGraphBuilder.settingsGraph(navController: NavController, onRequestPhoneRole: () -> Unit) {
    composable("settings") {
        SettingsScreen(onRequestPhoneRole = onRequestPhoneRole, onOpen = { route -> navController.navigate(route) })
    }
    spamGraph(navController)
    dataGraph(navController)
    syncGraph(navController)
    backupGraph(navController)
}

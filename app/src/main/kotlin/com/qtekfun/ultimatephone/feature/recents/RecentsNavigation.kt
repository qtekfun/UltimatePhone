package com.qtekfun.ultimatephone.feature.recents

import androidx.compose.material3.Text
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

/** Call history graph (route "recents"). Placeholder until the recents feature is implemented. */
fun NavGraphBuilder.recentsGraph(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    navigation(startDestination = "recents/list", route = "recents") {
        composable("recents/list") { Text("Recents") }
    }
}

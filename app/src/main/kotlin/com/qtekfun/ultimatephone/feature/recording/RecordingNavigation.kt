package com.qtekfun.ultimatephone.feature.recording

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.qtekfun.ultimatephone.navigation.SETTINGS_RECORDING_ROUTE

/** Settings > Call recording and its list of recordings. Graph route [SETTINGS_RECORDING_ROUTE]. */
fun NavGraphBuilder.recordingGraph(navController: NavController) {
    navigation(startDestination = "$SETTINGS_RECORDING_ROUTE/home", route = SETTINGS_RECORDING_ROUTE) {
        composable("$SETTINGS_RECORDING_ROUTE/home") {
            RecordingSettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenRecordings = { navController.navigate("$SETTINGS_RECORDING_ROUTE/list") }
            )
        }
        composable("$SETTINGS_RECORDING_ROUTE/list") { RecordingsRoute(onBack = { navController.popBackStack() }) }
    }
}

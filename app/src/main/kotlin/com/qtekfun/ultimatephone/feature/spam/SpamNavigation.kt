package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.material3.Text
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.qtekfun.ultimatephone.navigation.SETTINGS_SPAM_ROUTE

/** Spam protection screens, under Settings. Graph route [SETTINGS_SPAM_ROUTE]. Placeholder until the feature is built. */
fun NavGraphBuilder.spamGraph(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    navigation(startDestination = "$SETTINGS_SPAM_ROUTE/home", route = SETTINGS_SPAM_ROUTE) {
        composable("$SETTINGS_SPAM_ROUTE/home") { Text("Spam") }
    }
}

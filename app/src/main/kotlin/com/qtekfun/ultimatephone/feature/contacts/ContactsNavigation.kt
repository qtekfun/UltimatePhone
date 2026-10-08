package com.qtekfun.ultimatephone.feature.contacts

import androidx.compose.material3.Text
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

/** Contacts graph (route "contacts"). Placeholder until the contacts feature is implemented. */
fun NavGraphBuilder.contactsGraph(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    navigation(startDestination = "contacts/list", route = "contacts") {
        composable("contacts/list") { Text("Contacts") }
    }
}

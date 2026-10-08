package com.qtekfun.ultimatephone.feature.contacts

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.qtekfun.ultimatephone.navigation.CONTACT_DETAIL_ROUTE
import com.qtekfun.ultimatephone.navigation.CONTACT_EDIT_ROUTE
import com.qtekfun.ultimatephone.navigation.contactDetailRoute
import com.qtekfun.ultimatephone.navigation.contactEditRoute
import com.qtekfun.ultimatephone.navigation.openDialer

private const val LIST_ROUTE = "contacts/list"

/** Contacts graph (route "contacts"): list, detail and create/edit. */
fun NavGraphBuilder.contactsGraph(navController: NavController) {
    navigation(startDestination = LIST_ROUTE, route = "contacts") {
        composable(LIST_ROUTE) {
            ContactsListScreen(
                onOpenContact = { navController.navigate(contactDetailRoute(it)) },
                onAddContact = { navController.navigate(contactEditRoute()) }
            )
        }
        composable(
            route = CONTACT_DETAIL_ROUTE,
            arguments = listOf(navArgument(ContactDetailViewModel.ARG_LOOKUP_KEY) { type = NavType.StringType })
        ) {
            ContactDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(contactEditRoute(lookupKey = it)) },
                onDialFallback = { navController.openDialer(it) }
            )
        }
        composable(
            route = CONTACT_EDIT_ROUTE,
            arguments = listOf(
                navArgument(ContactEditViewModel.ARG_LOOKUP_KEY) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(ContactEditViewModel.ARG_NUMBER) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            ContactEditScreen(onClose = { navController.popBackStack() })
        }
    }
}

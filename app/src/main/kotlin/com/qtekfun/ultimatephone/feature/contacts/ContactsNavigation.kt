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
private const val GROUPS_ROUTE = "contacts/groups"
private const val DUPLICATES_ROUTE = "contacts/duplicates"
private const val IMPORT_ROUTE = "contacts/import"

/** Contacts graph (route "contacts"): list, detail, create/edit, groups, duplicates and vCard import. */
fun NavGraphBuilder.contactsGraph(navController: NavController) {
    navigation(startDestination = LIST_ROUTE, route = "contacts") {
        composable(LIST_ROUTE) {
            ContactsListScreen(
                onOpenContact = { navController.navigate(contactDetailRoute(it)) },
                onAddContact = { navController.navigate(contactEditRoute()) },
                onOpenGroups = { navController.navigate(GROUPS_ROUTE) },
                onOpenDuplicates = { navController.navigate(DUPLICATES_ROUTE) },
                onOpenImport = { navController.navigate(IMPORT_ROUTE) }
            )
        }
        composable(GROUPS_ROUTE) { GroupsScreen(onBack = { navController.popBackStack() }) }
        composable(DUPLICATES_ROUTE) { DuplicatesScreen(onBack = { navController.popBackStack() }) }
        composable(IMPORT_ROUTE) { VCardImportScreen(onClose = { navController.popBackStack() }) }
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

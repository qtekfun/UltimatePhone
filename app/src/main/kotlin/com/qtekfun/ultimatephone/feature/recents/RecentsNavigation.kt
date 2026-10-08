package com.qtekfun.ultimatephone.feature.recents

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.qtekfun.ultimatephone.navigation.CALL_DETAIL_ROUTE
import com.qtekfun.ultimatephone.navigation.callDetailRoute
import com.qtekfun.ultimatephone.navigation.contactDetailRoute
import com.qtekfun.ultimatephone.navigation.contactEditRoute

private const val LIST_ROUTE = "recents/list"

/** Call history graph (route "recents"): the history list and the detail of one number. */
fun NavGraphBuilder.recentsGraph(navController: NavController) {
    navigation(startDestination = LIST_ROUTE, route = "recents") {
        composable(LIST_ROUTE) {
            RecentsRoute(onOpenDetail = { number -> navController.navigate(callDetailRoute(number)) })
        }
        composable(
            route = CALL_DETAIL_ROUTE,
            arguments = listOf(navArgument(CallDetailViewModel.NUMBER_ARG) { type = NavType.StringType })
        ) {
            CallDetailRoute(
                onBack = { navController.popBackStack() },
                onCreateContact = { number -> navController.navigate(contactEditRoute(number = number)) },
                onAddToContact = { lookupKey, number -> navController.navigate(contactEditRoute(lookupKey = lookupKey, number = number)) },
                onOpenContact = { lookupKey -> navController.navigate(contactDetailRoute(lookupKey)) },
                onEditContact = { lookupKey -> navController.navigate(contactEditRoute(lookupKey = lookupKey)) }
            )
        }
    }
}

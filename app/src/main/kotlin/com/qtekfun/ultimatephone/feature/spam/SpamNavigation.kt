package com.qtekfun.ultimatephone.feature.spam

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.navigation.SETTINGS_DATA_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_SPAM_ROUTE

private const val HOME_ROUTE = "$SETTINGS_SPAM_ROUTE/home"
private const val LIST_ROUTE = "$SETTINGS_SPAM_ROUTE/list/{${SpamListViewModel.LIST_ARG}}"

/** Route of the "why was this flagged" screen for a number; used by the history and the in-call screen too. */
const val SPAM_WHY_ROUTE = "$SETTINGS_SPAM_ROUTE/why/{${WhyFlaggedViewModel.NUMBER_ARG}}"

fun spamWhyRoute(number: String): String = "$SETTINGS_SPAM_ROUTE/why/${android.net.Uri.encode(number)}"

private fun spamListRoute(list: ListType): String = "$SETTINGS_SPAM_ROUTE/list/${list.routeName()}"

/** Spam protection screens, under Settings. Graph route [SETTINGS_SPAM_ROUTE]. */
fun NavGraphBuilder.spamGraph(navController: NavController) {
    navigation(startDestination = HOME_ROUTE, route = SETTINGS_SPAM_ROUTE) {
        composable(HOME_ROUTE) {
            SpamHomeRoute(
                onBack = { navController.popBackStack() },
                onOpenOwnList = { navController.navigate(spamListRoute(ListType.OWN)) },
                onOpenAllowed = { navController.navigate(spamListRoute(ListType.WHITELIST)) },
                onOpenData = { navController.navigate(SETTINGS_DATA_ROUTE) }
            )
        }
        composable(LIST_ROUTE, arguments = listOf(navArgument(SpamListViewModel.LIST_ARG) { type = NavType.StringType })) {
            SpamListRoute(onBack = { navController.popBackStack() })
        }
        composable(SPAM_WHY_ROUTE, arguments = listOf(navArgument(WhyFlaggedViewModel.NUMBER_ARG) { type = NavType.StringType })) {
            WhyFlaggedRoute(onBack = { navController.popBackStack() })
        }
    }
}

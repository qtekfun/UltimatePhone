package com.qtekfun.ultimatephone.feature.dialer

import androidx.compose.material3.Text
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.qtekfun.ultimatephone.navigation.DIALER_NUMBER_ARG
import com.qtekfun.ultimatephone.navigation.DIALER_ROUTE_PATTERN

fun NavGraphBuilder.dialerGraph(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    composable(
        route = DIALER_ROUTE_PATTERN,
        arguments = listOf(
            navArgument(DIALER_NUMBER_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { Text("Dialer") }
}

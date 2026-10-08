package com.qtekfun.ultimatephone.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qtekfun.ultimatephone.feature.contacts.contactsGraph
import com.qtekfun.ultimatephone.feature.dialer.dialerGraph
import com.qtekfun.ultimatephone.feature.recents.recentsGraph
import com.qtekfun.ultimatephone.feature.settings.settingsGraph

@Composable
fun AppNavHost(initialDialNumber: String?) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val hierarchy = backStack?.destination?.hierarchy
    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = hierarchy?.any { it.route?.startsWith(destination.route) == true } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = dialerRoute(initialDialNumber),
            modifier = Modifier.padding(padding)
        ) {
            dialerGraph(navController)
            recentsGraph(navController)
            contactsGraph(navController)
            settingsGraph(navController)
        }
    }
}

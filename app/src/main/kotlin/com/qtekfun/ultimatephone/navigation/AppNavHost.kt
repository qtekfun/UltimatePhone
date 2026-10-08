package com.qtekfun.ultimatephone.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qtekfun.ultimatephone.feature.contacts.contactsGraph
import com.qtekfun.ultimatephone.feature.dialer.dialerGraph
import com.qtekfun.ultimatephone.feature.onboarding.OnboardingGateViewModel
import com.qtekfun.ultimatephone.feature.onboarding.OnboardingRoute
import com.qtekfun.ultimatephone.feature.recents.recentsGraph
import com.qtekfun.ultimatephone.feature.settings.settingsGraph

/**
 * The app's navigation. Until the first-run flow (onboarding) has been completed, it is the only thing shown; it works
 * without an account or a network and its steps can be skipped.
 */
@Composable
fun AppNavHost(initialDialNumber: String?) {
    val gate: OnboardingGateViewModel = hiltViewModel()
    val onboardingCompleted by gate.completed.collectAsStateWithLifecycle()
    when (onboardingCompleted) {
        null -> Box(Modifier.fillMaxSize())
        false -> OnboardingRoute(onFinished = {})
        true -> MainContent(initialDialNumber)
    }
}

@Composable
private fun MainContent(initialDialNumber: String?) {
    val navController = rememberNavController()
    val roleViewModel: RoleViewModel = hiltViewModel()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val requestPhoneRole = { roleViewModel.phoneRoleIntent()?.let { roleLauncher.launch(it) } ?: Unit }
    // A new tel: link while the app is already open must reach the keypad too, not only the first one.
    LaunchedEffect(initialDialNumber) {
        if (!initialDialNumber.isNullOrBlank()) navController.openDialer(initialDialNumber)
    }
    val backStack by navController.currentBackStackEntryAsState()
    val hierarchy = backStack?.destination?.hierarchy
    val inSetupGuide = backStack?.destination?.route == ONBOARDING_ROUTE
    Scaffold(
        bottomBar = {
            if (!inSetupGuide) {
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
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = dialerRoute(initialDialNumber),
            modifier = Modifier.padding(padding)
        ) {
            dialerGraph(navController, requestPhoneRole)
            recentsGraph(navController)
            contactsGraph(navController)
            settingsGraph(navController, requestPhoneRole)
            composable(ONBOARDING_ROUTE) { OnboardingRoute(onFinished = { navController.popBackStack() }, embedded = true) }
        }
    }
}

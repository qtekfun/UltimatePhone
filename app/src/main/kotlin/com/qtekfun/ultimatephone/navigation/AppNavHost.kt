package com.qtekfun.ultimatephone.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qtekfun.ultimatephone.core.designsystem.LocalReduceMotion
import com.qtekfun.ultimatephone.feature.contacts.contactsGraph
import com.qtekfun.ultimatephone.feature.dialer.dialerGraph
import com.qtekfun.ultimatephone.feature.onboarding.OnboardingGateViewModel
import com.qtekfun.ultimatephone.feature.onboarding.OnboardingRoute
import com.qtekfun.ultimatephone.feature.recents.recentsGraph
import com.qtekfun.ultimatephone.feature.settings.settingsGraph
import com.qtekfun.ultimatephone.feature.spam.spamWhyRoute

/**
 * The app's navigation. Until the first-run flow (onboarding) has been completed, it is the only thing shown; it works
 * without an account or a network and its steps can be skipped.
 */
@Composable
fun AppNavHost(initialDialNumber: String?, whyFlaggedNumber: String? = null, onWhyFlaggedHandled: () -> Unit = {}) {
    val gate: OnboardingGateViewModel = hiltViewModel()
    val onboardingCompleted by gate.completed.collectAsStateWithLifecycle()
    when (onboardingCompleted) {
        null -> Box(Modifier.fillMaxSize())
        false -> OnboardingRoute(onFinished = {})
        true -> MainContent(initialDialNumber, whyFlaggedNumber, onWhyFlaggedHandled)
    }
}

@Composable
private fun MainContent(initialDialNumber: String?, whyFlaggedNumber: String?, onWhyFlaggedHandled: () -> Unit) {
    val navController = rememberNavController()
    val roleViewModel: RoleViewModel = hiltViewModel()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val requestPhoneRole = { roleViewModel.phoneRoleIntent()?.let { roleLauncher.launch(it) } ?: Unit }
    // A new tel: link while the app is already open must reach the keypad too, not only the first one.
    LaunchedEffect(initialDialNumber) {
        if (!initialDialNumber.isNullOrBlank()) navController.openDialer(initialDialNumber)
    }
    // Asked for by the call screen: open the explanation of its spam warning. Until the setup guide is done this is not reached.
    LaunchedEffect(whyFlaggedNumber) {
        if (whyFlaggedNumber != null) {
            navController.navigate(spamWhyRoute(whyFlaggedNumber)) { launchSingleTop = true }
            onWhyFlaggedHandled()
        }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val hierarchy = backStack?.destination?.hierarchy
    val inSetupGuide = backStack?.destination?.route == ONBOARDING_ROUTE
    Scaffold(
        bottomBar = {
            if (!inSetupGuide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                            label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        // The scaffold only pads for the bottom bar. Everything else the system draws over (status bar, cutout, side bars, the
        // keyboard) is applied once here and consumed, so screens with their own top bar never pad twice. The setup guide
        // is not padded here: it handles every inset itself.
        val containerInsets = if (inSetupGuide) {
            Modifier
        } else {
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .imePadding()
        }
        val reduceMotion = LocalReduceMotion.current
        NavHost(
            navController = navController,
            startDestination = dialerRoute(initialDialNumber),
            modifier = containerInsets,
            enterTransition = { if (reduceMotion) EnterTransition.None else enterFor(forward = true) },
            exitTransition = { if (reduceMotion) ExitTransition.None else exitFor(forward = true) },
            popEnterTransition = { if (reduceMotion) EnterTransition.None else enterFor(forward = false) },
            popExitTransition = { if (reduceMotion) ExitTransition.None else exitFor(forward = false) }
        ) {
            dialerGraph(navController, requestPhoneRole)
            recentsGraph(navController)
            contactsGraph(navController)
            settingsGraph(navController, requestPhoneRole)
            composable(ONBOARDING_ROUTE) { OnboardingRoute(onFinished = { navController.popBackStack() }) }
        }
    }
}

// ---- Transitions ---------------------------------------------------------------------------------------------------------

private const val FADE_IN_MS = 210
private const val FADE_IN_DELAY_MS = 90
private const val FADE_OUT_MS = 90
private const val SLIDE_MS = 300
private const val SLIDE_DIVISOR = 10
private const val TAB_START_SCALE = 0.96f

/** The top-level graph a destination belongs to, or null (the setup guide). */
private fun NavBackStackEntry.topLevelRoute(): String? =
    destination.hierarchy.mapNotNull { it.route }.firstOrNull { route -> TopLevelDestination.entries.any { it.route == route } }

/** Switching between tabs fades through; going deeper or back inside a tab slides a little and fades. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.switchesTab(): Boolean = initialState.topLevelRoute() != targetState.topLevelRoute()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(forward: Boolean): EnterTransition = if (switchesTab()) {
    fadeIn(tween(FADE_IN_MS, FADE_IN_DELAY_MS, FastOutSlowInEasing)) + scaleIn(tween(SLIDE_MS, easing = FastOutSlowInEasing), TAB_START_SCALE)
} else {
    slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { width -> if (forward) width / SLIDE_DIVISOR else -width / SLIDE_DIVISOR } +
        fadeIn(tween(FADE_IN_MS, FADE_IN_DELAY_MS, FastOutSlowInEasing))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(forward: Boolean): ExitTransition = if (switchesTab()) {
    fadeOut(tween(FADE_OUT_MS, easing = FastOutSlowInEasing))
} else {
    slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { width -> if (forward) -width / SLIDE_DIVISOR else width / SLIDE_DIVISOR } +
        fadeOut(tween(FADE_OUT_MS, easing = FastOutSlowInEasing))
}

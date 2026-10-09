package com.qtekfun.ultimatephone.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/** How the app picks light or dark; the user setting maps onto this. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Material 3 Expressive with the brand colours (deep teal and mint) by default.
 *
 * @param mode light, dark or follow the system.
 * @param useSystemColors true to use the Material You colours of the wallpaper instead of the brand colours (Android 12+;
 * ignored where unavailable). Off by default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UltimatePhoneTheme(mode: ThemeMode = ThemeMode.SYSTEM, useSystemColors: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        useSystemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        useSystemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        else -> brandColorScheme(dark)
    }
    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = AppTypography,
            content = content
        )
    }
}

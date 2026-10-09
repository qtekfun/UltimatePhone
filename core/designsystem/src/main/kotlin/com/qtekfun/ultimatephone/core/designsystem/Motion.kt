@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.qtekfun.ultimatephone.core.designsystem

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user turned animations off (system "Remove animations" / animator duration scale 0). Provided by
 * [UltimatePhoneTheme]; read it with `LocalReduceMotion.current` or use [motionSpatialSpec] / [motionEffectsSpec], which
 * already honour it.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Whether the system animator duration scale is 0 right now. */
fun isMotionReduced(context: Context): Boolean = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Tracks [isMotionReduced] and updates when the user changes the system setting while the app is open. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(isMotionReduced(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = isMotionReduced(context)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** The expressive "fast spatial" spring (movement, size, shape), or an instant jump when motion is reduced. */
@Composable
fun <T> motionSpatialSpec(): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

/** The expressive "default effects" spring (colour, alpha), or an instant jump when motion is reduced. */
@Composable
fun <T> motionEffectsSpec(): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

/**
 * Press feedback for a custom control: it shrinks slightly while pressed and springs back. Pass the same
 * [InteractionSource] the control's clickable uses. With reduced motion it does nothing.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = PRESSED_SCALE): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !LocalReduceMotion.current) pressedScale else 1f,
        animationSpec = motionSpatialSpec(),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private const val PRESSED_SCALE = 0.94f

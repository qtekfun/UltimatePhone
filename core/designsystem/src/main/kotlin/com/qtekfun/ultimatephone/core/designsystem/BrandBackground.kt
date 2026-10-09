package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val GLOW_ALPHA = 0.14f
private const val WASH_ALPHA = 0.55f

/**
 * The call screen's backdrop: the surface colour with a soft teal wash fading in from the top and a faint mint glow behind
 * where the avatar sits. It is decorative and low contrast; text on it uses the normal `onSurface` colours (checked on the
 * strongest point of the wash). Fill the screen with it and put the content inside.
 */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val wash = MaterialTheme.colorScheme.primaryContainer.copy(alpha = WASH_ALPHA)
    Box(
        modifier = modifier.background(surface).drawBehind {
            drawRect(Brush.verticalGradient(listOf(wash, surface), startY = 0f, endY = size.height * 0.75f))
            drawRect(
                Brush.radialGradient(
                    listOf(BrandColors.Mint.copy(alpha = GLOW_ALPHA), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.18f),
                    radius = size.width * 0.8f
                )
            )
        },
        content = content
    )
}

private const val RING_PERIOD_MS = 2000
private const val RING_STROKE_DP = 3f

/**
 * Draws a soft ring that expands from [content] and fades out, over and over, while [active] (a ringing call). With reduced
 * motion, or when not active, the ring is not drawn (a still ring would suggest a state that is not there). It reserves
 * [extent] of space around the content for the ring.
 */
@Composable
fun PulsingRing(
    active: Boolean,
    modifier: Modifier = Modifier,
    extent: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    val animate = active && !LocalReduceMotion.current
    // The clock only runs while the ring is visible; the value is read while drawing, so nothing recomposes per frame.
    val progress: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "ringing").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(RING_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "ringProgress"
        )
    } else {
        null
    }
    Box(
        modifier = modifier.padding(extent).drawBehind {
            val current = progress?.value ?: return@drawBehind
            val base = size.minDimension / 2
            val grow = extent.toPx()
            // Two rings half a period apart, each growing and fading.
            listOf(current, (current + 0.5f) % 1f).forEach { p ->
                drawCircle(
                    color = color.copy(alpha = (1f - p) * 0.5f),
                    radius = base + grow * p,
                    style = Stroke(width = RING_STROKE_DP.dp.toPx())
                )
            }
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

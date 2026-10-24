package com.peoplehub.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.radialGlow
import com.peoplehub.core.ui.theme.PeopleHubTheme

private const val GLOW_MIN_ALPHA = 0.6f
private const val GLOW_MAX_SCALE = 1.14f
private const val GLOW_STATIC_ALPHA = 0.8f

/**
 * A soft accent glow: a radial gradient blob that slowly breathes (opacity .6 -> 1, scale 1 -> 1.14
 * over 7 s), the "bagliore" behind every Velluto screen. Static when animations are off.
 *
 * @param strong uses the strong-to-soft accent ramp (top-right glow); otherwise the soft one.
 * @param delayMillis phase offset, so two glows on one screen do not pulse in sync.
 */
@Composable
fun AmbientGlow(
    size: Dp,
    modifier: Modifier = Modifier,
    strong: Boolean = true,
    color: Color = if (strong) PeopleHubTheme.colors.glowStrong else PeopleHubTheme.colors.glowSoft,
    delayMillis: Int = 0,
) {
    val colors = PeopleHubTheme.colors
    val motion = PeopleHubTheme.motion
    val mid = if (strong) colors.glowSoft else Color.Transparent
    val pulse = rememberPulse(motion.reduced, motion.glowMillis, delayMillis)
    Box(
        modifier =
            modifier
                .requiredSize(size)
                .graphicsLayer {
                    val p = pulse?.value
                    alpha = if (p == null) GLOW_STATIC_ALPHA else GLOW_MIN_ALPHA + (1f - GLOW_MIN_ALPHA) * p
                    val s = if (p == null) 1f else 1f + (GLOW_MAX_SCALE - 1f) * p
                    scaleX = s
                    scaleY = s
                }.radialGlow(color = color, mid = mid),
    )
}

/** A 0..1..0 ease-in-out loop, or `null` when animations are off. */
@Composable
internal fun rememberPulse(
    reduced: Boolean,
    periodMillis: Int,
    delayMillis: Int = 0,
): State<Float>? {
    if (reduced) return null
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = periodMillis / 2, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(delayMillis),
            ),
        label = "pulseValue",
    )
}

/**
 * The full-screen Velluto backdrop: the background colour plus the breathing accent glow in the
 * top-right corner (and, with [secondGlow], a soft one on the left at mid height), as in every
 * mockup. Put the screen's content in [content]; the glows never intercept touches.
 */
@Composable
fun VellutoBackground(
    modifier: Modifier = Modifier,
    secondGlow: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = PeopleHubTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        AmbientGlow(
            size = 460.dp,
            strong = true,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 130.dp, y = (-175).dp),
        )
        if (secondGlow) {
            AmbientGlow(
                size = 400.dp,
                strong = false,
                delayMillis = 2000,
                modifier = Modifier.align(Alignment.TopStart).offset(x = (-185).dp, y = 420.dp),
            )
        }
        content()
    }
}

/**
 * The fade that dissolves scrolling content into the background above the floating navigation bar
 * (`linear-gradient(0deg, bg 18%, transparent)`, 130dp tall).
 */
@Composable
fun BottomFade(
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
) {
    val bg = PeopleHubTheme.colors.bg
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(
                    remember(bg) {
                        Brush.verticalGradient(
                            0f to bg.copy(alpha = 0f),
                            BOTTOM_FADE_SOLID to bg,
                        )
                    },
                ),
    )
}

/** The fade is fully opaque over its bottom 18 %. */
private const val BOTTOM_FADE_SOLID = 0.82f

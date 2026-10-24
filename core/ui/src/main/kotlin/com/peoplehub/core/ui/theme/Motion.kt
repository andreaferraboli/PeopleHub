package com.peoplehub.core.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Velluto motion tokens: one easing (the mockups' `cubic-bezier(.2,.8,.2,1)`), cascading entrances
 * and slow ambient loops. When [reduced] is true (the system "remove animations" setting, i.e.
 * `ANIMATOR_DURATION_SCALE == 0`) every entrance renders in its final state and every ambient loop
 * stands still. Read it with `PeopleHubTheme.motion`.
 */
@Immutable
data class VellutoMotion(
    val reduced: Boolean = false,
    /** Entrance (rise + fade) duration. */
    val riseMillis: Int = 800,
    /** Vertical travel of the entrance. */
    val riseOffset: Dp = 16.dp,
    /** Delay added per item in a cascade. */
    val staggerMillis: Int = 60,
    /** Cap on the cascade delay, so long lists do not wait seconds for their tail. */
    val maxStaggerMillis: Int = 480,
    /** Pop-in of FABs and KPI numbers. */
    val popMillis: Int = 600,
    /** Horizontal fill of segment bars. */
    val fillMillis: Int = 900,
    /** Breathing glow of primary actions. */
    val breathMillis: Int = 4000,
    /** Float of the 3D objects. */
    val floatMillis: Int = 6500,
    /** Pulse of the background glows. */
    val glowMillis: Int = 7000,
    /** Press / state change. */
    val quickMillis: Int = 200,
) {
    /** The single Velluto easing curve. */
    val easing: Easing get() = VellutoEasing
}

/** `cubic-bezier(.2, .8, .2, 1)`: fast start, long soft landing. */
val VellutoEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

internal val LocalVellutoMotion = staticCompositionLocalOf { VellutoMotion() }

/**
 * Whether the user asked the system to remove animations (Settings > Accessibility > Remove
 * animations, or Developer options > Animator duration scale = off).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

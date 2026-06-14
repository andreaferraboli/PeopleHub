package com.peoplehub.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.peoplehub.core.domain.model.CheckInStatus

/**
 * Semantic colour pair used to render a check-in status as a filled container with readable
 * foreground text.
 */
@Immutable
data class StatusColors(val container: Color, val onContainer: Color)

/**
 * Brand colours that fall outside the Material 3 [ColorScheme] — currently the green "fresh" status
 * used by the frequency tracker. Provided through [LocalExtendedColors] so composables can read them
 * the same way they read [androidx.compose.material3.MaterialTheme].
 */
@Immutable
data class ExtendedColors(
    val fresh: StatusColors,
)

internal val LocalExtendedColors =
    staticCompositionLocalOf {
        ExtendedColors(
            fresh = StatusColors(MidnightGold.FreshContainerDark, MidnightGold.OnFreshContainerDark),
        )
    }

/**
 * Resolves the [StatusColors] for a [CheckInStatus] against the active [ColorScheme] and
 * [ExtendedColors]. FRESH is green, DUE is amber (secondary container), OVERDUE/NEVER are error.
 */
@Composable
@ReadOnlyComposable
fun statusColorsFor(status: CheckInStatus): StatusColors {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val extended = LocalExtendedColors.current
    return when (status) {
        CheckInStatus.FRESH -> extended.fresh
        CheckInStatus.DUE -> StatusColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        CheckInStatus.OVERDUE -> StatusColors(scheme.errorContainer, scheme.onErrorContainer)
        CheckInStatus.NEVER -> StatusColors(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
}

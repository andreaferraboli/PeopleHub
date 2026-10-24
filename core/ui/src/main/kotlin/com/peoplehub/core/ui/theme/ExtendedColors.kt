package com.peoplehub.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.peoplehub.core.domain.model.CheckInStatus

/**
 * Semantic colours for a check-in status: a soft translucent [container], the readable
 * [onContainer] text/dot colour, and the solid [ring] drawn around the person's avatar.
 */
@Immutable
data class StatusColors(
    val container: Color,
    val onContainer: Color,
    val ring: Color = onContainer,
)

internal val LocalVellutoColors = staticCompositionLocalOf { vellutoColors(dark = true) }

/**
 * Resolves the [StatusColors] for a [CheckInStatus]: FRESH is green ("seen recently"), DUE is amber
 * ("to see soon"), OVERDUE is red, NEVER is the neutral track ("not tracked yet").
 */
@Composable
@ReadOnlyComposable
fun statusColorsFor(status: CheckInStatus): StatusColors {
    val c = LocalVellutoColors.current
    return when (status) {
        CheckInStatus.FRESH -> StatusColors(c.okSoft, c.ok)
        CheckInStatus.DUE -> StatusColors(c.warnSoft, c.warn)
        CheckInStatus.OVERDUE -> StatusColors(c.errSoft, c.err)
        CheckInStatus.NEVER -> StatusColors(c.surface2, c.text2, ring = c.track)
    }
}

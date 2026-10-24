package com.peoplehub.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Velluto spacing scale and fixed layout measures, read from the 390x844 mockups. */
@Immutable
data class VellutoSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 6.dp,
    val s: Dp = 8.dp,
    val sm: Dp = 10.dp,
    val m: Dp = 12.dp,
    val ml: Dp = 14.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 28.dp,
    val section: Dp = 32.dp,
    /** Horizontal gutter of cards and lists. */
    val gutter: Dp = 16.dp,
    /** Horizontal inset of hero titles and headers (a touch wider than the gutter). */
    val heroInset: Dp = 20.dp,
    /** Minimum touch target (Material / WCAG). */
    val touchTarget: Dp = 48.dp,
    /** Floating navigation bar: height, width and distance from the bottom edge. */
    val navHeight: Dp = 68.dp,
    val navWidth: Dp = 316.dp,
    val navBottom: Dp = 22.dp,
    /** Gap kept between the FAB and the floating navigation bar. */
    val fabAboveNav: Dp = 16.dp,
)

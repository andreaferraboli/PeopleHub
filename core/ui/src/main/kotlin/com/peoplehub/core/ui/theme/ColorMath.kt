package com.peoplehub.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** WCAG AA threshold for normal-size text. */
internal const val AA_TEXT_CONTRAST = 4.5f

private const val READABLE_STEP = 0.05f
private const val CONTRAST_OFFSET = 0.05f

/**
 * Linear per-channel interpolation from [this] towards [other] by [fraction] (0 = this, 1 = other),
 * rounded to whole 8-bit channels exactly like the design mockups' `mix()` helper, so the derived
 * tokens match the approved values to the digit.
 */
internal fun Color.mixWith(
    other: Color,
    fraction: Float,
): Color {
    fun channel(
        a: Float,
        b: Float,
    ): Float = ((a * 255f + (b * 255f - a * 255f) * fraction).roundToInt() / 255f)
    return Color(
        red = channel(red, other.red),
        green = channel(green, other.green),
        blue = channel(blue, other.blue),
        alpha = 1f,
    )
}

/** WCAG 2.x contrast ratio between two opaque colours (1..21). */
internal fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val x = a.luminance()
    val y = b.luminance()
    return (max(x, y) + CONTRAST_OFFSET) / (min(x, y) + CONTRAST_OFFSET)
}

/**
 * Darkens or lightens [this] towards [toward] in 5 % steps until it reaches AA contrast (4.5:1)
 * against [background] — the mockups' `readable()` helper. A translucent [background] is first
 * composited over [base].
 */
internal fun Color.readableOn(
    background: Color,
    toward: Color,
    base: Color = background,
): Color {
    val opaqueBackground = if (background.alpha < 1f) background.compositeOver(base) else background
    var candidate = this
    var step = 0f
    while (contrastRatio(candidate, opaqueBackground) < AA_TEXT_CONTRAST && step < 1f) {
        step += READABLE_STEP
        candidate = mixWith(toward, step)
    }
    return candidate
}

/** Picks whichever of [dark] / [light] contrasts more with [this] — the mockups' `onAccent` rule. */
internal fun Color.bestContentColor(
    dark: Color,
    light: Color,
): Color = if (contrastRatio(this, dark) >= contrastRatio(this, light)) dark else light

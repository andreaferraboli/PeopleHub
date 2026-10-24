package com.peoplehub.core.ui.modifier

import androidx.compose.foundation.background
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.theme.VellutoColors

/**
 * A CSS-like drop shadow: [blur] is the CSS blur radius, [offsetY] the vertical offset. Velluto uses
 * coloured shadows (warm in light, black in dark, accent for glows), which `Modifier.shadow` cannot
 * draw below API 28; this paints the shape's outline with a framework shadow layer instead.
 * On API < 28 hardware rendering ignores shadow layers, so the element simply renders flat.
 */
@Immutable
data class ShadowSpec(
    val blur: Dp,
    val offsetY: Dp = 0.dp,
)

/** The shadow presets of the mockups. */
object VellutoElevation {
    /** List cards: `0 14px 30px`. */
    val Card = ShadowSpec(blur = 30.dp, offsetY = 14.dp)

    /** Section and hero cards: `0 20px 40px`. */
    val Raised = ShadowSpec(blur = 40.dp, offsetY = 20.dp)

    /** Event covers and the notched hero: `0 24px 46px`. */
    val Hero = ShadowSpec(blur = 46.dp, offsetY = 24.dp)

    /** Floating navigation bar: `0 20px 40px`. */
    val Nav = ShadowSpec(blur = 40.dp, offsetY = 20.dp)

    /** Accent glow under FABs and primary pills: `0 14px 34px`. */
    val Glow = ShadowSpec(blur = 34.dp, offsetY = 14.dp)

    /** Small glow under the selected nav item / chips: `0 6px 18px`. */
    val GlowSmall = ShadowSpec(blur = 18.dp, offsetY = 6.dp)
}

/** Converts a CSS blur radius to the framework shadow-layer radius (Gaussian sigma ~ blur / 2). */
private const val CSS_BLUR_TO_LAYER = 0.5f

/** Draws a soft coloured shadow of [shape] behind the element. No-op when [color] is transparent. */
fun Modifier.softShadow(
    color: Color,
    shape: Shape,
    spec: ShadowSpec,
): Modifier =
    if (color.alpha == 0f) {
        this
    } else {
        drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val paint =
                Paint().also {
                    it.asFrameworkPaint().apply {
                        isAntiAlias = true
                        this.color = android.graphics.Color.TRANSPARENT
                        setShadowLayer(
                            spec.blur.toPx() * CSS_BLUR_TO_LAYER,
                            0f,
                            spec.offsetY.toPx(),
                            color.toArgb(),
                        )
                    }
                }
            onDrawWithContent {
                drawIntoCanvas { canvas -> canvas.drawOutline(outline, paint) }
                drawContent()
            }
        }
    }

/**
 * The Velluto "light edge": a 1dp hairline in [border] all around [shape], plus the CSS
 * `inset 0 1px 0 hi` top highlight, drawn as a stroke that fades out over the top ~28dp.
 * Pass `Color.Transparent` for either to skip it.
 */
fun Modifier.lightEdge(
    shape: Shape,
    highlight: Color,
    border: Color,
    width: Dp = 1.dp,
): Modifier =
    drawWithCache {
        val strokePx = width.toPx()
        val inner = Size(size.width - strokePx, size.height - strokePx)
        val outline = shape.createOutline(inner, layoutDirection, this)
        val stroke = Stroke(strokePx)
        val fade = HIGHLIGHT_FADE.toPx().coerceAtMost(size.height)
        val highlightBrush = Brush.verticalGradient(listOf(highlight, Color.Transparent), startY = 0f, endY = fade)
        onDrawWithContent {
            drawContent()
            translate(strokePx / 2f, strokePx / 2f) {
                if (border.alpha > 0f) drawOutline(outline, border, style = stroke)
                if (highlight.alpha > 0f) drawOutline(outline, highlightBrush, style = stroke)
            }
        }
    }

private val HIGHLIGHT_FADE = 28.dp

/**
 * Velluto glass: translucent [VellutoColors.glass] fill, light edge and (optionally) a soft shadow.
 * True backdrop blur is applied only where content scrolls beneath the glass (the floating
 * navigation bar, via Haze on Android 12+); everywhere else glass sits on the flat background, where
 * blur would be invisible, so the translucent fill plus the light edge *is* the effect.
 */
fun Modifier.glassSurface(
    colors: VellutoColors,
    shape: Shape,
    shadow: ShadowSpec? = null,
    fill: Color = colors.glass,
): Modifier =
    (if (shadow != null) softShadow(colors.shadowSoft, shape, shadow) else this)
        .background(fill, shape)
        .lightEdge(shape, highlight = colors.hi, border = colors.line)

/** An opaque Velluto card: [VellutoColors.surface] fill, light edge, optional soft shadow. */
fun Modifier.cardSurface(
    colors: VellutoColors,
    shape: Shape,
    shadow: ShadowSpec? = null,
    fill: Color = colors.surface,
    border: Color = colors.line,
): Modifier =
    (if (shadow != null) softShadow(colors.shadowSoft, shape, shadow) else this)
        .background(fill, shape)
        .lightEdge(shape, highlight = colors.hi, border = border)

/** A radial glow centred in the element: [color] at the centre fading to transparent at 70 %. */
fun Modifier.radialGlow(
    color: Color,
    mid: Color = Color.Transparent,
): Modifier =
    drawWithCache {
        // CSS `radial-gradient(circle, ...)` sizes the ray to the farthest corner.
        val radius = kotlin.math.hypot(size.width, size.height) / 2f
        val brush =
            Brush.radialGradient(
                colorStops =
                    if (mid.alpha > 0f) {
                        arrayOf(0f to color, 0.45f * GLOW_EXTENT to mid, GLOW_EXTENT to Color.Transparent)
                    } else {
                        arrayOf(0f to color, GLOW_EXTENT to Color.Transparent)
                    },
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius,
            )
        onDrawBehind { drawRect(brush) }
    }

/** The CSS glows fade to transparent at 70 % of the radius; past that they are invisible. */
private const val GLOW_EXTENT = 0.7f

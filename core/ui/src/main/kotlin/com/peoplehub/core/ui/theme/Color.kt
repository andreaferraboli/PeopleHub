package com.peoplehub.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The single brand accent of the "Velluto" identity. **This is the only colour to change to
 * re-colour the app**: every accent-derived token (readable accent text, soft fills, edges, glows,
 * the five-stop gold ramp of the 3D objects, the colour of content drawn on the accent) is computed
 * from it in [vellutoColors].
 */
val VellutoAccent: Color = Color(0xFFFFC83D)

/** The fixed, non-accent palette of the two themes, transcribed from the approved mockups. */
internal object VellutoPalette {
    val Ink = Color(0xFF0B0A09)
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    // Dark theme
    val DarkBg = Color(0xFF0B0A09)
    val DarkSurface = Color(0xFF161412)
    val DarkSurfaceHi = Color(0xFF221E1A)
    val DarkSurface2 = Color(0xFF1F1C19)
    val DarkText = Color(0xFFF5F1EA)
    val DarkText2 = Color(0xFFA8A097)
    val DarkNavGlass = Color(0x9E1E1B18)
    val DarkRingGap = Color(0xFF141210)
    val DarkHeroBg = Color(0xFF1C1814)
    val DarkOk = Color(0xFF2FD07A)
    val DarkWarn = Color(0xFFFFC23D)
    val DarkErr = Color(0xFFFF5A5F)

    // Light theme
    val LightBg = Color(0xFFF4F1EC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurface2 = Color(0xFFF7F4EF)
    val LightText = Color(0xFF141210)
    val LightText2 = Color(0xFF5E574F)
    val LightHeroBg = Color(0xFF14110E)

    // Mockup values #0E7A45 / #C8282E, deepened slightly so the text stays AA on their own soft
    // fill when that fill sits on the page background (4.2:1 -> 4.8:1).
    val LightOk = Color(0xFF0C7040)
    val LightWarn = Color(0xFF8F5A00)
    val LightErr = Color(0xFFB8252B)

    /** Text on the always-dark hero card and on photo/scene covers, in both themes. */
    val OnMedia = Color(0xFFFFF8EE)

    /** Secondary text on media and on the hero card. */
    val OnMediaMuted = Color(0xDBFFF8EE)
}

/**
 * The full semantic colour set of the Velluto theme. Read it with `PeopleHubTheme.colors`.
 *
 * Translucent tokens (glass, hi, line, soft fills, glows, shadows) are meant to be drawn over the
 * background or a surface, exactly like the CSS `rgba()` values of the mockups. Every *text* token has
 * been checked for WCAG AA (>= 4.5:1) against the surfaces it is used on; see DESIGN.md.
 */
@Immutable
data class VellutoColors(
    val isDark: Boolean,
    /** Screen background. */
    val bg: Color,
    /** Opaque card surface. */
    val surface: Color,
    /** The lit top of gradient hero cards (gradient from [surfaceHi] to [surface]). */
    val surfaceHi: Color,
    /** Secondary fill: date tiles, inner rows, neutral buttons. */
    val surface2: Color,
    /** Primary text. */
    val text: Color,
    /** Secondary text, captions, inactive icons. */
    val text2: Color,
    /** Translucent glass fill for floating panels, chips and search. */
    val glass: Color,
    /** Glass of the floating navigation bar (more opaque). */
    val navGlass: Color,
    /** The 1px inner top highlight of glass and cards. */
    val hi: Color,
    /** Hairline borders and dividers. */
    val line: Color,
    /** Large drop shadow colour. */
    val shadow: Color,
    /** Soft drop shadow colour for list cards. */
    val shadowSoft: Color,
    /** Neutral track of rings, bars and the "not tracked" status. */
    val track: Color,
    /** The thin gap drawn between an avatar and its status ring. */
    val ringGap: Color,
    /** The brand accent, used as a FILL (buttons, active nav, today, FAB). */
    val accent: Color,
    /** Content (text, icons) drawn on [accent]. */
    val onAccent: Color,
    /** Translucent accent fill for soft buttons and selected chips. */
    val accentSoft: Color,
    /** Accent hairline around [accentSoft] fills. */
    val accentEdge: Color,
    /** Accent as TEXT on [bg] (AA). */
    val accentText: Color,
    /** Accent as TEXT on [surface] (AA). */
    val accentTextSurface: Color,
    /** Accent as TEXT on an [accentSoft] fill (AA). */
    val accentOnSoft: Color,
    /** Strong accent glow (FAB, today, KPI text-shadow). */
    val glowStrong: Color,
    /** Soft ambient accent glow (background blobs). */
    val glowSoft: Color,
    /** "Seen recently" / success. */
    val ok: Color,
    /** "Due soon" / warning. */
    val warn: Color,
    /** "Overdue" / error / destructive. */
    val err: Color,
    val okSoft: Color,
    val warnSoft: Color,
    val errSoft: Color,
    val warnEdge: Color,
    val errEdge: Color,
    /** Background of the always-dark hero card (Promemoria "due today"). */
    val heroBg: Color,
    /** Accent as TEXT on [heroBg] (AA). */
    val accentOnHero: Color,
    /** Accent glow on [heroBg]. */
    val glowHero: Color,
    val glowHeroSoft: Color,
    /** Fill of glass inside 3D objects (hourglass) and its stroke. */
    val glassFill: Color,
    val glassStroke: Color,
    /** Drop shadow under floating 3D objects. */
    val dropShadow: Color,
    /** Gold ramp of the 3D objects, from the lightest highlight [g1] to the deepest shade [g5]. */
    val g1: Color,
    val g2: Color,
    val g3: Color,
    val g4: Color,
    val g5: Color,
) {
    /** Text on photos and illustrated covers (always light, the cover is always scrimmed dark). */
    val onMedia: Color get() = VellutoPalette.OnMedia

    /** Secondary text on photos and illustrated covers. */
    val onMediaMuted: Color get() = VellutoPalette.OnMediaMuted
}

/** The non-accent half of a theme: fixed colours and the alphas applied to the accent. */
private data class ThemeBase(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val surface2: Color,
    val text: Color,
    val text2: Color,
    val glass: Color,
    val navGlass: Color,
    val hi: Color,
    val line: Color,
    val shadow: Color,
    val shadowSoft: Color,
    val track: Color,
    val ringGap: Color,
    val heroBg: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
    val glassFill: Color,
    val glassStroke: Color,
    val dropShadow: Color,
    val toward: Color,
    val accentSoftAlpha: Float,
    val glowStrongAlpha: Float,
    val glowSoftAlpha: Float,
    val statusSoftAlpha: Float,
)

private val DarkBase =
    ThemeBase(
        dark = true,
        bg = VellutoPalette.DarkBg,
        surface = VellutoPalette.DarkSurface,
        surfaceHi = VellutoPalette.DarkSurfaceHi,
        surface2 = VellutoPalette.DarkSurface2,
        text = VellutoPalette.DarkText,
        text2 = VellutoPalette.DarkText2,
        glass = VellutoPalette.White.copy(alpha = 0.055f),
        navGlass = VellutoPalette.DarkNavGlass,
        hi = VellutoPalette.White.copy(alpha = 0.12f),
        line = VellutoPalette.White.copy(alpha = 0.08f),
        shadow = VellutoPalette.Black.copy(alpha = 0.5f),
        shadowSoft = VellutoPalette.Black.copy(alpha = 0.3f),
        track = VellutoPalette.White.copy(alpha = 0.14f),
        ringGap = VellutoPalette.DarkRingGap,
        heroBg = VellutoPalette.DarkHeroBg,
        ok = VellutoPalette.DarkOk,
        warn = VellutoPalette.DarkWarn,
        err = VellutoPalette.DarkErr,
        glassFill = VellutoPalette.White.copy(alpha = 0.08f),
        glassStroke = VellutoPalette.White.copy(alpha = 0.55f),
        dropShadow = VellutoPalette.Black.copy(alpha = 0.5f),
        toward = VellutoPalette.White,
        accentSoftAlpha = 0.16f,
        glowStrongAlpha = 0.45f,
        glowSoftAlpha = 0.13f,
        statusSoftAlpha = 0.14f,
    )

private val LightBase =
    ThemeBase(
        dark = false,
        bg = VellutoPalette.LightBg,
        surface = VellutoPalette.LightSurface,
        surfaceHi = VellutoPalette.LightSurface,
        surface2 = VellutoPalette.LightSurface2,
        text = VellutoPalette.LightText,
        text2 = VellutoPalette.LightText2,
        glass = VellutoPalette.White.copy(alpha = 0.72f),
        navGlass = VellutoPalette.White.copy(alpha = 0.74f),
        hi = VellutoPalette.White.copy(alpha = 0.95f),
        line = Color(0x17141210),
        shadow = Color(0x1F3C2814),
        shadowSoft = Color(0x0F3C2814),
        track = Color(0x24141210),
        ringGap = VellutoPalette.LightSurface,
        heroBg = VellutoPalette.LightHeroBg,
        ok = VellutoPalette.LightOk,
        warn = VellutoPalette.LightWarn,
        err = VellutoPalette.LightErr,
        glassFill = VellutoPalette.White.copy(alpha = 0.45f),
        glassStroke = Color(0x7378561E),
        dropShadow = Color(0x405A3C14),
        toward = VellutoPalette.Black,
        accentSoftAlpha = 0.2f,
        glowStrongAlpha = 0.34f,
        glowSoftAlpha = 0.12f,
        statusSoftAlpha = 0.1f,
    )

private const val ALPHA_ACCENT_EDGE = 0.45f
private const val ALPHA_STATUS_EDGE = 0.4f
private const val ALPHA_GLOW_HERO = 0.5f
private const val ALPHA_GLOW_HERO_SOFT = 0.14f
private const val RAMP_G1 = 0.7f
private const val RAMP_G2 = 0.22f
private const val RAMP_G4 = 0.38f
private const val RAMP_G5 = 0.66f

/**
 * Builds the Velluto colour set for [dark] / light from the single [accent]. Mirrors the `theme()`
 * function of the approved mockups one token at a time.
 */
fun vellutoColors(
    dark: Boolean,
    accent: Color = VellutoAccent,
): VellutoColors {
    val b = if (dark) DarkBase else LightBase
    val softBg = b.surface.mixWith(accent, b.accentSoftAlpha)
    return VellutoColors(
        isDark = b.dark,
        bg = b.bg,
        surface = b.surface,
        surfaceHi = b.surfaceHi,
        surface2 = b.surface2,
        text = b.text,
        text2 = b.text2,
        glass = b.glass,
        navGlass = b.navGlass,
        hi = b.hi,
        line = b.line,
        shadow = b.shadow,
        shadowSoft = b.shadowSoft,
        track = b.track,
        ringGap = b.ringGap,
        accent = accent,
        onAccent = accent.bestContentColor(dark = VellutoPalette.Ink, light = VellutoPalette.White),
        accentSoft = accent.copy(alpha = b.accentSoftAlpha),
        accentEdge = accent.copy(alpha = ALPHA_ACCENT_EDGE),
        accentText = accent.readableOn(b.bg, b.toward),
        accentTextSurface = accent.readableOn(b.surface, b.toward),
        accentOnSoft = accent.readableOn(softBg, b.toward),
        glowStrong = accent.copy(alpha = b.glowStrongAlpha),
        glowSoft = accent.copy(alpha = b.glowSoftAlpha),
        ok = b.ok,
        warn = b.warn,
        err = b.err,
        okSoft = b.ok.copy(alpha = b.statusSoftAlpha),
        warnSoft = b.warn.copy(alpha = b.statusSoftAlpha),
        errSoft = b.err.copy(alpha = b.statusSoftAlpha),
        warnEdge = b.warn.copy(alpha = ALPHA_STATUS_EDGE),
        errEdge = b.err.copy(alpha = ALPHA_STATUS_EDGE),
        heroBg = b.heroBg,
        accentOnHero = accent.readableOn(b.heroBg, VellutoPalette.White),
        glowHero = accent.copy(alpha = ALPHA_GLOW_HERO),
        glowHeroSoft = accent.copy(alpha = ALPHA_GLOW_HERO_SOFT),
        glassFill = b.glassFill,
        glassStroke = b.glassStroke,
        dropShadow = b.dropShadow,
        g1 = accent.mixWith(VellutoPalette.White, RAMP_G1),
        g2 = accent.mixWith(VellutoPalette.White, RAMP_G2),
        g3 = accent,
        g4 = accent.mixWith(VellutoPalette.Black, RAMP_G4),
        g5 = accent.mixWith(VellutoPalette.Black, RAMP_G5),
    )
}

/** [color] flattened over [base], for places that need an opaque equivalent of a translucent token. */
internal fun opaque(
    color: Color,
    base: Color,
): Color = if (color.alpha < 1f) color.compositeOver(base) else color

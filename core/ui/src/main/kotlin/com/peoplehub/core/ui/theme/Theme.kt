package com.peoplehub.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LocalVellutoType = staticCompositionLocalOf { VellutoType() }
private val LocalVellutoSpacing = staticCompositionLocalOf { VellutoSpacing() }
private val LocalVellutoRadii = staticCompositionLocalOf { VellutoRadii() }

/**
 * Maps the Velluto tokens onto the Material 3 roles, so stock Material components (text fields,
 * dialogs, sheets, switches, menus, date pickers) inherit the identity without per-call styling.
 *
 * `primary` is the accent **as text** (AA on the background): in dark it is the accent itself, in
 * light a deepened gold. Velluto components that need the accent as a *fill* read
 * `PeopleHubTheme.colors.accent` instead.
 */
private fun VellutoColors.toColorScheme(): ColorScheme {
    val onPrimary = accentText.bestContentColor(dark = VellutoPalette.Ink, light = VellutoPalette.White)
    val onError = err.bestContentColor(dark = VellutoPalette.Ink, light = VellutoPalette.White)
    val base =
        if (isDark) {
            darkColorScheme(
                onTertiary = bg,
                surfaceBright = Color(0xFF2A2622),
                surfaceDim = bg,
                surfaceContainerLowest = bg,
                surfaceContainerLow = Color(0xFF12100E),
                surfaceContainer = surface,
                surfaceContainerHigh = Color(0xFF1C1916),
                surfaceContainerHighest = surfaceHi,
                outline = Color(0xFF6E675F),
            )
        } else {
            lightColorScheme(
                onTertiary = VellutoPalette.White,
                surfaceBright = surface,
                surfaceDim = Color(0xFFEDE9E2),
                surfaceContainerLowest = surface,
                surfaceContainerLow = surface,
                surfaceContainer = surface,
                surfaceContainerHigh = surface,
                surfaceContainerHighest = surface2,
                outline = Color(0xFF8C847A),
            )
        }
    val primaryContainer = opaque(accentSoft, surface)
    return base.copy(
        primary = accentText,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = accentOnSoft,
        inversePrimary = if (isDark) accentText else accent,
        secondary = accentText,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = accentOnSoft,
        tertiary = text2,
        tertiaryContainer = surface2,
        onTertiaryContainer = text,
        error = err,
        onError = onError,
        errorContainer = opaque(errSoft, surface),
        onErrorContainer = err,
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = surface2,
        onSurfaceVariant = text2,
        surfaceTint = bg,
        inverseSurface = text,
        inverseOnSurface = bg,
        outlineVariant = opaque(line, bg),
        scrim = VellutoPalette.Black,
    )
}

/**
 * The PeopleHub "Velluto" theme: velvet-dark or warm-paper light, one gold accent, Instrument Serif
 * over Geist, glass and soft glows.
 *
 * The whole palette derives from [accent] ([VellutoAccent] by default). Dynamic Color (Monet) is
 * supported on Android 12+ but stays **off by default**; when on, only the Material roles follow the
 * wallpaper, the Velluto tokens keep the brand accent.
 *
 * @param darkTheme whether to use the dark colour scheme (defaults to the system setting).
 * @param dynamicColor whether to derive the Material roles from the wallpaper on Android 12+.
 * @param accent the single brand accent every other accent token is computed from.
 */
@Composable
fun PeopleHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accent: Color = VellutoAccent,
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme, accent) { vellutoColors(darkTheme, accent) }
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        if (supportsDynamic) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            remember(colors) { colors.toColorScheme() }
        }
    val reduced = rememberReducedMotion()
    val motion = remember(reduced) { VellutoMotion(reduced = reduced) }

    CompositionLocalProvider(
        LocalVellutoColors provides colors,
        LocalVellutoMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PeopleHubTypography,
            shapes = PeopleHubShapes,
            content = content,
        )
    }
}

/** Accessors for the Velluto tokens that have no Material 3 role. */
object PeopleHubTheme {
    /** The semantic Velluto colours of the active theme. */
    val colors: VellutoColors
        @Composable @ReadOnlyComposable
        get() = LocalVellutoColors.current

    /** Hero/KPI text styles outside the Material scale. */
    val type: VellutoType
        @Composable @ReadOnlyComposable
        get() = LocalVellutoType.current

    /** Spacing scale and fixed layout measures. */
    val spacing: VellutoSpacing
        @Composable @ReadOnlyComposable
        get() = LocalVellutoSpacing.current

    /** Corner radii. */
    val radii: VellutoRadii
        @Composable @ReadOnlyComposable
        get() = LocalVellutoRadii.current

    /** Durations, easing and the reduced-motion flag. */
    val motion: VellutoMotion
        @Composable @ReadOnlyComposable
        get() = LocalVellutoMotion.current
}

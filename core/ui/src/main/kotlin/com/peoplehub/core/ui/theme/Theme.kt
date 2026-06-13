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
import androidx.compose.ui.platform.LocalContext

private val DarkColors: ColorScheme = darkColorScheme(
    primary = MidnightGold.DarkPrimary,
    onPrimary = MidnightGold.DarkOnPrimary,
    primaryContainer = MidnightGold.DarkPrimaryContainer,
    onPrimaryContainer = MidnightGold.DarkOnPrimaryContainer,
    inversePrimary = MidnightGold.DarkInversePrimary,
    secondary = MidnightGold.DarkSecondary,
    onSecondary = MidnightGold.DarkOnSecondary,
    secondaryContainer = MidnightGold.DarkSecondaryContainer,
    onSecondaryContainer = MidnightGold.DarkOnSecondaryContainer,
    tertiary = MidnightGold.DarkTertiary,
    onTertiary = MidnightGold.DarkOnTertiary,
    tertiaryContainer = MidnightGold.DarkTertiaryContainer,
    onTertiaryContainer = MidnightGold.DarkOnTertiaryContainer,
    error = MidnightGold.DarkError,
    onError = MidnightGold.DarkOnError,
    errorContainer = MidnightGold.DarkErrorContainer,
    onErrorContainer = MidnightGold.DarkOnErrorContainer,
    background = MidnightGold.DarkBackground,
    onBackground = MidnightGold.DarkOnBackground,
    surface = MidnightGold.DarkSurface,
    onSurface = MidnightGold.DarkOnSurface,
    surfaceVariant = MidnightGold.DarkSurfaceVariant,
    onSurfaceVariant = MidnightGold.DarkOnSurfaceVariant,
    surfaceDim = MidnightGold.DarkSurfaceDim,
    surfaceBright = MidnightGold.DarkSurfaceBright,
    surfaceContainerLowest = MidnightGold.DarkSurfaceContainerLowest,
    surfaceContainerLow = MidnightGold.DarkSurfaceContainerLow,
    surfaceContainer = MidnightGold.DarkSurfaceContainer,
    surfaceContainerHigh = MidnightGold.DarkSurfaceContainerHigh,
    surfaceContainerHighest = MidnightGold.DarkSurfaceContainerHighest,
    outline = MidnightGold.DarkOutline,
    outlineVariant = MidnightGold.DarkOutlineVariant,
    inverseSurface = MidnightGold.DarkInverseSurface,
    inverseOnSurface = MidnightGold.DarkInverseOnSurface,
    surfaceTint = MidnightGold.DarkSurfaceTint,
    scrim = MidnightGold.Scrim,
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = MidnightGold.LightPrimary,
    onPrimary = MidnightGold.LightOnPrimary,
    primaryContainer = MidnightGold.LightPrimaryContainer,
    onPrimaryContainer = MidnightGold.LightOnPrimaryContainer,
    inversePrimary = MidnightGold.LightInversePrimary,
    secondary = MidnightGold.LightSecondary,
    onSecondary = MidnightGold.LightOnSecondary,
    secondaryContainer = MidnightGold.LightSecondaryContainer,
    onSecondaryContainer = MidnightGold.LightOnSecondaryContainer,
    tertiary = MidnightGold.LightTertiary,
    onTertiary = MidnightGold.LightOnTertiary,
    tertiaryContainer = MidnightGold.LightTertiaryContainer,
    onTertiaryContainer = MidnightGold.LightOnTertiaryContainer,
    error = MidnightGold.LightError,
    onError = MidnightGold.LightOnError,
    errorContainer = MidnightGold.LightErrorContainer,
    onErrorContainer = MidnightGold.LightOnErrorContainer,
    background = MidnightGold.LightBackground,
    onBackground = MidnightGold.LightOnBackground,
    surface = MidnightGold.LightSurface,
    onSurface = MidnightGold.LightOnSurface,
    surfaceVariant = MidnightGold.LightSurfaceVariant,
    onSurfaceVariant = MidnightGold.LightOnSurfaceVariant,
    surfaceDim = MidnightGold.LightSurfaceDim,
    surfaceBright = MidnightGold.LightSurfaceBright,
    surfaceContainerLowest = MidnightGold.LightSurfaceContainerLowest,
    surfaceContainerLow = MidnightGold.LightSurfaceContainerLow,
    surfaceContainer = MidnightGold.LightSurfaceContainer,
    surfaceContainerHigh = MidnightGold.LightSurfaceContainerHigh,
    surfaceContainerHighest = MidnightGold.LightSurfaceContainerHighest,
    outline = MidnightGold.LightOutline,
    outlineVariant = MidnightGold.LightOutlineVariant,
    inverseSurface = MidnightGold.LightInverseSurface,
    inverseOnSurface = MidnightGold.LightInverseOnSurface,
    surfaceTint = MidnightGold.LightSurfaceTint,
    scrim = MidnightGold.Scrim,
)

private val DarkExtendedColors = ExtendedColors(
    fresh = StatusColors(MidnightGold.FreshContainerDark, MidnightGold.OnFreshContainerDark),
)

private val LightExtendedColors = ExtendedColors(
    fresh = StatusColors(MidnightGold.FreshContainerLight, MidnightGold.OnFreshContainerLight),
)

/**
 * The PeopleHub theme.
 *
 * Dark is the brand-defining default. Dynamic Color (Monet) is supported on Android 12+ but stays
 * **off by default** so the signature champagne-gold-on-onyx identity is preserved; pass
 * [dynamicColor] `= true` to opt into wallpaper-derived colours.
 *
 * @param darkTheme whether to use the dark colour scheme (defaults to the system setting).
 * @param dynamicColor whether to derive colours from the wallpaper on Android 12+.
 */
@Composable
fun PeopleHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        supportsDynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PeopleHubTypography,
            shapes = PeopleHubShapes,
            content = content,
        )
    }
}

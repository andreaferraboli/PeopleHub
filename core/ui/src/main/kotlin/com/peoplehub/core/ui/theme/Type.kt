package com.peoplehub.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * PeopleHub typography. The Neo-Luxury design relies on a hard contrast between an editorial serif
 * for display/headline roles (Bodoni Moda) and a clean sans for body/label roles (Manrope).
 *
 * Because the app is fully offline and ships no downloadable fonts, the families resolve to the
 * platform serif / sans-serif. To adopt the exact brand faces, drop `bodoni_moda` and `manrope`
 * into `res/font` and point [Display] / [Body] at the new [FontFamily]s — nothing else changes.
 */
internal object PeopleHubFonts {
    val Display: FontFamily = FontFamily.Serif
    val Body: FontFamily = FontFamily.SansSerif
}

/** A high-impact serif label style used for the small uppercase "watchmaking" labels. */
val LabelCaps: TextStyle =
    TextStyle(
        fontFamily = PeopleHubFonts.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.1.em,
    )

internal val PeopleHubTypography: Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = PeopleHubFonts.Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.02).em,
            ),
        displayMedium =
            TextStyle(
                fontFamily = PeopleHubFonts.Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = (-0.01).em,
            ),
        displaySmall =
            TextStyle(
                fontFamily = PeopleHubFonts.Display,
                fontWeight = FontWeight.Medium,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = PeopleHubFonts.Display,
                fontWeight = FontWeight.Medium,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = PeopleHubFonts.Display,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = PeopleHubFonts.Display,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.05.em,
            ),
        labelMedium =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.08.em,
            ),
        labelSmall =
            TextStyle(
                fontFamily = PeopleHubFonts.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.1.em,
                textAlign = TextAlign.Start,
            ),
    )

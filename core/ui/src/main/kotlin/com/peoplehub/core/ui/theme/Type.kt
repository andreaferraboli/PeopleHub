package com.peoplehub.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.peoplehub.core.ui.R

/**
 * The two Velluto families, bundled as font resources (no downloadable fonts: the app is offline).
 *
 * - [Display]: Instrument Serif (regular + italic) for hero titles, section and card headings.
 * - [Body]: Geist (300–700) for everything else, including the big tabular numerals.
 *
 * Both are SIL OFL 1.1; the licence texts ship in `assets/licenses/`.
 */
object PeopleHubFonts {
    val Display: FontFamily =
        FontFamily(
            Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
            Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
        )

    val Body: FontFamily =
        FontFamily(
            Font(R.font.geist_light, FontWeight.Light),
            Font(R.font.geist_regular, FontWeight.Normal),
            Font(R.font.geist_medium, FontWeight.Medium),
            Font(R.font.geist_semibold, FontWeight.SemiBold),
            Font(R.font.geist_bold, FontWeight.Bold),
        )
}

/** OpenType feature for fixed-width digits, so counters and dates do not jitter. */
private const val TABULAR = "tnum"

/**
 * The small uppercase "eyebrow" label (12sp, wide tracking) used above hero titles and as caps
 * labels. Call sites uppercase the text themselves (CapsLabel does).
 */
val LabelCaps: TextStyle =
    TextStyle(
        fontFamily = PeopleHubFonts.Body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.em,
    )

/**
 * Velluto text styles that have no Material 3 role: the italic accent half of hero titles and the
 * big Geist numerals of KPIs and countdowns. Read them with `PeopleHubTheme.type`.
 */
@Immutable
data class VellutoType(
    /** Eyebrow label above hero titles (uppercase at the call site). */
    val eyebrow: TextStyle = LabelCaps,
    /** Small caps label inside tiles (month of a date tile, "TRA", "GIORNI"). */
    val tileCaps: TextStyle =
        TextStyle(
            fontFamily = PeopleHubFonts.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.13.em,
        ),
    /** Serif italic hero subtitle ("L'architettura dei tuoi momenti."). */
    val heroSubtitle: TextStyle =
        TextStyle(
            fontFamily = PeopleHubFonts.Display,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            fontSize = 21.sp,
            lineHeight = 25.sp,
        ),
    /** 92sp hero KPI ("5 need attention"). */
    val kpiHero: TextStyle = kpi(92, 76, -0.043),
    /** 58–62sp countdown on event covers. */
    val kpiLarge: TextStyle = kpi(60, 54, -0.04),
    /** 52sp countdown on archived covers. */
    val kpiCover: TextStyle = kpi(52, 47, -0.038),
    /** 40sp secondary KPI and reminder countdown. */
    val kpiMedium: TextStyle = kpi(40, 40, -0.037),
    /** 30sp stat in the Vault summary and stepper value. */
    val kpiSmall: TextStyle = kpi(30, 30, -0.033),
    /** 22–24sp day number inside a date tile. */
    val kpiTile: TextStyle = kpi(22, 22, 0.0),
)

private fun kpi(
    size: Int,
    lineHeight: Int,
    tracking: Double,
): TextStyle =
    TextStyle(
        fontFamily = PeopleHubFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = TABULAR,
    )

private fun serif(
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0,
): TextStyle =
    TextStyle(
        fontFamily = PeopleHubFonts.Display,
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
    )

private fun sans(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
): TextStyle =
    TextStyle(
        fontFamily = PeopleHubFonts.Body,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
    )

/**
 * The Material 3 scale mapped onto Velluto: display and headline roles are Instrument Serif (one
 * weight, contrast comes from size and the italic accent), title/body/label roles are Geist.
 */
internal val PeopleHubTypography: Typography =
    Typography(
        // Hero titles ("La Cerchia", "Bentornato, Andrea.")
        displayLarge = serif(62, 58, -0.022),
        // Hero card title ("Porta i fiori")
        displayMedium = serif(46, 44, -0.011),
        // Big section title ("Ricorrenze")
        displaySmall = serif(34, 36),
        // Cover title ("Weekend a Lisbona")
        headlineLarge = serif(32, 34),
        // Section header ("Uscite in ritardo")
        headlineMedium = serif(30, 34),
        // Card heading ("Backup e ripristino", person name on a hero row)
        headlineSmall = serif(26, 30),
        // Top app bar title and dialog titles
        titleLarge = serif(26, 30),
        // Row primary text (person name)
        titleMedium = sans(17, 22, FontWeight.SemiBold),
        // Button and emphasised small text
        titleSmall = sans(15, 20, FontWeight.SemiBold),
        // Long reading text
        bodyLarge = sans(16, 23, FontWeight.Normal),
        // Paragraphs and supporting copy (15 / 1.45)
        bodyMedium = sans(15, 22, FontWeight.Normal),
        // Secondary lines, captions
        bodySmall = sans(13, 18, FontWeight.Normal),
        // Chips, buttons
        labelLarge = sans(14, 20, FontWeight.SemiBold),
        // Badges
        labelMedium = sans(12, 16, FontWeight.SemiBold),
        // Tiny caps (date tile month)
        labelSmall = sans(11, 14, FontWeight.SemiBold, 0.1),
    )

package com.peoplehub.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Velluto corner radii, read from the mockups. Everything interactive is a pill; cards are soft and
 * large; small tiles sit in between.
 */
@Immutable
data class VellutoRadii(
    /** Brand mark tile. */
    val mark: Dp = 11.dp,
    /** Icon tile inside a settings card, chips inside tiles. */
    val tile: Dp = 14.dp,
    /** Date tile, inner list row. */
    val dateTile: Dp = 16.dp,
    /** Inner grouped rows, secondary buttons in a grid. */
    val inner: Dp = 20.dp,
    /** Small cards, outings history. */
    val cardSmall: Dp = 24.dp,
    /** List cards (people, reminders). */
    val card: Dp = 26.dp,
    /** Section cards (settings, milestones). */
    val cardLarge: Dp = 28.dp,
    /** Hero cards and event covers. */
    val hero: Dp = 30.dp,
)

/** Shared shape instances for the radii above. */
object VellutoShapes {
    private val r = VellutoRadii()
    val Pill = CircleShape
    val Mark = RoundedCornerShape(r.mark)
    val Tile = RoundedCornerShape(r.tile)
    val DateTile = RoundedCornerShape(r.dateTile)
    val Inner = RoundedCornerShape(r.inner)
    val CardSmall = RoundedCornerShape(r.cardSmall)
    val Card = RoundedCornerShape(r.card)
    val CardLarge = RoundedCornerShape(r.cardLarge)
    val Hero = RoundedCornerShape(r.hero)
}

/**
 * Material 3 shape scale mapped onto Velluto: text fields and menus get the inner radius, dialogs and
 * sheets the hero radius, buttons stay fully rounded (Material default).
 */
internal val PeopleHubShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(30.dp),
    )

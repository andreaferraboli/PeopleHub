package com.peoplehub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubTheme

/**
 * A rounded rectangle with a circular bite out of its bottom edge (the Velluto "card con incavo"):
 * the notch is centred on the bottom edge, horizontally at the middle or [notchCenterFromEnd] from
 * the end edge, so a round button can sit inside it.
 */
@Immutable
class NotchedShape(
    private val cornerRadius: Dp,
    private val notchRadius: Dp,
    private val notchCenterFromEnd: Dp? = null,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val corner = with(density) { cornerRadius.toPx() }
        val radius = with(density) { notchRadius.toPx() }
        val fromEnd = notchCenterFromEnd?.let { with(density) { it.toPx() } }
        val centerX =
            when {
                fromEnd == null -> size.width / 2f
                layoutDirection == LayoutDirection.Ltr -> size.width - fromEnd
                else -> fromEnd
            }
        val card = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner))) }
        val notch = Path().apply { addOval(Rect(center = Offset(centerX, size.height), radius = radius)) }
        return Outline.Generic(Path.combine(PathOperation.Difference, card, notch))
    }

    override fun equals(other: Any?): Boolean =
        other is NotchedShape &&
            other.cornerRadius == cornerRadius &&
            other.notchRadius == notchRadius &&
            other.notchCenterFromEnd == notchCenterFromEnd

    override fun hashCode(): Int = (cornerRadius.hashCode() * 31 + notchRadius.hashCode()) * 31 + notchCenterFromEnd.hashCode()
}

/**
 * A hero card with a notch and its round action sitting inside it (Riflessione "Vitalità della
 * rete", Promemoria "in scadenza oggi"). The card stops [buttonSize]/2 above the bottom of the layout;
 * the [button] is centred on the card's bottom edge, with a [gap] ring of background around it.
 *
 * @param buttonCenterFromEnd `null` centres the button; otherwise its centre is this far from the end.
 * @param background the card fill (a vertical surface gradient by default).
 * @param border hairline colour; pass the accent edge for the dark hero card.
 */
@Composable
fun NotchedCard(
    modifier: Modifier = Modifier,
    buttonSize: Dp = 64.dp,
    gap: Dp = 7.dp,
    buttonCenterFromEnd: Dp? = null,
    cornerRadius: Dp = PeopleHubTheme.radii.hero,
    background: Brush? = null,
    border: Color = PeopleHubTheme.colors.line,
    contentColor: Color = PeopleHubTheme.colors.text,
    shadow: ShadowSpec = VellutoElevation.Hero,
    contentPadding: Dp = 20.dp,
    decoration: @Composable BoxScope.() -> Unit = {},
    button: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PeopleHubTheme.colors
    val shape =
        remember(cornerRadius, buttonSize, gap, buttonCenterFromEnd) {
            NotchedShape(cornerRadius, buttonSize / 2 + gap, buttonCenterFromEnd)
        }
    val fill = background ?: Brush.linearGradient(listOf(c.surfaceHi, c.surface, c.surface))
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = buttonSize / 2)
                    .softShadow(c.shadow, shape, shadow)
                    .background(fill, shape)
                    .lightEdge(shape, highlight = c.hi, border = border)
                    .clip(shape),
        ) {
            decoration()
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = contentPadding,
                                end = contentPadding,
                                top = contentPadding,
                                bottom =
                                    buttonSize / 2 + gap + 8.dp,
                            ),
                    content = content,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .align(if (buttonCenterFromEnd == null) Alignment.BottomCenter else Alignment.BottomEnd)
                    .padding(end = buttonCenterFromEnd?.let { (it - buttonSize / 2).coerceAtLeast(0.dp) } ?: 0.dp),
        ) {
            button()
        }
    }
}

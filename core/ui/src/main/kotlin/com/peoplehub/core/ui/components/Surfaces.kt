package com.peoplehub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.cardSurface
import com.peoplehub.core.ui.modifier.glassSurface
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.LabelCaps
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes

/**
 * A Velluto glass panel: translucent fill, 1dp hairline, top light edge and a soft shadow. The
 * workhorse floating surface (list cards, settings sections, search, chips rows).
 *
 * @param shadow the drop shadow, or `null` for a flat panel (e.g. nested inside another card).
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = VellutoShapes.Card,
    shadow: ShadowSpec? = VellutoElevation.Card,
    content: @Composable () -> Unit,
) {
    val colors = PeopleHubTheme.colors
    Box(
        modifier =
            modifier
                .glassSurface(colors, shape, shadow)
                .clip(shape),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.text) { content() }
    }
}

/**
 * An opaque Velluto card ([com.peoplehub.core.ui.theme.VellutoColors.surface]) with hairline and
 * light edge: used where the mockups draw solid cards (milestones, outings history, reminder cards).
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = VellutoShapes.Card,
    shadow: ShadowSpec? = null,
    borderColor: Color = PeopleHubTheme.colors.line,
    content: @Composable () -> Unit,
) {
    val colors = PeopleHubTheme.colors
    Box(
        modifier =
            modifier
                .cardSurface(colors, shape, shadow, border = borderColor)
                .clip(shape),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.text) { content() }
    }
}

/**
 * The Velluto divider: a 1dp accent line fading to transparent at both ends, optionally with the
 * glowing accent diamond in the middle that separates major sections on the home screen.
 */
@Composable
fun GoldDivider(
    modifier: Modifier = Modifier,
    diamond: Boolean = false,
) {
    val colors = PeopleHubTheme.colors
    val accent = colors.accent
    Box(modifier = modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to accent.copy(alpha = 0f),
                            0.5f to accent.copy(alpha = 0.9f),
                            1f to accent.copy(alpha = 0f),
                        ),
                    ),
        )
        if (diamond) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .rotate(45f)
                        .softShadow(colors.glowStrong, RoundedCornerShape(0.dp), ShadowSpec(14.dp))
                        .background(accent),
            )
        }
    }
}

/**
 * The small uppercase eyebrow label ("24 PERSONE", "IMPOSTAZIONI"): Geist 12sp with wide tracking,
 * in secondary text by default. Pass an accent colour for emphasis.
 */
@Composable
fun CapsLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PeopleHubTheme.colors.text2,
) {
    Text(
        text = text.uppercase(),
        style = LabelCaps,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A section header: Instrument Serif title (30sp) with an optional muted [count] after it, an
 * optional accent fade line filling the rest of the row ([fadeLine]), and an optional trailing text
 * action ("Vedi tutti") with a 48dp touch target.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    count: String? = null,
    fadeLine: Boolean = false,
) {
    val colors = PeopleHubTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = !fadeLine && count == null).semantics { heading() },
        )
        if (count != null) {
            Text(text = count, style = MaterialTheme.typography.bodySmall, color = colors.text2)
        }
        if (fadeLine) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                0f to colors.accent.copy(alpha = 0.8f),
                                1f to colors.accent.copy(alpha = 0f),
                            ),
                        ),
            )
        } else if (count != null) {
            Box(Modifier.weight(1f).width(0.dp))
        }
        if (actionLabel != null && onActionClick != null) {
            TextAction(label = actionLabel, onClick = onActionClick)
        }
    }
}

/**
 * An inline accent text action ("Vedi tutti", "Apri la guida") with a full 48dp touch target and an
 * optional trailing icon slot.
 */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PeopleHubTheme.colors.accentText,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = color)
        if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides color) { trailing() }
        }
    }
}

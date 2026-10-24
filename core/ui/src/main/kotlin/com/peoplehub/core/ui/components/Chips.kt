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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.glassSurface
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.LabelCaps
import com.peoplehub.core.ui.theme.PeopleHubTheme

/** How a selected [TagChip] is painted. */
enum class ChipSelectedStyle {
    /** Solid accent with dark text and a soft glow (tag filters in Cerchia and Promemoria). */
    Solid,

    /** Translucent accent with accent text and hairline (categories in Eventi, toggles in Vault). */
    Soft,
}

/**
 * The Velluto filter pill: 48dp tall, glass when idle, accent when selected. Optional leading
 * [icon] or coloured [dot], and a muted trailing [count].
 */
@Composable
fun TagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
    icon: ImageVector? = null,
    dot: Color? = null,
    selectedStyle: ChipSelectedStyle = ChipSelectedStyle.Solid,
) {
    val c = PeopleHubTheme.colors
    val solid = selected && selectedStyle == ChipSelectedStyle.Solid
    val soft = selected && selectedStyle == ChipSelectedStyle.Soft
    val content =
        when {
            solid -> c.onAccent
            soft -> c.accentOnSoft
            else -> c.text
        }
    val countColor = if (solid) c.onAccent else c.text2
    Row(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .then(
                    when {
                        solid -> Modifier.softShadow(c.glowSoft, CircleShape, ShadowSpec(22.dp, 8.dp)).background(c.accent, CircleShape)
                        soft -> Modifier.background(c.accentSoft, CircleShape).lightEdge(CircleShape, Color.Transparent, c.accentEdge)
                        else -> Modifier.glassSurface(c, CircleShape)
                    },
                ).clip(CircleShape)
                .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = content,
            maxLines = 1,
        )
        if (count != null) {
            Text(text = count, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = countColor)
        }
    }
}

/**
 * A toggle pill with a check mark when on ("Il giorno stesso", "7 giorni prima"): soft accent when
 * on, hairline when off. [checkIcon] is drawn only when [checked].
 */
@Composable
fun ToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    Row(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .background(if (checked) c.accentSoft else Color.Transparent, CircleShape)
                .lightEdge(CircleShape, Color.Transparent, if (checked) c.accentEdge else c.line)
                .clip(CircleShape)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (checked) Icon(checkIcon, contentDescription = null, tint = c.accentOnSoft, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium),
            color = if (checked) c.accentOnSoft else c.text2,
        )
    }
}

/**
 * A read-only category pill (an event category, a reminder category). Soft tint of [tint] by
 * default; with [onMedia] it becomes the dark translucent pill used on photo and scene covers, with
 * an optional coloured [dot].
 */
@Composable
fun CategoryChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color = PeopleHubTheme.colors.text2,
    onMedia: Boolean = false,
    dot: Color? = null,
    icon: ImageVector? = null,
) {
    val c = PeopleHubTheme.colors
    val textColor = if (onMedia) c.onMedia else tint
    Row(
        modifier =
            modifier
                .height(if (onMedia) 30.dp else 28.dp)
                .then(
                    if (onMedia) {
                        Modifier.background(MEDIA_CHIP_FILL, CircleShape).lightEdge(CircleShape, Color.Transparent, MEDIA_CHIP_EDGE)
                    } else {
                        Modifier.background(c.surface2, CircleShape).lightEdge(CircleShape, Color.Transparent, c.line)
                    },
                ).clip(CircleShape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(7.dp).background(dot, CircleShape))
        if (icon != null) Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(13.dp))
        Text(
            text = if (onMedia) label.uppercase() else label,
            style =
                if (onMedia) {
                    LabelCaps.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
                    )
                } else {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                },
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dark translucent fill of chips over photos/scenes (`rgba(10,8,6,.45)`). */
internal val MEDIA_CHIP_FILL = Color(0x730A0806)

/** Hairline of chips over photos/scenes (`rgba(255,255,255,.16)`). */
internal val MEDIA_CHIP_EDGE = Color(0x29FFFFFF)

/**
 * A status pill: soft [container] fill, [content] text and a leading dot (or [icon]): "Visto 42
 * giorni fa", "In scadenza", "Aggiornata".
 */
@Composable
fun StatusPill(
    label: String,
    content: Color,
    container: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dot: Boolean = icon == null,
    border: Color = Color.Transparent,
) {
    Row(
        modifier =
            modifier
                .height(28.dp)
                .background(container, CircleShape)
                .lightEdge(CircleShape, Color.Transparent, border)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
        } else if (dot) {
            Box(Modifier.size(6.dp).background(content, CircleShape))
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/**
 * The Velluto segmented control: a glass track with pills, the selected one filled with the text
 * colour (inverted), as in the Eventi period switch.
 */
@Composable
fun SegmentedPill(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .glassSurface(c, CircleShape)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(
                            if (on) {
                                Modifier
                                    .softShadow(
                                        c.shadow,
                                        CircleShape,
                                        ShadowSpec(16.dp, 6.dp),
                                    ).background(c.text, CircleShape)
                            } else {
                                Modifier
                            },
                        ).clip(CircleShape)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) c.bg else c.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A legend entry: coloured dot + label (status legends, segment bars). */
@Composable
fun LegendDot(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
            color = PeopleHubTheme.colors.text2,
        )
    }
}

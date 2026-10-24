package com.peoplehub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes

/** The 40dp rounded tile holding a section icon on a soft accent fill. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = PeopleHubTheme.colors.accentSoft,
    content: Color = PeopleHubTheme.colors.accentOnSoft,
) {
    Box(
        modifier = modifier.size(40.dp).background(container, VellutoShapes.Tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

/**
 * A Vault section ("Backup e ripristino"): glass card, 28dp corners, icon tile + serif title, an
 * optional muted [description], then the section's own [content] with 14dp rhythm.
 */
@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PeopleHubTheme.colors
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardLarge, shadow = VellutoElevation.Raised) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(icon)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.text,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = c.text2,
                )
            }
            content()
        }
    }
}

/**
 * A navigation row inside a grouped card ("Lingua · Italiano ›"): 60dp tall, muted leading icon,
 * label, muted [value] and a chevron.
 */
@Composable
fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    trailingIcon: ImageVector? = null,
    supporting: String? = null,
) {
    val c = PeopleHubTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.text2, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = c.text,
            )
            if (supporting != null) {
                Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = c.text2)
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                color = c.text2,
                maxLines = 1,
            )
        }
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = c.text2, modifier = Modifier.size(16.dp))
    }
}

/**
 * A labelled switch row ("Usa allarmi esatti"). The whole row toggles; the switch uses the accent
 * track with the on-accent thumb.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val c = PeopleHubTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = c.text)
            if (supporting !=
                null
            ) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                    color = c.text2,
                )
            }
        }
        VellutoSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A Material switch in Velluto colours: accent track and dark thumb when on, hairline when off. */
@Composable
fun VellutoSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = PeopleHubTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.then(if (checked) Modifier.softShadow(c.glowSoft, CircleShape, ShadowSpec(16.dp, 6.dp)) else Modifier),
        colors =
            SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.onAccent,
                checkedIconColor = c.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = c.surface2,
                uncheckedThumbColor = c.text2,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
    )
}

/**
 * A "− value +" stepper row ("Avviso dopo (giorni) − 14 +"): status dot, label, a hairline minus
 * button, the tabular value and a soft accent plus button, all 48dp targets.
 */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementLabel: String,
    incrementLabel: String,
    minusIcon: ImageVector,
    plusIcon: ImageVector,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    val c = PeopleHubTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(c.surface2, VellutoShapes.Inner)
                .lightEdge(VellutoShapes.Inner, Color.Transparent, c.line)
                .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (dotColor != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .softShadow(dotColor.copy(alpha = 0.5f), CircleShape, ShadowSpec(10.dp))
                    .background(dotColor, CircleShape),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = c.text,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        WithTooltip(description = decrementLabel) {
            IconButton(
                onClick = onDecrement,
                enabled = canDecrement,
                modifier = Modifier.size(48.dp).lightEdge(CircleShape, Color.Transparent, c.line),
                colors = IconButtonDefaults.iconButtonColors(contentColor = c.text, disabledContentColor = c.text2.copy(alpha = 0.5f)),
            ) {
                Icon(minusIcon, contentDescription = decrementLabel, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = value,
            style = PeopleHubTheme.type.kpiSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        WithTooltip(description = incrementLabel) {
            IconButton(
                onClick = onIncrement,
                enabled = canIncrement,
                modifier = Modifier.size(48.dp).lightEdge(CircleShape, Color.Transparent, c.accentEdge),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = c.accentSoft,
                        contentColor = c.accentOnSoft,
                        disabledContainerColor = c.surface2,
                        disabledContentColor = c.text2.copy(alpha = 0.5f),
                    ),
            ) {
                Icon(plusIcon, contentDescription = incrementLabel, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * A grouped list inside a card (the notification-health checks): rows on a surface2 panel separated
 * by hairlines.
 */
@Composable
fun GroupedRows(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PeopleHubTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(c.surface2, VellutoShapes.Inner)
                .lightEdge(VellutoShapes.Inner, Color.Transparent, c.line)
                .padding(vertical = 4.dp),
        content = content,
    )
}

/** The 1dp hairline used between rows and blocks. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
            .heightIn(min = 1.dp, max = 1.dp)
            .background(PeopleHubTheme.colors.line),
    )
}

package com.peoplehub.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.theme.LabelCaps

/**
 * A selectable, pill-shaped tag filter chip with low-opacity gold fill and high-contrast gold text
 * when selected.
 */
@Composable
fun TagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            ),
    )
}

/**
 * A read-only, pill-shaped category chip (e.g. an event category), tinted gold.
 */
@Composable
fun CategoryChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    SuggestionChip(
        onClick = onClick ?: {},
        modifier = modifier,
        enabled = onClick != null,
        shape = RoundedCornerShape(50),
        label = {
            Text(
                text = label.uppercase(),
                style = LabelCaps,
                color = tint,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        },
        colors =
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = tint.copy(alpha = 0.12f),
                labelColor = tint,
            ),
        border =
            SuggestionChipDefaults.suggestionChipBorder(
                enabled = true,
                borderColor = tint.copy(alpha = 0.3f),
            ),
    )
}

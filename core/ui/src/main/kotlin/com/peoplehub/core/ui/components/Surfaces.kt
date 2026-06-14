package com.peoplehub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.theme.LabelCaps

/**
 * A glassmorphic panel: a tonal container with a 1px "ghost" gold-tinted border and soft rounded
 * corners, the workhorse surface of the Neo-Luxury layout.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        content = content,
    )
}

/**
 * A thin centred gold line that fades to transparent at both ends — used to separate major
 * narrative sections instead of a plain rule.
 */
@Composable
fun GoldDivider(modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, gold.copy(alpha = 0.5f), Color.Transparent),
                    ),
                ),
    )
}

/** A small uppercase "watchmaking" label rendered in the brand caps style. */
@Composable
fun CapsLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
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
 * A section header: an editorial serif title with an optional trailing action (e.g. "VIEW ALL").
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel.uppercase(),
                style = LabelCaps,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onActionClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

package com.peoplehub.core.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * An [IconButton] that explains itself: long-pressing (or hovering) reveals a Material 3 plain
 * tooltip with [description], which is also used as the icon's accessibility `contentDescription`.
 *
 * Keeping the experimental tooltip opt-in inside this wrapper means call sites stay opt-in free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    WithTooltip(description = description) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Icon(imageVector = icon, contentDescription = description, tint = tint)
        }
    }
}

/**
 * Wraps arbitrary [content] (a FAB, a custom button, …) with a plain tooltip showing [description].
 * Use this when [TooltipIconButton] does not fit the call site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    description: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
        content = content,
    )
}

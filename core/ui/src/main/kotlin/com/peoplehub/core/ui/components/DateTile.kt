package com.peoplehub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes

/**
 * A 50x54 calendar tile: caps month in accent over the day number ("OTT / 23"). [highlighted] lifts
 * it onto the card surface, for the "today" row that sits on a soft accent band.
 */
@Composable
fun DateTile(
    month: String,
    day: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val c = PeopleHubTheme.colors
    Column(
        modifier =
            modifier
                .size(width = 50.dp, height = 54.dp)
                .background(if (highlighted) c.surface else c.surface2, VellutoShapes.DateTile)
                .lightEdge(VellutoShapes.DateTile, Color.Transparent, c.line),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = month.uppercase(),
            style = PeopleHubTheme.type.tileCaps,
            color = if (highlighted) c.accentTextSurface else c.accentText,
            maxLines = 1,
        )
        Text(text = day, style = PeopleHubTheme.type.kpiTile, color = c.text, maxLines = 1)
    }
}

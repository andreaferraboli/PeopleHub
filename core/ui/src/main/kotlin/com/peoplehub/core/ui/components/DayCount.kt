package com.peoplehub.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.peoplehub.core.ui.theme.PeopleHubTheme

/**
 * The elapsed/remaining day counter of events and birthdays: an optional caps [prefix] ("TRA"), a
 * big tabular Geist number and a caps [unitLabel] ("GIORNI", "GIORNI FA"), right-aligned by default
 * as on the Velluto covers.
 *
 * @param emphasized upcoming items use the accent-as-text colour, past ones the primary text colour.
 * @param onMedia draws it in the light cover colours, for use over a photo or illustrated scene.
 */
@Composable
fun DayCountDisplay(
    number: Int,
    unitLabel: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    emphasized: Boolean = true,
    onMedia: Boolean = false,
    numberStyle: TextStyle = PeopleHubTheme.type.kpiMedium,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    val c = PeopleHubTheme.colors
    val numberColor: Color =
        when {
            onMedia -> c.onMedia
            emphasized -> c.accentText
            else -> c.text
        }
    val capsColor = if (onMedia) c.onMediaMuted else c.text2
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        if (prefix != null) {
            Text(text = prefix.uppercase(), style = PeopleHubTheme.type.tileCaps, color = capsColor)
        }
        Text(text = number.toString(), style = numberStyle, color = numberColor, maxLines = 1)
        Text(text = unitLabel.uppercase(), style = PeopleHubTheme.type.tileCaps, color = capsColor)
    }
}

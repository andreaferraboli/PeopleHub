package com.peoplehub.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.peoplehub.core.ui.theme.LabelCaps

/**
 * The large elapsed/remaining day counter used by events and birthdays: an optional caps prefix
 * (e.g. "IN"), a serif display number, and a caps unit label (e.g. "DAYS" / "DAYS AGO").
 *
 * @param emphasized when true (typically future/upcoming) the number is rendered in champagne gold;
 * otherwise it uses a muted on-surface tone for past items.
 */
@Composable
fun DayCountDisplay(
    number: Int,
    unitLabel: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    emphasized: Boolean = true,
) {
    val numberColor: Color =
        if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier) {
        if (prefix != null) {
            Text(text = prefix.uppercase(), style = LabelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = numberColor,
        )
        Text(
            text = unitLabel.uppercase(),
            style = LabelCaps,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

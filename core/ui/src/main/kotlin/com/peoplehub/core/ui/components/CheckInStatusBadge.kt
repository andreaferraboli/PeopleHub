package com.peoplehub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.ui.theme.LabelCaps
import com.peoplehub.core.ui.theme.statusColorsFor

/**
 * A small semantic pill describing check-in recency — "Seen X days ago" — coloured by [status]:
 * green when fresh, amber when due, red when overdue or never seen.
 *
 * @param label the already-localised, already-formatted recency text supplied by the feature layer.
 */
@Composable
fun CheckInStatusBadge(
    label: String,
    status: CheckInStatus,
    modifier: Modifier = Modifier,
) {
    val colors = statusColorsFor(status)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = label.uppercase(), style = LabelCaps, color = colors.onContainer)
    }
}

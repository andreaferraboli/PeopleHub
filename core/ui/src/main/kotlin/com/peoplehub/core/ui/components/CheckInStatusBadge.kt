package com.peoplehub.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.statusColorsFor

/**
 * The check-in recency pill ("Visto 42 giorni fa"): soft fill, dot and text in the status colour:
 * green when fresh, amber when due, red when overdue, neutral with a hairline when never seen.
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
    StatusPill(
        label = label,
        content = colors.onContainer,
        container = colors.container,
        modifier = modifier,
        border = if (status == CheckInStatus.NEVER) PeopleHubTheme.colors.line else androidx.compose.ui.graphics.Color.Transparent,
    )
}

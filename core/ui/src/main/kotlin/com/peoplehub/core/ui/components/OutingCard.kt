package com.peoplehub.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.ui.R
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OutingDateFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())

/** How many attendee avatars are drawn before the rest are summarised as "+N". */
private const val MAX_AVATARS = 4

/**
 * One entry of the outings history: the day, the description, and everyone who was there.
 *
 * The whole card and the pencil both open the editor through [onEdit], because editing is the only
 * thing there is to do with a past outing — the date, the description and the attendee list are all
 * rewritten from there, for every attendee at once.
 */
@Composable
fun OutingCard(
    outing: Outing,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapsLabel(text = outing.date.format(OutingDateFormatter), modifier = Modifier.weight(1f))
                TooltipIconButton(
                    icon = Icons.Outlined.Edit,
                    description = stringResource(R.string.outing_edit),
                    onClick = onEdit,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = outing.note ?: stringResource(R.string.outing_no_description),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (outing.note != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                fontStyle = if (outing.note == null) FontStyle.Italic else FontStyle.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            AttendeeRow(outing)
        }
    }
}

@Composable
private fun AttendeeRow(outing: Outing) {
    val shown = outing.attendees.take(MAX_AVATARS)
    val hidden = outing.attendees.size - shown.size
    Row(verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { attendee ->
            PersonAvatar(
                initials = attendee.initials,
                photoPath = attendee.photoPath,
                size = 28.dp,
                shape = CircleShape,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        if (hidden > 0) {
            Text(
                text = stringResource(R.string.outing_more_people, hidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = outing.attendees.joinToString { it.fullName },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

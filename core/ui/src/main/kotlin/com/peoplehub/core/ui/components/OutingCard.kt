package com.peoplehub.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.ui.R
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WeekdayFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

private val MonthFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

/** How many attendee avatars are drawn before the rest are summarised as "+N". */
private const val MAX_AVATARS = 3

/**
 * One entry of the outings history ("Cronologia uscite"): a date column (weekday, day, month), the
 * description, and the overlapping faces of everyone who was there with their names.
 *
 * The whole card opens the editor through [onEdit] (announced to accessibility services with the
 * "edit outing" label), because editing is the only thing there is to do with a past outing — the
 * date, the description and the attendee list are all rewritten from there, for every attendee.
 */
@Composable
fun OutingCard(
    outing: Outing,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val editLabel = stringResource(R.string.outing_edit)
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        shape = VellutoShapes.CardSmall,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit)
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.width(50.dp).padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text =
                        outing.date
                            .format(WeekdayFormatter)
                            .trimEnd('.')
                            .uppercase(),
                    style = PeopleHubTheme.type.tileCaps,
                    color = c.accentTextSurface,
                )
                Text(text = outing.date.dayOfMonth.toString(), style = PeopleHubTheme.type.kpiTile, color = c.text)
                Text(
                    text = outing.date.format(MonthFormatter).trimEnd('.'),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing),
                    color = c.text2,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = outing.note ?: stringResource(R.string.outing_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (outing.note != null) c.text else c.text2,
                    fontStyle = if (outing.note == null) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                AttendeeRow(outing)
            }
        }
    }
}

@Composable
private fun AttendeeRow(outing: Outing) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AvatarStack(
            faces = outing.attendees.map { StackedFace(it.initials, it.photoPath) },
            max = MAX_AVATARS,
        )
        Text(
            text = outing.attendees.joinToString { it.fullName },
            style = MaterialTheme.typography.bodySmall,
            color = PeopleHubTheme.colors.text2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

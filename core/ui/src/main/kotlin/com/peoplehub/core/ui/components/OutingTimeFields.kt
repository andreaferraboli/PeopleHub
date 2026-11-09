package com.peoplehub.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.peoplehub.core.domain.model.OutingTimes
import com.peoplehub.core.ui.R
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.theme.PeopleHubTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private enum class TimeField { START, END }

/**
 * The optional time range of an outing in a form: a check pill that turns the range on, then a start
 * and an end button that open a 24h time picker.
 *
 * [times] is `null` while the outing has no range (the app's implicit noon, no end). Turning the pill
 * on seeds a two-hour range from [defaultStart]. An end at or before the start is the next morning and
 * the end button says so, so a night out (18:00 -> 07:30) needs no date of its own.
 */
@Composable
fun OutingTimeFields(
    times: OutingTimes?,
    onTimesChange: (OutingTimes?) -> Unit,
    defaultStart: LocalTime,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<TimeField?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.m)) {
        ToggleChip(
            label = stringResource(R.string.outing_time_toggle),
            checked = times != null,
            onCheckedChange = { on -> onTimesChange(if (on) OutingTimes.startingAt(defaultStart) else null) },
            checkIcon = VellutoIcons.Check,
        )
        if (times != null) {
            VellutoButton(
                text = stringResource(R.string.outing_time_start, times.start.format(TimeFormat)),
                onClick = { picking = TimeField.START },
                style = VellutoButtonStyle.Neutral,
                icon = VellutoIcons.Clock,
                modifier = Modifier.fillMaxWidth(),
            )
            VellutoButton(
                text =
                    stringResource(
                        if (times.endsNextDay) R.string.outing_time_end_next_day else R.string.outing_time_end,
                        times.end.format(TimeFormat),
                    ),
                onClick = { picking = TimeField.END },
                style = VellutoButtonStyle.Neutral,
                icon = VellutoIcons.Clock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val field = picking
    if (field != null && times != null) {
        TimePickDialog(
            initial = if (field == TimeField.START) times.start else times.end,
            onPicked = { picked ->
                onTimesChange(if (field == TimeField.START) times.copy(start = picked) else times.copy(end = picked))
            },
            onDismiss = { picking = null },
        )
    }
}

/**
 * "09:00 - 11:30" for an outing that runs from [start] to [end] in [zone], marked "(+1)" when it ends
 * on the next day or later.
 */
@Composable
fun outingTimeRangeLabel(
    start: Instant,
    end: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val from = start.atZone(zone)
    val to = end.atZone(zone)
    val nextDay = to.toLocalDate().isAfter(from.toLocalDate())
    return stringResource(
        if (nextDay) R.string.outing_time_range_next_day else R.string.outing_time_range,
        from.format(TimeFormat),
        to.format(TimeFormat),
    )
}

/** A 24h [TimePicker] in a dialog, seeded with [initial]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(
    initial: LocalTime,
    onPicked: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.outing_time_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.outing_time_cancel)) }
        },
    )
}

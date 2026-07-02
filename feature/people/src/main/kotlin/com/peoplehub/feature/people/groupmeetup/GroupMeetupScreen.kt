package com.peoplehub.feature.people.groupmeetup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.feature.people.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val MeetupDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * "Record an outing": select everyone you saw and log one meetup for each of them at once, optionally
 * across several days. Navigates back automatically once the meetup has been saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMeetupScreen(
    onBack: () -> Unit,
    viewModel: GroupMeetupViewModel = hiltViewModel(),
) {
    val peopleState by viewModel.people.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    Scaffold(
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.group_meetup_title),
                navigationIcon = {
                    TooltipIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
            )
        },
        bottomBar = {
            PrimaryGoldButton(
                text =
                    if (form.selectedCount > 0) {
                        stringResource(R.string.group_meetup_save_count, form.selectedCount)
                    } else {
                        stringResource(R.string.group_meetup_save)
                    },
                onClick = viewModel::onSave,
                enabled = form.selectedCount > 0,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MeetupDetailsCard(
                form = form,
                onToggleMultiDay = viewModel::onToggleMultiDay,
                onStartDate = viewModel::onStartDate,
                onEndDate = viewModel::onEndDate,
                onNoteChange = viewModel::onNoteChange,
            )
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.group_meetup_search)) },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
            )
            UiStateContent(
                state = peopleState,
                emptyContent = {
                    Text(
                        text = stringResource(R.string.group_meetup_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) { people ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(people, key = { it.id }) { person ->
                        PersonPickRow(person = person, onToggle = { viewModel.onToggle(person.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetupDetailsCard(
    form: GroupMeetupForm,
    onToggleMultiDay: (Boolean) -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onEndDate: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CapsLabel(text = stringResource(R.string.group_meetup_when))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onToggleMultiDay(!form.multiDay) },
            ) {
                Checkbox(checked = form.multiDay, onCheckedChange = onToggleMultiDay)
                Text(
                    text = stringResource(R.string.checkin_multiday_toggle),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            GhostButton(
                text =
                    when {
                        form.multiDay ->
                            stringResource(R.string.checkin_date_from, form.startDate.format(MeetupDateFormat))
                        form.startDate == today -> stringResource(R.string.checkin_date_today)
                        else -> stringResource(R.string.checkin_date_on, form.startDate.format(MeetupDateFormat))
                    },
                onClick = { showStartPicker = true },
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.multiDay) {
                GhostButton(
                    text = stringResource(R.string.checkin_date_to, form.endDate.format(MeetupDateFormat)),
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = form.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.checkin_note_hint)) },
                shape = RoundedCornerShape(6.dp),
            )
        }
    }

    if (showStartPicker) {
        PastDatePickerDialog(
            initialDate = form.startDate,
            minDate = null,
            onPicked = onStartDate,
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        PastDatePickerDialog(
            initialDate = form.endDate,
            minDate = form.startDate,
            onPicked = onEndDate,
            onDismiss = { showEndPicker = false },
        )
    }
}

@Composable
private fun PersonPickRow(person: SelectablePerson, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(initials = person.initials, photoPath = person.photoPath, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = person.fullName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = person.selected, onCheckedChange = { onToggle() })
    }
}

/** A [DatePickerDialog] restricted to today or earlier, optionally floored at [minDate]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePickerDialog(
    initialDate: LocalDate,
    minDate: LocalDate?,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val todayUtcMillis =
        remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val minUtcMillis = minDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        utcTimeMillis <= todayUtcMillis && (minUtcMillis == null || utcTimeMillis >= minUtcMillis)
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

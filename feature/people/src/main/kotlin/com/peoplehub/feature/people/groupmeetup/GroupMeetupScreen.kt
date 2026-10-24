package com.peoplehub.feature.people.groupmeetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.SearchField
import com.peoplehub.core.ui.components.SettingsCard
import com.peoplehub.core.ui.components.ToggleChip
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.glassSurface
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.safeBottomBarPadding
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.feature.people.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val MeetupDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * "Record an outing" in both of its modes: select everyone you saw and log one meetup for each of them
 * at once, optionally across several days — or, when the route carries an outing id, rewrite that
 * outing's day, description and attendee list for everyone involved. Navigates back automatically once
 * the outing has been saved or deleted.
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
    val selectedNames by viewModel.selectedNames.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title =
                        stringResource(
                            if (form.editing) R.string.outing_edit_title else R.string.group_meetup_title,
                        ),
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.s, end = spacing.xs),
                        )
                    },
                    actions = {
                        if (form.editing) {
                            GlassIconButton(
                                icon = VellutoIcons.Trash2,
                                description = stringResource(R.string.outing_delete),
                                onClick = { showDeleteDialog = true },
                                tint = c.err,
                                modifier = Modifier.padding(end = spacing.s),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                VellutoButton(
                    text =
                        when {
                            form.editing -> stringResource(R.string.outing_save_changes)
                            form.selectedCount > 0 ->
                                stringResource(R.string.group_meetup_save_count, form.selectedCount)
                            else -> stringResource(R.string.group_meetup_save)
                        },
                    onClick = viewModel::onSave,
                    enabled = form.selectedCount > 0,
                    icon = VellutoIcons.Check,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .safeBottomBarPadding()
                            .padding(horizontal = spacing.gutter, vertical = spacing.m),
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(horizontal = spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                MeetupDetailsCard(
                    form = form,
                    attendeeNames = selectedNames,
                    onToggleMultiDay = viewModel::onToggleMultiDay,
                    onStartDate = viewModel::onStartDate,
                    onEndDate = viewModel::onEndDate,
                    onNoteChange = viewModel::onNoteChange,
                    modifier = Modifier.padding(top = spacing.xs).riseIn(0),
                )
                SearchField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.onQueryChange(it)
                    },
                    placeholder = stringResource(R.string.group_meetup_search),
                    modifier = Modifier.riseIn(1),
                )
                UiStateContent(
                    state = peopleState,
                    emptyContent = {
                        Text(
                            text = stringResource(R.string.group_meetup_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text2,
                            modifier = Modifier.padding(horizontal = spacing.xxs),
                        )
                    },
                ) { people ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = spacing.xxs, bottom = spacing.l),
                        verticalArrangement = Arrangement.spacedBy(spacing.s),
                    ) {
                        items(people, key = { it.id }) { person ->
                            PersonPickRow(person = person, onToggle = { viewModel.onToggle(person.id) })
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.outing_delete_title)) },
            text = { Text(stringResource(R.string.outing_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.onDelete()
                }) { Text(stringResource(R.string.outing_delete), color = c.err) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun MeetupDetailsCard(
    form: GroupMeetupForm,
    attendeeNames: List<String>,
    onToggleMultiDay: (Boolean) -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onEndDate: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val c = PeopleHubTheme.colors

    SettingsCard(
        title = stringResource(R.string.group_meetup_when),
        icon = VellutoIcons.CalendarDays,
        modifier = modifier,
    ) {
        // An edit always concerns the single day the outing was recorded on.
        if (!form.editing) {
            ToggleChip(
                label = stringResource(R.string.checkin_multiday_toggle),
                checked = form.multiDay,
                onCheckedChange = onToggleMultiDay,
                checkIcon = VellutoIcons.Check,
            )
        }
        VellutoButton(
            text =
                when {
                    form.multiDay ->
                        stringResource(R.string.checkin_date_from, form.startDate.format(MeetupDateFormat))
                    form.startDate == today -> stringResource(R.string.checkin_date_today)
                    else -> stringResource(R.string.checkin_date_on, form.startDate.format(MeetupDateFormat))
                },
            onClick = { showStartPicker = true },
            style = VellutoButtonStyle.Neutral,
            icon = VellutoIcons.Calendar,
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.multiDay) {
            VellutoButton(
                text = stringResource(R.string.checkin_date_to, form.endDate.format(MeetupDateFormat)),
                onClick = { showEndPicker = true },
                style = VellutoButtonStyle.Neutral,
                icon = VellutoIcons.Calendar,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = form.note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.checkin_note_hint)) },
        )
        if (attendeeNames.isNotEmpty()) {
            Text(
                text = stringResource(R.string.outing_attendees, attendeeNames.joinToString()),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text2,
            )
        }
        form.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = c.err,
            )
        }
    }

    if (showStartPicker) {
        MeetupDatePickerDialog(
            initialDate = form.startDate,
            minDate = null,
            onPicked = onStartDate,
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        MeetupDatePickerDialog(
            initialDate = form.endDate,
            minDate = form.startDate,
            onPicked = onEndDate,
            onDismiss = { showEndPicker = false },
        )
    }
}

/**
 * One person in the picker: a glass row (soft accent with an accent hairline once picked), the face
 * ringed in accent when selected, the name and a round check mark. The whole row toggles.
 */
@Composable
private fun PersonPickRow(
    person: SelectablePerson,
    onToggle: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val shape = VellutoShapes.Inner
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(
                    if (person.selected) {
                        Modifier.background(c.accentSoft, shape).lightEdge(shape, Color.Transparent, c.accentEdge)
                    } else {
                        Modifier.glassSurface(c, shape)
                    },
                ).clip(shape)
                .toggleable(value = person.selected, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = spacing.m, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        PersonAvatar(
            initials = person.initials,
            photoPath = person.photoPath,
            size = 44.dp,
            ringColor = if (person.selected) c.accent else null,
        )
        Text(
            text = person.fullName,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (person.selected) FontWeight.SemiBold else FontWeight.Normal),
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PickMark(selected = person.selected)
    }
}

/** The round check of a picker row: accent disc with a dark tick when picked, a hairline ring otherwise. */
@Composable
private fun PickMark(selected: Boolean) {
    val c = PeopleHubTheme.colors
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .then(
                    if (selected) {
                        Modifier.background(c.accent, CircleShape)
                    } else {
                        Modifier.lightEdge(CircleShape, Color.Transparent, MaterialTheme.colorScheme.outline, width = 1.5.dp)
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(VellutoIcons.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}

/** A [DatePickerDialog] for a meetup day, optionally floored at [minDate]. Future days are allowed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetupDatePickerDialog(
    initialDate: LocalDate,
    minDate: LocalDate?,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val minUtcMillis = minDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        minUtcMillis == null || utcTimeMillis >= minUtcMillis
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

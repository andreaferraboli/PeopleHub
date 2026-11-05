package com.peoplehub.feature.reminders.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.reminder.ReminderPreset
import com.peoplehub.core.domain.util.ReminderScheduling
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GroupedRows
import com.peoplehub.core.ui.components.Hairline
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.SettingsRow
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.StepperRow
import com.peoplehub.core.ui.components.SurfaceCard
import com.peoplehub.core.ui.components.SwitchRow
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.TextAction
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.safeBottomBarPadding
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.feature.reminders.R
import com.peoplehub.feature.reminders.descriptionRes
import com.peoplehub.feature.reminders.labelRes
import com.peoplehub.feature.reminders.nameRes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Add or edit a per-person relationship reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderScreen(
    onBack: () -> Unit,
    onOpenScience: () -> Unit,
    viewModel: AddEditReminderViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val historyError by viewModel.historyError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPresets by remember { mutableStateOf(false) }
    var showNextPicker by remember { mutableStateOf(false) }
    var completionEditor by remember { mutableStateOf<CompletionEditor?>(null) }
    val futureMessage = stringResource(R.string.reminder_history_error_future)
    val failedMessage = stringResource(R.string.reminder_history_error_failed)

    LaunchedEffect(saved) {
        if (saved) onBack()
    }
    LaunchedEffect(error) {
        val message = error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }
    LaunchedEffect(historyError) {
        val kind = historyError
        if (kind != null) {
            snackbarHostState.showSnackbar(if (kind == HistoryError.FUTURE) futureMessage else failedMessage)
            viewModel.onHistoryErrorShown()
        }
    }

    val spacing = PeopleHubTheme.spacing
    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title =
                        if (viewModel.isEditing) {
                            stringResource(R.string.edit_reminder_title_edit)
                        } else {
                            stringResource(R.string.edit_reminder_title_new)
                        },
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.s),
                        )
                    },
                    actions = {
                        GlassIconButton(
                            icon = VellutoIcons.Flask,
                            description = stringResource(R.string.reminders_why),
                            onClick = onOpenScience,
                            modifier = Modifier.padding(end = spacing.s),
                        )
                    },
                )
            },
            bottomBar = {
                VellutoButton(
                    text = stringResource(R.string.action_save),
                    onClick = viewModel::onSave,
                    enabled = form.canSave,
                    icon = VellutoIcons.Check,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .safeBottomBarPadding()
                            .padding(horizontal = spacing.heroInset, vertical = spacing.l),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.gutter, vertical = spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.ml),
            ) {
                if (!viewModel.isEditing) {
                    VellutoButton(
                        text = stringResource(R.string.reminder_choose_preset),
                        onClick = { showPresets = true },
                        style = VellutoButtonStyle.Soft,
                        icon = VellutoIcons.Sparkles,
                        modifier = Modifier.fillMaxWidth().riseIn(0),
                    )
                }

                FormSection(modifier = Modifier.riseIn(1)) {
                    if (!viewModel.personLocked) {
                        PersonPicker(
                            people = people,
                            selectedId = form.personId,
                            onPersonChange = viewModel::onPersonChange,
                        )
                    }

                    OutlinedTextField(
                        value = form.title,
                        onValueChange = viewModel::onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.reminder_field_title)) },
                        isError = form.title.isBlank(),
                    )

                    OutlinedTextField(
                        value = form.note,
                        onValueChange = viewModel::onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text(stringResource(R.string.reminder_field_note)) },
                    )

                    SwitchRow(
                        label = stringResource(R.string.reminder_enabled_label),
                        supporting = stringResource(R.string.reminder_enabled_supporting),
                        checked = form.enabled,
                        onCheckedChange = viewModel::onEnabledChange,
                        modifier = Modifier.padding(horizontal = spacing.xxs),
                    )
                }

                FormSection(title = stringResource(R.string.reminder_field_category), modifier = Modifier.riseIn(2)) {
                    CategorySelector(selected = form.category, onSelect = viewModel::onCategoryChange)
                }

                FormSection(title = stringResource(R.string.reminder_cadence_label), modifier = Modifier.riseIn(3)) {
                    CadenceSection(
                        targetIntervalDays = form.targetIntervalDays,
                        jitterPercent = form.jitterPercent,
                        window = form.window,
                        onTargetChange = viewModel::onTargetChange,
                        onJitterChange = viewModel::onJitterChange,
                    )
                }

                FormSection(title = stringResource(R.string.reminder_next_section), modifier = Modifier.riseIn(4)) {
                    NextOccurrenceSection(
                        form = form,
                        onPick = { showNextPicker = true },
                        onReset = { viewModel.onNextOverrideChange(null) },
                    )
                }

                if (viewModel.isEditing) {
                    FormSection(title = stringResource(R.string.reminder_history_title), modifier = Modifier.riseIn(5)) {
                        HistorySection(
                            history = history,
                            onEdit = { entry -> completionEditor = CompletionEditor(entry) },
                            onAdd = { completionEditor = CompletionEditor(entry = null) },
                        )
                    }
                }
            }
        }
    }

    if (showNextPicker) {
        ReminderDatePickerDialog(
            initialDate = form.nextShown ?: viewModel.today.plusDays(form.targetIntervalDays.toLong()),
            maxDate = null,
            onPicked = viewModel::onNextOverrideChange,
            onDismiss = { showNextPicker = false },
        )
    }

    completionEditor?.let { editor ->
        val entry = editor.entry
        CompletionDialog(
            entry = entry,
            today = viewModel.today,
            onConfirm = { day, time, note ->
                if (entry == null) {
                    viewModel.onAddCompletion(day, time, note)
                } else {
                    viewModel.onUpdateCompletion(entry.id, day, time, note)
                }
                completionEditor = null
            },
            onDelete =
                entry?.let {
                    {
                        viewModel.onDeleteCompletion(it.id)
                        completionEditor = null
                    }
                },
            onDismiss = { completionEditor = null },
        )
    }

    if (showPresets) {
        PresetSheet(
            presets = viewModel.presets,
            onSelect = { preset, name, description ->
                viewModel.onApplyPreset(preset, name, description)
                showPresets = false
            },
            onDismiss = { showPresets = false },
        )
    }
}

/** A form group: glass card with an optional serif title over its fields. */
@Composable
private fun FormSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardLarge) {
        Column(
            modifier = Modifier.padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = PeopleHubTheme.colors.text,
                    modifier = Modifier.padding(horizontal = spacing.xxs).semantics { heading() },
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelector(
    selected: ReminderCategory,
    onSelect: (ReminderCategory) -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        ReminderCategory.entries.forEach { category ->
            TagChip(
                label = stringResource(category.labelRes()),
                selected = selected == category,
                onClick = { onSelect(category) },
                icon = category.icon(),
            )
        }
    }
}

@Composable
private fun CadenceSection(
    targetIntervalDays: Int,
    jitterPercent: Int,
    window: IntRange,
    onTargetChange: (Int) -> Unit,
    onJitterChange: (Int) -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val sliderColors = SliderDefaults.colors(inactiveTrackColor = c.track)

    // The stepper sets any cadence up to a year, one day at a time; the slider is the quick way
    // across the common range and simply stays at its end for longer cadences.
    StepperRow(
        label = stringResource(R.string.reminder_interval_value, targetIntervalDays),
        value = targetIntervalDays.toString(),
        onDecrement = { onTargetChange(targetIntervalDays - 1) },
        onIncrement = { onTargetChange(targetIntervalDays + 1) },
        decrementLabel = stringResource(R.string.reminder_interval_less),
        incrementLabel = stringResource(R.string.reminder_interval_more),
        minusIcon = VellutoIcons.Minus,
        plusIcon = VellutoIcons.Plus,
        canDecrement = targetIntervalDays > MIN_INTERVAL.toInt(),
        canIncrement = targetIntervalDays < MAX_STEPPER_INTERVAL,
    )
    Column(modifier = Modifier.padding(horizontal = spacing.xxs)) {
        Slider(
            value = targetIntervalDays.toFloat().coerceIn(MIN_INTERVAL, MAX_INTERVAL),
            onValueChange = { onTargetChange(it.roundToInt()) },
            valueRange = MIN_INTERVAL..MAX_INTERVAL,
            colors = sliderColors,
        )
    }

    Column(modifier = Modifier.padding(horizontal = spacing.xxs)) {
        Text(
            text = stringResource(R.string.reminder_jitter_value, jitterPercent),
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
        )
        Slider(
            value = jitterPercent.toFloat(),
            onValueChange = { onJitterChange(it.roundToInt()) },
            valueRange = 0f..ReminderScheduling.MAX_JITTER_PERCENT.toFloat(),
            colors = sliderColors,
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(VellutoShapes.Inner)
                .background(c.accentSoft)
                .lightEdge(VellutoShapes.Inner, Color.Transparent, c.accentEdge)
                .padding(horizontal = spacing.ml, vertical = spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(VellutoIcons.Sparkles, contentDescription = null, tint = c.accentOnSoft, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.reminder_window_explain, window.first, window.last),
            style = MaterialTheme.typography.bodyMedium,
            color = c.accentOnSoft,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSheet(
    presets: List<ReminderPreset>,
    onSelect: (ReminderPreset, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.heroInset)
                    .padding(bottom = spacing.section),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.reminder_presets_title),
                style = MaterialTheme.typography.headlineMedium,
                color = c.text,
                modifier = Modifier.semantics { heading() },
            )
            ReminderCategory.entries
                .filter { it != ReminderCategory.CUSTOM }
                .forEach { category ->
                    val group = presets.filter { it.category == category }
                    if (group.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = spacing.m, start = spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        ) {
                            Icon(category.icon(), contentDescription = null, tint = c.accentTextSurface, modifier = Modifier.size(16.dp))
                            CapsLabel(text = stringResource(category.labelRes()))
                        }
                        group.forEach { preset ->
                            val name = stringResource(preset.nameRes())
                            val description = stringResource(preset.descriptionRes())
                            PresetRow(
                                name = name,
                                description = description,
                                intervalDays = preset.targetIntervalDays,
                                onClick = { onSelect(preset, name, description) },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun PresetRow(
    name: String,
    description: String,
    intervalDays: Int,
    onClick: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(VellutoShapes.Inner)
                .background(c.surface2)
                .lightEdge(VellutoShapes.Inner, Color.Transparent, c.line)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = c.text2,
        )
        StatusPill(
            label = stringResource(R.string.reminder_interval_value, intervalDays),
            content = c.accentOnSoft,
            container = c.accentSoft,
            icon = VellutoIcons.Clock,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonPicker(
    people: List<PersonOption>,
    selectedId: String?,
    onPersonChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hint = stringResource(R.string.reminder_person_hint)
    val selectedName = people.firstOrNull { it.id == selectedId }?.name ?: hint

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            label = { Text(stringResource(R.string.reminder_field_person)) },
            leadingIcon = { Icon(VellutoIcons.Users, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                Icon(
                    if (expanded) VellutoIcons.ChevronUp else VellutoIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            people.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onPersonChange(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The Lucide glyph of a relationship category (same mapping as the reminders list). */
private fun ReminderCategory.icon(): ImageVector =
    when (this) {
        ReminderCategory.LOVE -> VellutoIcons.Heart
        ReminderCategory.FRIENDSHIP -> VellutoIcons.Users
        ReminderCategory.FAMILY -> VellutoIcons.House
        ReminderCategory.CUSTOM -> VellutoIcons.Sparkle
    }

private const val MIN_INTERVAL = 1f
private const val MAX_INTERVAL = 90f

/** Longest cadence the stepper reaches, the same bound the view model clamps to. */
private const val MAX_STEPPER_INTERVAL = 365

/** Short date of the history rows and the next occurrence, e.g. 4 Aug 2026. */
private val ShortDateFormat: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Time of day of a history row, e.g. 18:42. */
private val TimeOfDayFormat: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** The history dialog being shown: editing [entry], or adding a new time when it is `null`. */
private data class CompletionEditor(val entry: HistoryEntry?)

/**
 * "When": the next occurrence, automatic (the cadence window) or a day picked by hand, with the way
 * back to automatic once one has been picked.
 */
@Composable
private fun NextOccurrenceSection(
    form: ReminderForm,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val shown = form.nextShown
    GroupedRows {
        SettingsRow(
            label = stringResource(R.string.reminder_next_label),
            value =
                shown?.format(ShortDateFormat)
                    ?: stringResource(R.string.reminder_next_auto, form.window.first, form.window.last),
            icon = VellutoIcons.CalendarDays,
            trailingIcon = VellutoIcons.Pencil,
            onClick = onPick,
        )
    }
    Text(
        text = stringResource(R.string.reminder_next_hint),
        style = MaterialTheme.typography.bodySmall,
        color = c.text2,
        modifier = Modifier.padding(horizontal = spacing.xxs),
    )
    if (form.nextOverride != null) {
        TextAction(label = stringResource(R.string.reminder_next_reset), onClick = onReset)
    }
}

/**
 * "History": every time the reminder was done, each row opening its editor, and the button to add a
 * time that was never ticked. Edits apply immediately (they are not part of the form's Save).
 */
@Composable
private fun HistorySection(
    history: List<HistoryEntry>,
    onEdit: (HistoryEntry) -> Unit,
    onAdd: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Text(
        text = stringResource(R.string.reminder_history_hint),
        style = MaterialTheme.typography.bodySmall,
        color = c.text2,
        modifier = Modifier.padding(horizontal = spacing.xxs),
    )
    if (history.isEmpty()) {
        Text(
            text = stringResource(R.string.reminder_done_never),
            style = MaterialTheme.typography.bodyMedium,
            color = c.text2,
            modifier = Modifier.padding(horizontal = spacing.xxs),
        )
    } else {
        GroupedRows {
            history.forEachIndexed { index, entry ->
                if (index > 0) Hairline()
                SettingsRow(
                    label = entry.day.format(ShortDateFormat),
                    value = entry.time?.format(TimeOfDayFormat) ?: stringResource(R.string.reminder_history_no_time),
                    icon = VellutoIcons.CircleCheck,
                    trailingIcon = VellutoIcons.Pencil,
                    supporting = entry.note,
                    onClick = { onEdit(entry) },
                )
            }
        }
    }
    VellutoButton(
        text = stringResource(R.string.reminder_history_add),
        onClick = onAdd,
        style = VellutoButtonStyle.Soft,
        icon = VellutoIcons.Plus,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Adds a past time ([entry] `null`) or edits/deletes an existing one: date and time pickers, the
 * optional note (seeded from the entry, so saving never drops it), and the delete action for an
 * existing entry. Days after [today] cannot be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompletionDialog(
    entry: HistoryEntry?,
    today: LocalDate,
    onConfirm: (LocalDate, LocalTime, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    var day by remember(entry?.id) { mutableStateOf(entry?.day ?: today) }
    // A day-only entry opens at noon, the moment it is counted from.
    var time by remember(entry?.id) {
        mutableStateOf(entry?.let { it.time ?: LocalTime.NOON } ?: LocalTime.now().withSecond(0).withNano(0))
    }
    var note by remember(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (entry == null) R.string.reminder_history_add_title else R.string.reminder_history_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                VellutoButton(
                    text = day.format(ShortDateFormat),
                    onClick = { showDatePicker = true },
                    style = VellutoButtonStyle.Neutral,
                    icon = VellutoIcons.Calendar,
                    modifier = Modifier.fillMaxWidth(),
                )
                VellutoButton(
                    text = time.format(TimeOfDayFormat),
                    onClick = { showTimePicker = true },
                    style = VellutoButtonStyle.Neutral,
                    icon = VellutoIcons.Clock,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reminder_field_note)) },
                )
                if (onDelete != null) {
                    VellutoButton(
                        text = stringResource(R.string.reminder_history_delete),
                        onClick = onDelete,
                        style = VellutoButtonStyle.Danger,
                        icon = VellutoIcons.Trash2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day, time, note) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showDatePicker) {
        ReminderDatePickerDialog(
            initialDate = day,
            maxDate = today,
            onPicked = { day = it },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        ReminderTimePickerDialog(
            initial = time,
            onPicked = { time = it },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** A [DatePickerDialog] in Velluto surface colours, optionally capped at [maxDate]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePickerDialog(
    initialDate: LocalDate,
    maxDate: LocalDate?,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val maxUtcMillis = maxDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = maxUtcMillis == null || utcTimeMillis <= maxUtcMillis

                    override fun isSelectableYear(year: Int): Boolean = maxDate == null || year <= maxDate.year
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        colors = DatePickerDefaults.colors(containerColor = c.surface),
    ) {
        DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = c.surface))
    }
}

/** A time picker on a Velluto surface card, as in the event editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initial: LocalTime,
    onPicked: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    Dialog(onDismissRequest = onDismiss) {
        SurfaceCard(shape = VellutoShapes.Hero, shadow = VellutoElevation.Raised) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.l),
            ) {
                TimePicker(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = {
                        onPicked(LocalTime.of(timeState.hour, timeState.minute))
                        onDismiss()
                    }) { Text(stringResource(R.string.action_confirm)) }
                }
            }
        }
    }
}

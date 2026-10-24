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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.reminder.ReminderPreset
import com.peoplehub.core.domain.util.ReminderScheduling
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.safeBottomBarPadding
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.feature.reminders.R
import com.peoplehub.feature.reminders.descriptionRes
import com.peoplehub.feature.reminders.labelRes
import com.peoplehub.feature.reminders.nameRes
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showPresets by remember { mutableStateOf(false) }

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
            }
        }
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

    Column(modifier = Modifier.padding(horizontal = spacing.xxs)) {
        Text(
            text = stringResource(R.string.reminder_interval_value, targetIntervalDays),
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
        )
        Slider(
            value = targetIntervalDays.toFloat(),
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
    selectedId: Long?,
    onPersonChange: (Long?) -> Unit,
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

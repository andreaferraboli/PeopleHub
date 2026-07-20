package com.peoplehub.feature.reminders.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.reminder.ReminderPreset
import com.peoplehub.core.domain.util.ReminderScheduling
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.TooltipIconButton
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            if (viewModel.isEditing) {
                                stringResource(R.string.edit_reminder_title_edit)
                            } else {
                                stringResource(R.string.edit_reminder_title_new)
                            },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    TooltipIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    TooltipIconButton(
                        icon = Icons.Outlined.Science,
                        description = stringResource(R.string.reminders_why),
                        onClick = onOpenScience,
                    )
                },
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!viewModel.isEditing) {
                GhostButton(
                    text = stringResource(R.string.reminder_choose_preset),
                    onClick = { showPresets = true },
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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
                shape = RoundedCornerShape(6.dp),
            )

            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text(stringResource(R.string.reminder_field_note)) },
                shape = RoundedCornerShape(6.dp),
            )

            CategorySelector(selected = form.category, onSelect = viewModel::onCategoryChange)

            CadenceSection(
                targetIntervalDays = form.targetIntervalDays,
                jitterPercent = form.jitterPercent,
                window = form.window,
                onTargetChange = viewModel::onTargetChange,
                onJitterChange = viewModel::onJitterChange,
            )

            PrimaryGoldButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::onSave,
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelector(selected: ReminderCategory, onSelect: (ReminderCategory) -> Unit) {
    Column {
        CapsLabel(text = stringResource(R.string.reminder_field_category))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderCategory.entries.forEach { category ->
                TagChip(
                    label = stringResource(category.labelRes()),
                    selected = selected == category,
                    onClick = { onSelect(category) },
                )
            }
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
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            CapsLabel(text = stringResource(R.string.reminder_cadence_label))
            GoldDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = stringResource(R.string.reminder_interval_value, targetIntervalDays),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = targetIntervalDays.toFloat(),
                onValueChange = { onTargetChange(it.roundToInt()) },
                valueRange = MIN_INTERVAL..MAX_INTERVAL,
            )

            Text(
                text = stringResource(R.string.reminder_jitter_value, jitterPercent),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = jitterPercent.toFloat(),
                onValueChange = { onJitterChange(it.roundToInt()) },
                valueRange = 0f..ReminderScheduling.MAX_JITTER_PERCENT.toFloat(),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reminder_window_explain, window.first, window.last),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSheet(
    presets: List<ReminderPreset>,
    onSelect: (ReminderPreset, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reminder_presets_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ReminderCategory.entries
                .filter { it != ReminderCategory.CUSTOM }
                .forEach { category ->
                    val group = presets.filter { it.category == category }
                    if (group.isNotEmpty()) {
                        CapsLabel(text = stringResource(category.labelRes()))
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
private fun PresetRow(name: String, description: String, intervalDays: Int, onClick: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.reminder_interval_value, intervalDays),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            label = { Text(stringResource(R.string.reminder_field_person)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(6.dp),
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

private const val MIN_INTERVAL = 1f
private const val MAX_INTERVAL = 90f

package com.peoplehub.feature.events.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CategoryChip
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.feature.events.R
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Add or edit an event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEventScreen(
    onBack: () -> Unit,
    viewModel: AddEditEventViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val categorySuggestions by viewModel.categorySuggestions.collectAsStateWithLifecycle()
    val backgroundImageSuggestions by viewModel.backgroundImageSuggestions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val path = EventImageStorage.saveImage(context, uri)
                    if (path != null) viewModel.onBackgroundImageChange(path)
                }
            }
        }

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
                                stringResource(R.string.edit_event_title_edit)
                            } else {
                                stringResource(R.string.edit_event_title_new)
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
            OutlinedTextField(
                value = form.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.event_field_title)) },
                isError = form.title.isBlank(),
                shape = RoundedCornerShape(6.dp),
            )

            DateTimeFields(
                date = form.dateTime.toLocalDate().format(DateFormatter),
                time = form.dateTime.toLocalTime().format(TimeFormatter),
                dateMillis =
                    form.dateTime
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
                initialHour = form.dateTime.hour,
                initialMinute = form.dateTime.minute,
                onDateMillis = { millis ->
                    viewModel.onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
                onTime = viewModel::onTimeChange,
            )

            OutlinedTextField(
                value = form.category,
                onValueChange = viewModel::onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.event_field_category)) },
                shape = RoundedCornerShape(6.dp),
            )

            CategorySuggestions(
                suggestions = categorySuggestions,
                selected = form.category,
                onSelect = viewModel::onCategoryChange,
            )

            BackgroundImageSection(
                selectedPath = form.backgroundImagePath,
                suggestions = backgroundImageSuggestions,
                onPickNew = {
                    imageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onSelectExisting = viewModel::onBackgroundImageChange,
                onRemove = { viewModel.onBackgroundImageChange(null) },
            )

            PersonPicker(
                people = people,
                selectedId = form.personId,
                onPersonChange = viewModel::onPersonChange,
            )

            OutlinedTextField(
                value = form.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text(stringResource(R.string.event_field_description)) },
                shape = RoundedCornerShape(6.dp),
            )

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.event_field_pin),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = form.pinnedToWidget, onCheckedChange = viewModel::onPinnedChange)
                }
            }

            PrimaryGoldButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::onSave,
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeFields(
    date: String,
    time: String,
    dateMillis: Long,
    initialHour: Int,
    initialMinute: Int,
    onDateMillis: (Long) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            CapsLabel(text = stringResource(R.string.event_field_date))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(text = date, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            CapsLabel(text = stringResource(R.string.event_field_time))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(text = time, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(onDateMillis)
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
        Dialog(onDismissRequest = { showTimePicker = false }) {
            GlassPanel {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TimePicker(state = timeState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            onTime(LocalTime.of(timeState.hour, timeState.minute))
                            showTimePicker = false
                        }) { Text(stringResource(R.string.action_confirm)) }
                    }
                }
            }
        }
    }
}

/** Quick-pick chips for categories already used by other events. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySuggestions(
    suggestions: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { category ->
            CategoryChip(
                label = category,
                onClick = { onSelect(category) },
                tint =
                    if (category.equals(selected, ignoreCase = true)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/**
 * Lets the user attach a card background image: a preview of the current choice, a button to pick a
 * brand-new image, and a row of images already used by other events for quick reuse.
 */
@Composable
private fun BackgroundImageSection(
    selectedPath: String?,
    suggestions: List<String>,
    onPickNew: () -> Unit,
    onSelectExisting: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        CapsLabel(text = stringResource(R.string.event_field_background))
        Spacer(Modifier.height(8.dp))
        if (selectedPath != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = File(selectedPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                )
                TooltipIconButton(
                    icon = Icons.Outlined.Delete,
                    description = stringResource(R.string.event_background_remove),
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        GhostButton(
            text = stringResource(R.string.event_background_add),
            onClick = onPickNew,
            icon = Icons.Outlined.AddPhotoAlternate,
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.event_background_existing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions, key = { it }) { path ->
                    val isSelected = path == selectedPath
                    val borderColor =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                                .clickable { onSelectExisting(path) },
                    )
                }
            }
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
    val noneLabel = stringResource(R.string.event_person_none)
    val selectedName = people.firstOrNull { it.id == selectedId }?.name ?: noneLabel

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            modifier =
                Modifier
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            label = { Text(stringResource(R.string.event_field_person)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(6.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = {
                    onPersonChange(null)
                    expanded = false
                },
            )
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

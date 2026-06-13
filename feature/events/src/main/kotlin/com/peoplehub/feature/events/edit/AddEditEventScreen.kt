package com.peoplehub.feature.events.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.feature.events.R
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
    val snackbarHostState = remember { SnackbarHostState() }

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
                        text = if (viewModel.isEditing) {
                            stringResource(R.string.edit_event_title_edit)
                        } else {
                            stringResource(R.string.edit_event_title_new)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
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
                dateMillis = form.dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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
                    modifier = Modifier
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
            modifier = Modifier
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

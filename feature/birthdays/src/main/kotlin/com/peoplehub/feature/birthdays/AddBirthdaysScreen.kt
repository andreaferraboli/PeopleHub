package com.peoplehub.feature.birthdays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.TooltipIconButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DraftDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

/**
 * Lets the user add several birthday-only people in one pass: each row is just a name and a date.
 * Saving persists every complete row as a bare birthday (hidden from The Circle, kept in Milestones)
 * and pops back to the calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBirthdaysScreen(
    onBack: () -> Unit,
    viewModel: AddBirthdaysViewModel = hiltViewModel(),
) {
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    val canSave = drafts.any { it.isComplete }

    Scaffold(
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.birthdays_add_title),
                centered = true,
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
                text = stringResource(R.string.action_save),
                onClick = viewModel::onSave,
                enabled = canSave,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 20.dp,
                    end = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Text(
                    text = stringResource(R.string.birthdays_add_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(drafts, key = { it.rowId }) { draft ->
                BirthdayDraftRow(
                    draft = draft,
                    onNameChange = { viewModel.onNameChange(draft.rowId, it) },
                    onDateChange = { viewModel.onDateChange(draft.rowId, it) },
                    onRemove = { viewModel.onRemoveRow(draft.rowId) },
                )
            }

            item(key = "add_row") {
                GhostButton(
                    text = stringResource(R.string.add_birthday_add_another),
                    onClick = viewModel::onAddRow,
                    icon = Icons.Filled.Add,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayDraftRow(
    draft: BirthdayDraft,
    onNameChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onRemove: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.add_birthday_name)) },
                    shape = RoundedCornerShape(6.dp),
                )
                TooltipIconButton(
                    icon = Icons.Outlined.Close,
                    description = stringResource(R.string.add_birthday_remove),
                    onClick = onRemove,
                )
            }
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = draft.date?.format(DraftDateFormatter) ?: stringResource(R.string.add_birthday_pick_date),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showPicker) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    draft.date
                        ?.atStartOfDay(ZoneOffset.UTC)
                        ?.toInstant()
                        ?.toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

package com.peoplehub.feature.people.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CategoryChip
import com.peoplehub.core.ui.components.CheckInStatusBadge
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.util.RelativeTime
import com.peoplehub.feature.people.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val BirthdayFormatter = DateTimeFormatter.ofPattern("MMMM d")
private val HistoryFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm")

private fun Instant.formatHistory(): String =
    atZone(ZoneId.systemDefault()).format(HistoryFormatter)

/** Builds a safe, lowercase JSON filename from a person's name (e.g. "Eleanor Vance" → "eleanor-vance.json"). */
private fun suggestedExportName(fullName: String): String {
    val slug =
        fullName
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "person" }
    return "$slug.json"
}

/** Tabbed detail screen for a single person (Info / Check-in / Related). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(closed) {
        if (closed) onBack()
    }
    LaunchedEffect(importMessage) {
        val message = importMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onImportMessageShown()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val json =
                    runCatching {
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                    }.getOrNull()
                if (json != null) pendingImportJson = json
            }
        }

    val exportPerson = (state as? UiState.Success)?.data?.person
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val person = exportPerson
            if (uri != null && person != null) {
                val written =
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(viewModel.exportJson(person).toByteArray())
                        }
                    }.isSuccess
                viewModel.onExported(written, person.fullName)
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TooltipIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    val data = (state as? UiState.Success)?.data
                    if (data != null) {
                        TooltipIconButton(
                            icon = Icons.Outlined.FileDownload,
                            description = stringResource(R.string.detail_import_json),
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                        )
                        TooltipIconButton(
                            icon = Icons.Outlined.FileUpload,
                            description = stringResource(R.string.detail_export_json),
                            onClick = { exportLauncher.launch(suggestedExportName(data.person.fullName)) },
                        )
                        TooltipIconButton(
                            icon = Icons.Outlined.Edit,
                            description = stringResource(R.string.detail_edit),
                            onClick = { onEdit(data.person.id) },
                        )
                        TooltipIconButton(
                            icon = Icons.Outlined.Delete,
                            description = stringResource(R.string.detail_delete),
                            onClick = { showDeleteDialog = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        UiStateContent(state = state) { data ->
            PersonDetailBody(
                data = data,
                modifier = Modifier.padding(innerPadding),
                onCheckIn = viewModel::onCheckIn,
                onEditCheckIn = viewModel::onEditCheckIn,
                onDeleteCheckIns = viewModel::onDeleteCheckIns,
                onAppendNote = viewModel::onAppendNote,
                onEventClick = onEventClick,
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.onDelete()
                }) { Text(stringResource(R.string.detail_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    val pendingJson = pendingImportJson
    if (pendingJson != null) {
        val personName =
            (state as? UiState.Success)
                ?.data
                ?.person
                ?.fullName
                .orEmpty()
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text(stringResource(R.string.detail_import_json_title)) },
            text = { Text(stringResource(R.string.detail_import_json_message, personName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportJson = null
                    viewModel.onImportJson(pendingJson)
                }) { Text(stringResource(R.string.detail_import_json_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PersonDetailBody(
    data: PersonDetailData,
    onCheckIn: (String?, LocalDate) -> Unit,
    onEditCheckIn: (CheckIn, String?, LocalDate) -> Unit,
    onDeleteCheckIns: (List<Long>) -> Unit,
    onAppendNote: (String) -> Unit,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCheckInDialog by remember { mutableStateOf(false) }
    var showAppendNoteDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        DetailHero(data)
        PrimaryGoldButton(
            text = stringResource(R.string.detail_seen_today_button, data.person.firstName),
            onClick = { showCheckInDialog = true },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
        )
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
            detailTabs().forEachIndexed { index, titleRes ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        when (selectedTab) {
            0 -> InfoTab(data, onAddNote = { showAppendNoteDialog = true }, Modifier.weight(1f))
            1 -> CheckInTab(data, onEditCheckIn, onDeleteCheckIns, Modifier.weight(1f))
            else -> RelatedTab(data, onEventClick, Modifier.weight(1f))
        }
    }

    if (showCheckInDialog) {
        CheckInDialog(
            personName = data.person.firstName,
            onConfirm = { note, date ->
                showCheckInDialog = false
                onCheckIn(note, date)
            },
            onDismiss = { showCheckInDialog = false },
        )
    }

    if (showAppendNoteDialog) {
        AppendNoteDialog(
            onConfirm = { phrase ->
                showAppendNoteDialog = false
                onAppendNote(phrase)
            },
            onDismiss = { showAppendNoteDialog = false },
        )
    }
}

@Composable
private fun DetailHero(data: PersonDetailData) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PersonAvatar(
            initials = data.person.initials,
            photoPath = data.person.photoPath,
            size = 120.dp,
            shape = RoundedCornerShape(12.dp),
        )
        Text(
            text = data.person.fullName,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (data.person.tags.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.person.tags
                    .take(3)
                    .forEach { tag -> CategoryChip(label = tag) }
            }
        }
        if (data.person.isFamily) {
            CapsLabel(
                text = stringResource(R.string.label_family),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            CheckInStatusBadge(
                label = RelativeTime.seenLabel(data.daysSince),
                status = data.status,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun InfoTab(data: PersonDetailData, onAddNote: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        data.nextBirthday?.let { birthday ->
            item(key = "birthday") {
                InfoPanel(label = stringResource(R.string.detail_birthday)) {
                    Text(birthday.birthday.format(BirthdayFormatter), style = MaterialTheme.typography.headlineSmall)
                    birthday.turningAge?.let { age ->
                        Text(
                            text = stringResource(R.string.detail_turns, age),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (data.person.interests.isNotEmpty()) {
            item(key = "interests") {
                InfoPanel(label = stringResource(R.string.detail_interests)) {
                    data.person.interests.forEach { interest ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = interest.key,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = interest.value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        item(key = "notes") {
            InfoPanel(label = stringResource(R.string.detail_notes)) {
                if (data.person.notes.isNotBlank()) {
                    Text(data.person.notes, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        text = stringResource(R.string.detail_notes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GhostButton(
                    text = stringResource(R.string.detail_add_note),
                    onClick = onAddNote,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CheckInTab(
    data: PersonDetailData,
    onEditCheckIn: (CheckIn, String?, LocalDate) -> Unit,
    onDeleteCheckIns: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var editing by remember { mutableStateOf<CheckIn?>(null) }
    var pendingDelete by remember { mutableStateOf<List<Long>?>(null) }

    // Selection is keyed by id; drop any ids that no longer exist (e.g. after a deletion).
    val historyIds = data.history.map { it.id }.toSet()
    if (selectedIds.any { it !in historyIds }) {
        selectedIds = selectedIds intersect historyIds
    }
    if (selectionMode && selectedIds.isEmpty()) {
        selectionMode = false
    }

    fun clearSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onDelete = { if (selectedIds.isNotEmpty()) pendingDelete = selectedIds.toList() },
                onCancel = ::clearSelection,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "cadence") { CadencePanel(data) }
            if (data.history.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        text = stringResource(R.string.detail_history_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                items(data.history, key = { it.id }) { checkIn ->
                    CheckInRow(
                        checkIn = checkIn,
                        selectionMode = selectionMode,
                        selected = checkIn.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds =
                                    if (checkIn.id in selectedIds) selectedIds - checkIn.id else selectedIds + checkIn.id
                            } else {
                                editing = checkIn
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedIds = selectedIds + checkIn.id
                        },
                    )
                }
            }
        }
    }

    editing?.let { checkIn ->
        CheckInEditDialog(
            checkIn = checkIn,
            onSave = { note, date ->
                editing = null
                onEditCheckIn(checkIn, note, date)
            },
            onDelete = {
                editing = null
                pendingDelete = listOf(checkIn.id)
            },
            onDismiss = { editing = null },
        )
    }

    pendingDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.checkin_delete_title)) },
            text = { Text(stringResource(R.string.checkin_delete_message, ids.size)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    clearSelection()
                    onDeleteCheckIns(ids)
                }) { Text(stringResource(R.string.checkin_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onDelete: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.checkin_selected_count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            TooltipIconButton(
                icon = Icons.Outlined.Delete,
                description = stringResource(R.string.checkin_delete_selected),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun CadencePanel(data: PersonDetailData) {
    InfoPanel(label = stringResource(R.string.detail_cadence)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_cadence_value, data.threshold.warningDays, data.threshold.criticalDays),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CategoryChip(
                label =
                    if (data.isCustomThreshold) {
                        stringResource(R.string.detail_cadence_custom)
                    } else {
                        stringResource(R.string.detail_cadence_default)
                    },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheckInRow(
    checkIn: CheckIn,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    GlassPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                CapsLabel(text = checkIn.timestamp.formatHistory())
                val note = checkIn.note
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedTab(data: PersonDetailData, onEventClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (data.relatedEvents.isEmpty()) {
        EmptyView(
            title = stringResource(R.string.detail_related_empty_title),
            description = stringResource(R.string.detail_related_empty_desc),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(data.relatedEvents, key = { it.id }) { event -> RelatedEventRow(event, onEventClick) }
    }
}

@Composable
private fun RelatedEventRow(event: PersonEvent, onEventClick: (Long) -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable { onEventClick(event.id) }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                event.category?.let { CapsLabel(text = it) }
                Text(event.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun InfoPanel(label: String, content: @Composable () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            CapsLabel(text = label)
            GoldDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInDialog(personName: String, onConfirm: (String?, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    var note by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkin_dialog_title, personName)) },
        text = {
            Column {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.checkin_note_hint)) },
                    shape = RoundedCornerShape(6.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                GhostButton(
                    text =
                        if (selectedDate == today) {
                            stringResource(R.string.checkin_date_today)
                        } else {
                            stringResource(R.string.checkin_date_on, selectedDate.format(CHECK_IN_DATE_FORMAT))
                        },
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.ifBlank { null }, selectedDate) }) {
                Text(stringResource(R.string.checkin_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showDatePicker) {
        val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates =
                    object : SelectableDates {
                        // A meeting can only have happened today or in the past.
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
                    },
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private val CHECK_IN_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Edits an existing check-in's note and day, or deletes it outright. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInEditDialog(
    checkIn: CheckIn,
    onSave: (String?, LocalDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val initialDate = remember(checkIn.id) { checkIn.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() }
    var note by remember(checkIn.id) { mutableStateOf(checkIn.note.orEmpty()) }
    var selectedDate by remember(checkIn.id) { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkin_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.checkin_note_hint)) },
                    shape = RoundedCornerShape(6.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                GhostButton(
                    text =
                        if (selectedDate == today) {
                            stringResource(R.string.checkin_date_today)
                        } else {
                            stringResource(R.string.checkin_date_on, selectedDate.format(CHECK_IN_DATE_FORMAT))
                        },
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.checkin_delete_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note.ifBlank { null }, selectedDate) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showDatePicker) {
        val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates =
                    object : SelectableDates {
                        // A meeting can only have happened today or in the past.
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
                    },
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AppendNoteDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var phrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_add_note_title)) },
        text = {
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 12,
                placeholder = { Text(stringResource(R.string.detail_add_note_hint)) },
                shape = RoundedCornerShape(6.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(phrase) },
                enabled = phrase.isNotBlank(),
            ) { Text(stringResource(R.string.detail_add_note_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun detailTabs(): List<Int> = listOf(R.string.tab_info, R.string.tab_checkin, R.string.tab_related)

package com.peoplehub.feature.people.detail

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.domain.util.DateCalculations
import com.peoplehub.core.ui.components.AmbientGlow
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CategoryChip
import com.peoplehub.core.ui.components.CheckInStatusBadge
import com.peoplehub.core.ui.components.DateTile
import com.peoplehub.core.ui.components.DisplayTitle
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.Hairline
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.SegmentedPill
import com.peoplehub.core.ui.components.SettingsCard
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.SurfaceCard
import com.peoplehub.core.ui.components.TextAction
import com.peoplehub.core.ui.components.ToggleChip
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.core.ui.theme.statusColorsFor
import com.peoplehub.core.ui.util.RelativeTime
import com.peoplehub.feature.people.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val BirthdayFormatter = DateTimeFormatter.ofPattern("MMMM d")
private val MonthFormatter = DateTimeFormatter.ofPattern("MMM")
private val HistoryCaptionFormatter = DateTimeFormatter.ofPattern("EEEE • yyyy • HH:mm")

/** Names up to this length get the big display serif; longer ones drop to the cover size. */
private const val DISPLAY_NAME_MAX_LENGTH = 14

/** Entrance cascade indices are capped here so deep list rows do not replay a long delay. */
private const val MAX_RISE_INDEX = 6

private fun Instant.localDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private fun Instant.formatHistoryCaption(): String = atZone(ZoneId.systemDefault()).format(HistoryCaptionFormatter)

private fun LocalDate.monthLabel(): String = format(MonthFormatter).trimEnd('.')

private fun LocalDateTime.monthLabel(): String = format(MonthFormatter).trimEnd('.')

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
    onAddReminder: (Long) -> Unit,
    onEditReminder: (Long) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing

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

    val exportData = (state as? UiState.Success)?.data
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val data = exportData
            if (uri != null && data != null) {
                val written =
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(viewModel.exportJson(data.person, data.history).toByteArray())
                        }
                    }.isSuccess
                viewModel.onExported(written, data.person.fullName)
            }
        }

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title = "",
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.s),
                        )
                    },
                    actions = {
                        val data = (state as? UiState.Success)?.data
                        if (data != null) {
                            Row(
                                modifier = Modifier.padding(end = spacing.s),
                                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                            ) {
                                GlassIconButton(
                                    icon = VellutoIcons.Download,
                                    description = stringResource(R.string.detail_import_json),
                                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                                )
                                GlassIconButton(
                                    icon = VellutoIcons.Upload,
                                    description = stringResource(R.string.detail_export_json),
                                    onClick = { exportLauncher.launch(suggestedExportName(data.person.fullName)) },
                                )
                                GlassIconButton(
                                    icon = VellutoIcons.Pencil,
                                    description = stringResource(R.string.detail_edit),
                                    onClick = { onEdit(data.person.id) },
                                )
                                GlassIconButton(
                                    icon = VellutoIcons.Trash2,
                                    description = stringResource(R.string.detail_delete),
                                    onClick = { showDeleteDialog = true },
                                    tint = c.err,
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            UiStateContent(state = state) { data ->
                PersonDetailContent(
                    data = data,
                    modifier = Modifier.padding(innerPadding),
                    onCheckIn = viewModel::onCheckIn,
                    onEditCheckIn = viewModel::onEditCheckIn,
                    onDeleteCheckIns = viewModel::onDeleteCheckIns,
                    onAppendNote = viewModel::onAppendNote,
                    onEventClick = onEventClick,
                    onAddReminder = onAddReminder,
                    onEditReminder = onEditReminder,
                )
            }
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
                }) { Text(stringResource(R.string.detail_delete), color = c.err) }
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

/**
 * The stateless body of the person detail screen: the portrait hero, the "I saw them today" action,
 * the segmented tab switch and the selected tab. Only local UI state (tab, open dialogs) lives here.
 */
@Composable
internal fun PersonDetailContent(
    data: PersonDetailData,
    onCheckIn: (String?, LocalDate, LocalDate) -> Unit,
    onEditCheckIn: (CheckIn, String?, LocalDate) -> Unit,
    onDeleteCheckIns: (List<Long>) -> Unit,
    onAppendNote: (String) -> Unit,
    onEventClick: (Long) -> Unit,
    onAddReminder: (Long) -> Unit,
    onEditReminder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCheckInDialog by remember { mutableStateOf(false) }
    var showAppendNoteDialog by remember { mutableStateOf(false) }
    val spacing = PeopleHubTheme.spacing

    Column(modifier = modifier.fillMaxSize()) {
        DetailHero(data = data, modifier = Modifier.riseIn(0))
        VellutoButton(
            text = stringResource(R.string.detail_seen_today_button, data.person.firstName),
            onClick = { showCheckInDialog = true },
            icon = VellutoIcons.Check,
            breathing = data.status == CheckInStatus.OVERDUE,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter, vertical = spacing.m)
                    .riseIn(1),
        )
        SegmentedPill(
            options = detailTabs().map { stringResource(it) },
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier =
                Modifier
                    .padding(horizontal = spacing.gutter)
                    .padding(bottom = spacing.xs)
                    .riseIn(2),
        )
        when (selectedTab) {
            0 -> InfoTab(data, onAddNote = { showAppendNoteDialog = true }, Modifier.weight(1f))
            1 -> CheckInTab(data, onEditCheckIn, onDeleteCheckIns, Modifier.weight(1f))
            2 -> RelatedTab(data, onEventClick, Modifier.weight(1f))
            else ->
                RemindersTab(
                    data = data,
                    onAddReminder = { onAddReminder(data.person.id) },
                    onEditReminder = onEditReminder,
                    modifier = Modifier.weight(1f),
                )
        }
    }

    if (showCheckInDialog) {
        CheckInDialog(
            personName = data.person.firstName,
            onConfirm = { note, start, end ->
                showCheckInDialog = false
                onCheckIn(note, start, end)
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

/** Padding shared by the scrolling tab bodies: gutter at the sides, room to breathe at the bottom. */
@Composable
private fun tabContentPadding(): PaddingValues {
    val spacing = PeopleHubTheme.spacing
    return PaddingValues(start = spacing.gutter, end = spacing.gutter, top = spacing.ml, bottom = spacing.section)
}

/**
 * The portrait hero: a large avatar ringed in the check-in status colour (gold for family) on a soft
 * accent glow, the name as a two-tone serif title (surname in italic accent), tags and the status pill.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHero(
    data: PersonDetailData,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val person = data.person
    val status = statusColorsFor(data.status)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.heroInset, vertical = spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AmbientGlow(size = 220.dp, strong = false, modifier = Modifier.matchParentSize())
            PersonAvatar(
                initials = person.initials,
                photoPath = person.photoPath,
                size = 112.dp,
                ringColor = if (person.isFamily) c.accent else status.ring,
                ringGlow = if (person.isFamily) c.glowSoft else Color.Transparent,
            )
        }
        val nameStyle =
            if (person.fullName.length <= DISPLAY_NAME_MAX_LENGTH) {
                MaterialTheme.typography.displayMedium
            } else {
                MaterialTheme.typography.headlineLarge
            }
        // An empty accent leaves the title plain: a lone first name is not split in two.
        DisplayTitle(
            text = person.fullName,
            accent = person.lastName.trim(),
            style = nameStyle.copy(textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = spacing.l),
        )
        if (person.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                person.tags
                    .take(3)
                    .forEach { tag -> CategoryChip(label = tag) }
            }
        }
        if (person.isFamily) {
            StatusPill(
                label = stringResource(R.string.label_family),
                content = c.accentOnSoft,
                container = c.accentSoft,
                icon = VellutoIcons.Heart,
                border = c.accentEdge,
                modifier = Modifier.padding(top = spacing.m),
            )
        } else {
            CheckInStatusBadge(
                label = RelativeTime.seenLabel(data.daysSince),
                status = data.status,
                modifier = Modifier.padding(top = spacing.m),
            )
        }
    }
}

@Composable
private fun InfoTab(
    data: PersonDetailData,
    onAddNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = tabContentPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.ml),
    ) {
        data.nextBirthday?.let { birthday ->
            item(key = "birthday") {
                SettingsCard(
                    title = stringResource(R.string.detail_birthday),
                    icon = VellutoIcons.Cake,
                    modifier = Modifier.riseIn(3),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.ml),
                    ) {
                        DateTile(
                            month = birthday.birthday.monthLabel(),
                            day = birthday.birthday.dayOfMonth.toString(),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                            Text(
                                text = birthday.birthday.format(BirthdayFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                color = c.text,
                            )
                            birthday.turningAge?.let { age ->
                                Text(
                                    text = stringResource(R.string.detail_turns, age),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.text2,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (data.person.interests.isNotEmpty()) {
            item(key = "interests") {
                SettingsCard(
                    title = stringResource(R.string.detail_interests),
                    icon = VellutoIcons.Sparkles,
                    modifier = Modifier.riseIn(4),
                ) {
                    Column {
                        data.person.interests.forEachIndexed { index, interest ->
                            if (index > 0) Hairline()
                            InterestRow(interest)
                        }
                    }
                }
            }
        }
        item(key = "notes") {
            SettingsCard(
                title = stringResource(R.string.detail_notes),
                icon = VellutoIcons.Pencil,
                modifier = Modifier.riseIn(5),
            ) {
                if (data.person.notes.isNotBlank()) {
                    Text(data.person.notes, style = MaterialTheme.typography.bodyLarge, color = c.text)
                } else {
                    Text(
                        text = stringResource(R.string.detail_notes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text2,
                    )
                }
                VellutoButton(
                    text = stringResource(R.string.detail_add_note),
                    onClick = onAddNote,
                    style = VellutoButtonStyle.Soft,
                    icon = VellutoIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun InterestRow(interest: Interest) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Text(
            text = interest.key,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = interest.value,
            style = MaterialTheme.typography.bodyLarge,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
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
    val spacing = PeopleHubTheme.spacing

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
            contentPadding = tabContentPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "cadence") { CadencePanel(data, Modifier.riseIn(3)) }
            if (data.history.isEmpty()) {
                item(key = "history-empty") {
                    EmptyView(
                        title = stringResource(R.string.detail_history_empty_title),
                        description = stringResource(R.string.detail_history_empty_desc),
                        icon = VellutoIcons.CalendarX,
                    )
                }
            } else {
                itemsIndexed(data.history, key = { _, checkIn -> checkIn.id }) { index, checkIn ->
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
                        modifier = Modifier.riseIn((index + 4).coerceAtMost(MAX_RISE_INDEX + 2)),
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
                }) { Text(stringResource(R.string.checkin_delete_confirm), color = PeopleHubTheme.colors.err) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The multi-select toolbar: a glass pill with the count, a cancel link and the delete action. */
@Composable
private fun SelectionBar(
    count: Int,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    GlassPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.gutter, vertical = spacing.s),
        shape = VellutoShapes.Pill,
        shadow = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = spacing.xl, end = spacing.xs, top = spacing.xxs, bottom = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.checkin_selected_count, count),
                style = MaterialTheme.typography.labelLarge,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            TextAction(label = stringResource(R.string.action_cancel), onClick = onCancel)
            GlassIconButton(
                icon = VellutoIcons.Trash2,
                description = stringResource(R.string.checkin_delete_selected),
                onClick = onDelete,
                tint = c.err,
            )
        }
    }
}

@Composable
private fun CadencePanel(
    data: PersonDetailData,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    SettingsCard(
        title = stringResource(R.string.detail_cadence),
        icon = VellutoIcons.Clock,
        modifier = modifier.padding(bottom = spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_cadence_value, data.threshold.warningDays, data.threshold.criticalDays),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            if (data.isCustomThreshold) {
                StatusPill(
                    label = stringResource(R.string.detail_cadence_custom),
                    content = c.accentOnSoft,
                    container = c.accentSoft,
                    border = c.accentEdge,
                    dot = false,
                )
            } else {
                CategoryChip(label = stringResource(R.string.detail_cadence_default))
            }
        }
    }
}

/** One meetup of the person's own history: date tile, weekday/year/time caption and the note. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheckInRow(
    checkIn: CheckIn,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val day = checkIn.timestamp.localDate()
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        shape = VellutoShapes.CardSmall,
        borderColor = if (selected) c.accentEdge else c.line,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .heightIn(min = spacing.touchTarget)
                    .padding(spacing.ml),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.ml),
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            DateTile(month = day.monthLabel(), day = day.dayOfMonth.toString())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                CapsLabel(text = checkIn.timestamp.formatHistoryCaption())
                val note = checkIn.note
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedTab(
    data: PersonDetailData,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.relatedEvents.isEmpty()) {
        EmptyView(
            title = stringResource(R.string.detail_related_empty_title),
            description = stringResource(R.string.detail_related_empty_desc),
            icon = VellutoIcons.CalendarDays,
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = tabContentPadding(),
        verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.sm),
    ) {
        itemsIndexed(data.relatedEvents, key = { _, event -> event.id }) { index, event ->
            RelatedEventRow(event, onEventClick, Modifier.riseIn((index + 3).coerceAtMost(MAX_RISE_INDEX + 2)))
        }
    }
}

@Composable
private fun RelatedEventRow(
    event: PersonEvent,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.Card) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onEventClick(event.id) }
                    .padding(spacing.ml),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.ml),
        ) {
            DateTile(month = event.dateTime.monthLabel(), day = event.dateTime.dayOfMonth.toString())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                event.category?.let { CapsLabel(text = it) }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(VellutoIcons.ChevronRight, contentDescription = null, tint = c.text2, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RemindersTab(
    data: PersonDetailData,
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = tabContentPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "add-reminder") {
            VellutoButton(
                text = stringResource(R.string.detail_reminders_add),
                onClick = onAddReminder,
                style = VellutoButtonStyle.Soft,
                icon = VellutoIcons.Plus,
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xs).riseIn(3),
            )
        }
        if (data.reminders.isEmpty()) {
            item(key = "reminders-empty") {
                Text(
                    text = stringResource(R.string.detail_reminders_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.s),
                )
            }
        } else {
            itemsIndexed(data.reminders, key = { _, reminder -> reminder.id }) { index, reminder ->
                ReminderRow(reminder, onEditReminder, Modifier.riseIn((index + 4).coerceAtMost(MAX_RISE_INDEX + 2)))
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onEditReminder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val nextDate = reminder.nextFireAt.atZone(ZoneId.systemDefault()).toLocalDate()
    val days = DateCalculations.signedDaysFromToday(nextDate, LocalDate.now())
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onEditReminder(reminder.id) }
                    .padding(horizontal = spacing.xl, vertical = spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.headlineSmall,
                color = c.text,
            )
            reminder.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium, color = c.text2)
            }
            when {
                !reminder.enabled ->
                    StatusPill(
                        label = stringResource(R.string.detail_reminder_paused),
                        content = c.text2,
                        container = c.surface2,
                        icon = VellutoIcons.BellOff,
                        border = c.line,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                days <= 0L ->
                    StatusPill(
                        label = stringResource(R.string.detail_reminder_due),
                        content = c.accentOnSoft,
                        container = c.accentSoft,
                        icon = VellutoIcons.BellRing,
                        border = c.accentEdge,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                else ->
                    StatusPill(
                        label = stringResource(R.string.detail_reminder_in_days, days.toInt()),
                        content = c.text2,
                        container = c.surface2,
                        icon = VellutoIcons.Clock,
                        border = c.line,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInDialog(
    personName: String,
    onConfirm: (String?, LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var note by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var multiDay by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val spacing = PeopleHubTheme.spacing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkin_dialog_title, personName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.checkin_note_hint)) },
                )
                ToggleChip(
                    label = stringResource(R.string.checkin_multiday_toggle),
                    checked = multiDay,
                    onCheckedChange = {
                        multiDay = it
                        if (!it) endDate = startDate
                    },
                    checkIcon = VellutoIcons.Check,
                )
                VellutoButton(
                    text =
                        when {
                            multiDay ->
                                stringResource(R.string.checkin_date_from, startDate.format(CHECK_IN_DATE_FORMAT))
                            startDate == today -> stringResource(R.string.checkin_date_today)
                            else ->
                                stringResource(R.string.checkin_date_on, startDate.format(CHECK_IN_DATE_FORMAT))
                        },
                    onClick = { showStartPicker = true },
                    style = VellutoButtonStyle.Neutral,
                    icon = VellutoIcons.Calendar,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (multiDay) {
                    VellutoButton(
                        text = stringResource(R.string.checkin_date_to, endDate.format(CHECK_IN_DATE_FORMAT)),
                        onClick = { showEndPicker = true },
                        style = VellutoButtonStyle.Neutral,
                        icon = VellutoIcons.Calendar,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val end = if (multiDay) endDate else startDate
                onConfirm(note.ifBlank { null }, startDate, end)
            }) {
                Text(stringResource(R.string.checkin_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showStartPicker) {
        MeetupDatePickerDialog(
            initialDate = startDate,
            minDate = null,
            onPicked = { picked ->
                startDate = picked
                if (endDate.isBefore(picked)) endDate = picked
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        MeetupDatePickerDialog(
            initialDate = endDate,
            minDate = startDate,
            onPicked = { endDate = it },
            onDismiss = { showEndPicker = false },
        )
    }
}

/**
 * A [DatePickerDialog] for a meetup day, optionally floored at [minDate] so an end date can't
 * precede its start. Future days are selectable so a planned outing can be recorded ahead of time.
 */
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
    val spacing = PeopleHubTheme.spacing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkin_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.checkin_note_hint)) },
                )
                VellutoButton(
                    text =
                        if (selectedDate == today) {
                            stringResource(R.string.checkin_date_today)
                        } else {
                            stringResource(R.string.checkin_date_on, selectedDate.format(CHECK_IN_DATE_FORMAT))
                        },
                    onClick = { showDatePicker = true },
                    style = VellutoButtonStyle.Neutral,
                    icon = VellutoIcons.Calendar,
                    modifier = Modifier.fillMaxWidth(),
                )
                VellutoButton(
                    text = stringResource(R.string.checkin_delete_action),
                    onClick = onDelete,
                    style = VellutoButtonStyle.Danger,
                    icon = VellutoIcons.Trash2,
                    modifier = Modifier.fillMaxWidth(),
                )
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
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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
private fun AppendNoteDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
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

private fun detailTabs(): List<Int> = listOf(R.string.tab_info, R.string.tab_checkin, R.string.tab_related, R.string.tab_reminders)

// region Previews (sample data lives only here)

private fun previewDetailData(): PersonDetailData {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.of(2026, 10, 24)
    val birthday = LocalDate.of(1994, 11, 3)
    val person =
        Person(
            id = 7L,
            firstName = "Francesca",
            lastName = "Lombardi",
            birthday = birthday,
            tags = listOf("Amici", "Università"),
            interests = listOf(Interest("Libro preferito", "Le città invisibili"), Interest("Caffè", "Macchiato, poca schiuma")),
            notes = "Si è trasferita a Torino a settembre. Chiedere del nuovo lavoro.",
            lastCheckInAt = today.minusDays(42).atStartOfDay(zone).toInstant(),
        )
    return PersonDetailData(
        person = person,
        status = CheckInStatus.OVERDUE,
        daysSince = 42L,
        threshold = CheckInThreshold.Default,
        isCustomThreshold = false,
        nextBirthday =
            UpcomingBirthday(
                personId = person.id,
                fullName = person.fullName,
                photoPath = null,
                birthday = birthday,
                nextOccurrence = LocalDate.of(2026, 11, 3),
                daysUntil = 10,
                turningAge = 32,
            ),
        history =
            listOf(
                CheckIn(
                    id = 1L,
                    personId = 7L,
                    timestamp =
                        today
                            .minusDays(42)
                            .atTime(20, 30)
                            .atZone(zone)
                            .toInstant(),
                    note = "Cena da Sciamadda",
                ),
                CheckIn(
                    id = 2L,
                    personId = 7L,
                    timestamp =
                        today
                            .minusDays(80)
                            .atTime(18, 0)
                            .atZone(zone)
                            .toInstant(),
                ),
            ),
        relatedEvents = emptyList(),
        reminders = emptyList(),
    )
}

@Composable
private fun PersonDetailPreview(dark: Boolean) {
    PeopleHubTheme(darkTheme = dark) {
        VellutoBackground {
            PersonDetailContent(
                data = previewDetailData(),
                onCheckIn = { _, _, _ -> },
                onEditCheckIn = { _, _, _ -> },
                onDeleteCheckIns = {},
                onAppendNote = {},
                onEventClick = {},
                onAddReminder = {},
                onEditReminder = {},
                modifier = Modifier.statusBarsPadding().padding(top = 64.dp),
            )
        }
    }
}

@Preview(name = "Person detail, light", widthDp = 390, heightDp = 844)
@Composable
private fun PersonDetailLightPreview() {
    PersonDetailPreview(dark = false)
}

@Preview(name = "Person detail, dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PersonDetailDarkPreview() {
    PersonDetailPreview(dark = true)
}

// endregion

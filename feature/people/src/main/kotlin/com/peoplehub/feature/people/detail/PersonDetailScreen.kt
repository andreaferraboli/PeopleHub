package com.peoplehub.feature.people.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.util.RelativeTime
import com.peoplehub.feature.people.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BirthdayFormatter = DateTimeFormatter.ofPattern("MMMM d")
private val HistoryFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm")

private fun Instant.formatHistory(): String =
    atZone(ZoneId.systemDefault()).format(HistoryFormatter)

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

    LaunchedEffect(closed) {
        if (closed) onBack()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val data = (state as? UiState.Success)?.data
                    if (data != null) {
                        IconButton(onClick = { onEdit(data.person.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.detail_edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.detail_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        UiStateContent(state = state) { data ->
            PersonDetailBody(
                data = data,
                modifier = Modifier.padding(innerPadding),
                onCheckIn = viewModel::onCheckIn,
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
}

@Composable
private fun PersonDetailBody(
    data: PersonDetailData,
    onCheckIn: (String?) -> Unit,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCheckInDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        DetailHero(data)
        PrimaryGoldButton(
            text = stringResource(R.string.detail_seen_today_button, data.person.firstName),
            onClick = { showCheckInDialog = true },
            modifier = Modifier
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
            0 -> InfoTab(data, Modifier.weight(1f))
            1 -> CheckInTab(data, Modifier.weight(1f))
            else -> RelatedTab(data, onEventClick, Modifier.weight(1f))
        }
    }

    if (showCheckInDialog) {
        CheckInDialog(
            personName = data.person.firstName,
            onConfirm = { note ->
                showCheckInDialog = false
                onCheckIn(note)
            },
            onDismiss = { showCheckInDialog = false },
        )
    }
}

@Composable
private fun DetailHero(data: PersonDetailData) {
    Column(
        modifier = Modifier
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
                data.person.tags.take(3).forEach { tag -> CategoryChip(label = tag) }
            }
        }
        CheckInStatusBadge(
            label = RelativeTime.seenLabel(data.daysSince),
            status = data.status,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun InfoTab(data: PersonDetailData, modifier: Modifier = Modifier) {
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
        if (data.person.notes.isNotBlank()) {
            item(key = "notes") {
                InfoPanel(label = stringResource(R.string.detail_notes)) {
                    Text(data.person.notes, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun CheckInTab(data: PersonDetailData, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
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
            items(data.history, key = { it.id }) { checkIn -> CheckInRow(checkIn) }
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
                label = if (data.isCustomThreshold) {
                    stringResource(R.string.detail_cadence_custom)
                } else {
                    stringResource(R.string.detail_cadence_default)
                },
            )
        }
    }
}

@Composable
private fun CheckInRow(checkIn: CheckIn) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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

@Composable
private fun CheckInDialog(personName: String, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkin_dialog_title, personName)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.checkin_note_hint)) },
                shape = RoundedCornerShape(6.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.ifBlank { null }) }) { Text(stringResource(R.string.checkin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun detailTabs(): List<Int> = listOf(R.string.tab_info, R.string.tab_checkin, R.string.tab_related)

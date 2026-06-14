package com.peoplehub.feature.events.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CategoryChip
import com.peoplehub.core.ui.components.DayCountDisplay
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.feature.events.R
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private val EventDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy • HH:mm")

/** Detail screen for a single event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
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
                        IconButton(onClick = { onEdit(data.event.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.event_detail_edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.event_detail_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        UiStateContent(state = state) { data ->
            EventDetailBody(
                data = data,
                modifier = Modifier.padding(innerPadding),
                onTogglePin = viewModel::onTogglePin,
                onPersonClick = onPersonClick,
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.event_detail_delete_title)) },
            text = { Text(stringResource(R.string.event_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.onDelete()
                }) { Text(stringResource(R.string.event_detail_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EventDetailBody(
    data: EventDetailData,
    onTogglePin: (Boolean) -> Unit,
    onPersonClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        data.event.category?.let { CategoryChip(label = it) }

        Text(
            text = data.event.title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val days = data.signedDays.absoluteValue.toInt()
        if (data.signedDays < 0) {
            DayCountDisplay(number = days, unitLabel = stringResource(R.string.event_days_ago), emphasized = false)
        } else {
            DayCountDisplay(
                number = days,
                unitLabel = stringResource(R.string.event_days),
                prefix = stringResource(R.string.event_in),
                emphasized = true,
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                CapsLabel(text = stringResource(R.string.event_field_date))
                GoldDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = data.event.dateTime.format(EventDateFormatter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        data.event.description?.takeIf { it.isNotBlank() }?.let { description ->
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    CapsLabel(text = stringResource(R.string.event_detail_description))
                    GoldDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = description, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        val personId = data.event.personId
        val personName = data.personName
        if (personId != null && personName != null) {
            GhostButton(
                text = stringResource(R.string.event_detail_linked_person, personName),
                onClick = { onPersonClick(personId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GhostButton(
            text =
                if (data.event.pinnedToWidget) {
                    stringResource(R.string.event_unpin)
                } else {
                    stringResource(R.string.event_pin)
                },
            onClick = { onTogglePin(!data.event.pinnedToWidget) },
            icon = if (data.event.pinnedToWidget) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

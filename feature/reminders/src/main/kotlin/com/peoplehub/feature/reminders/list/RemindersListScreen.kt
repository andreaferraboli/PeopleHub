package com.peoplehub.feature.reminders.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CategoryChip
import com.peoplehub.core.ui.components.DayCountDisplay
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.core.ui.components.WithTooltip
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.feature.reminders.R
import com.peoplehub.feature.reminders.labelRes
import kotlin.math.absoluteValue

/** Stateful entry point for the global reminders list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersListScreen(
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onOpenScience: () -> Unit,
    onPersonClick: (Long) -> Unit,
    viewModel: RemindersListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.brand_wordmark),
                centered = true,
                showLogo = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    TooltipIconButton(
                        icon = Icons.Outlined.Science,
                        description = stringResource(R.string.reminders_why),
                        onClick = onOpenScience,
                    )
                },
            )
        },
        floatingActionButton = {
            WithTooltip(description = stringResource(R.string.reminders_add)) {
                FloatingActionButton(
                    onClick = onAddReminder,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reminders_add))
                }
            }
        },
    ) { innerPadding ->
        RemindersListContent(
            state = state,
            contentPadding = innerPadding,
            onCategoryChange = viewModel::onCategoryChange,
            onReminderClick = onEditReminder,
            onPersonClick = onPersonClick,
            onToggleEnabled = viewModel::onToggleEnabled,
            onDelete = viewModel::onDelete,
        )
    }
}

/** Stateless list layout, suitable for previews. */
@Composable
private fun RemindersListContent(
    state: RemindersScreenState,
    contentPadding: PaddingValues,
    onCategoryChange: (ReminderCategory?) -> Unit,
    onReminderClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
                start = 20.dp,
                end = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = stringResource(R.string.reminders_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.reminders_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "category-filter") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoryOptions(), key = { it?.name ?: "all" }) { option ->
                    TagChip(
                        label =
                            if (option == null) {
                                stringResource(R.string.reminder_filter_all)
                            } else {
                                stringResource(option.labelRes())
                            },
                        selected = state.category == option,
                        onClick = { onCategoryChange(option) },
                    )
                }
            }
        }

        remindersListBody(state.listState, onReminderClick, onPersonClick, onToggleEnabled, onDelete)
    }
}

private fun LazyListScope.remindersListBody(
    listState: UiState<List<ReminderListItem>>,
    onReminderClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    when (listState) {
        UiState.Loading -> item(key = "loading") { StateBox { LoadingView() } }
        UiState.Empty ->
            item(key = "empty") {
                StateBox {
                    EmptyView(
                        title = stringResource(R.string.reminders_empty_title),
                        description = stringResource(R.string.reminders_empty_desc),
                    )
                }
            }
        is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = listState.message) } }
        is UiState.Success ->
            items(listState.data, key = { it.id }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    onClick = { onReminderClick(reminder.id) },
                    onPersonClick = { onPersonClick(reminder.personId) },
                    onToggleEnabled = { onToggleEnabled(reminder.id, !reminder.enabled) },
                    onDelete = { onDelete(reminder.id) },
                )
            }
    }
}

@Composable
private fun StateBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderListItem,
    onClick: () -> Unit,
    onPersonClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CapsLabel(
                    text = reminder.personName,
                    modifier = Modifier.clickable(onClick = onPersonClick),
                )
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!reminder.note.isNullOrBlank()) {
                    Text(
                        text = reminder.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reminder.isDue) {
                    CategoryChip(label = stringResource(R.string.reminder_due), tint = MaterialTheme.colorScheme.primary)
                } else {
                    DayCountDisplay(
                        number = reminder.daysUntil.absoluteValue.toInt(),
                        unitLabel = stringResource(R.string.reminder_days),
                        prefix = stringResource(R.string.reminder_in),
                        emphasized = reminder.enabled,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = reminder.enabled, onCheckedChange = { onToggleEnabled() })
                TooltipIconButton(
                    icon = Icons.Outlined.Delete,
                    description = stringResource(R.string.reminder_delete),
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun categoryOptions(): List<ReminderCategory?> =
    listOf(null, ReminderCategory.LOVE, ReminderCategory.FRIENDSHIP, ReminderCategory.FAMILY, ReminderCategory.CUSTOM)

@Preview(name = "Phone", device = "spec:width=411dp,height=891dp")
@Composable
private fun RemindersListPreview() {
    PeopleHubTheme {
        RemindersListContent(
            state =
                RemindersScreenState(
                    listState =
                        UiState.Success(
                            listOf(
                                ReminderListItem(
                                    1,
                                    1,
                                    "Marco",
                                    "Bring flowers",
                                    "Her favourite are peonies",
                                    ReminderCategory.LOVE,
                                    enabled = true,
                                    daysUntil = 4,
                                ),
                                ReminderListItem(
                                    2,
                                    2,
                                    "Giulia",
                                    "Call to catch up",
                                    null,
                                    ReminderCategory.FRIENDSHIP,
                                    enabled = true,
                                    daysUntil = 0,
                                ),
                            ),
                        ),
                    category = null,
                ),
            contentPadding = PaddingValues(0.dp),
            onCategoryChange = {},
            onReminderClick = {},
            onPersonClick = {},
            onToggleEnabled = { _, _ -> },
            onDelete = {},
        )
    }
}

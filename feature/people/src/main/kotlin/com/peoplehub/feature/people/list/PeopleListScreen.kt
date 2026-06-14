package com.peoplehub.feature.people.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.PeopleSort
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CheckInStatusBadge
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.core.ui.components.WithTooltip
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.util.RelativeTime
import com.peoplehub.feature.people.R
import java.time.format.DateTimeFormatter

private val BirthdayCardFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")

/** Stateful entry point for the people directory ("The Circle"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    onPersonClick: (Long) -> Unit,
    onAddPerson: () -> Unit,
    viewModel: PeopleListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
                if (json != null) viewModel.onImportFileLoaded(json)
            }
        }

    LaunchedEffect(state.importMessage) {
        val message = state.importMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showCustomCheckIn by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    scrollBehavior = scrollBehavior,
                    onClear = viewModel::onClearSelection,
                    onSelectAll = viewModel::onSelectAll,
                    onNotifications = viewModel::onBulkNotifications,
                    onBirthdayOnly = viewModel::onBulkBirthdayOnly,
                    onCustomCheckIn = { showCustomCheckIn = true },
                    onDisableCheckIn = viewModel::onBulkDisableCheckIn,
                )
            } else {
                PeopleHubTopBar(
                    title = stringResource(R.string.brand_wordmark),
                    centered = true,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        SortMenu(current = state.sort, onSortChange = viewModel::onSortChange)
                        TooltipIconButton(
                            icon = Icons.Outlined.FileDownload,
                            description = stringResource(R.string.people_import),
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            if (!state.inSelectionMode) {
                WithTooltip(description = stringResource(R.string.people_add)) {
                    FloatingActionButton(
                        onClick = onAddPerson,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.people_add))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PeopleListContent(
            state = state,
            contentPadding = innerPadding,
            onQueryChange = viewModel::onQueryChange,
            onToggleTag = viewModel::onToggleTag,
            onPersonClick = { id ->
                if (state.inSelectionMode) viewModel.onToggleSelection(id) else onPersonClick(id)
            },
            onPersonLongClick = viewModel::onToggleSelection,
        )
    }

    if (showCustomCheckIn) {
        CustomCheckInDialog(
            onConfirm = { warning, critical ->
                showCustomCheckIn = false
                viewModel.onBulkCustomCheckIn(warning, critical)
            },
            onDismiss = { showCustomCheckIn = false },
        )
    }

    val preview = state.importPreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissImport,
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_message, preview.fullName)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmImport) { Text(stringResource(R.string.action_import)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissImport) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Stateless directory layout, suitable for previews. */
@Composable
private fun PeopleListContent(
    state: PeopleListScreenState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onPersonClick: (Long) -> Unit,
    onPersonLongClick: (Long) -> Unit = {},
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
                    text = stringResource(R.string.people_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.people_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.people_search_hint)) },
                shape = RoundedCornerShape(6.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }

        if (state.allTags.isNotEmpty()) {
            item(key = "tags") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.allTags, key = { it }) { tag ->
                        TagChip(label = tag, selected = tag in state.selectedTags, onClick = { onToggleTag(tag) })
                    }
                }
            }
        }

        peopleListBody(
            state.listState,
            state.sort == PeopleSort.UPCOMING_BIRTHDAY,
            state.selectedIds,
            onPersonClick,
            onPersonLongClick,
        )
    }
}

private fun LazyListScope.peopleListBody(
    listState: UiState<List<PersonListItem>>,
    showBirthday: Boolean,
    selectedIds: Set<Long>,
    onPersonClick: (Long) -> Unit,
    onPersonLongClick: (Long) -> Unit,
) {
    when (listState) {
        UiState.Loading -> item(key = "loading") { StateBox { LoadingView() } }
        UiState.Empty ->
            item(key = "empty") {
                StateBox {
                    EmptyView(
                        title = stringResource(R.string.people_empty_title),
                        description = stringResource(R.string.people_empty_desc),
                    )
                }
            }
        is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = listState.message) } }
        is UiState.Success ->
            items(listState.data, key = { it.id }) { person ->
                PersonCard(
                    person = person,
                    showBirthday = showBirthday,
                    selected = person.id in selectedIds,
                    onClick = { onPersonClick(person.id) },
                    onLongClick = { onPersonLongClick(person.id) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonCard(
    person: PersonListItem,
    showBirthday: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.0f)
    GlassPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                PersonAvatar(initials = person.initials, photoPath = person.photoPath, size = 64.dp)
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (person.primaryTag != null) {
                    CapsLabel(text = person.primaryTag)
                }
                Text(
                    text = person.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                when {
                    person.checkInDisabled ->
                        CapsLabel(
                            text = stringResource(R.string.circle_checkin_off),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    showBirthday && person.nextBirthday != null ->
                        BirthdayLine(date = person.nextBirthday, daysUntil = person.daysUntilBirthday)
                    else ->
                        CheckInStatusBadge(label = RelativeTime.seenLabel(person.daysSince), status = person.status)
                }
            }
        }
    }
}

/** The contextual action bar shown while one or more people are selected for a bulk operation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onNotifications: (Boolean) -> Unit,
    onBirthdayOnly: (Boolean) -> Unit,
    onCustomCheckIn: () -> Unit,
    onDisableCheckIn: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    PeopleHubTopBar(
        title = stringResource(R.string.circle_selected_count, selectedCount),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            TooltipIconButton(
                icon = Icons.Outlined.Close,
                description = stringResource(R.string.circle_selection_clear),
                onClick = onClear,
            )
        },
        actions = {
            TooltipIconButton(
                icon = Icons.Outlined.NotificationsActive,
                description = stringResource(R.string.circle_bulk_notifications_on),
                onClick = { onNotifications(true) },
            )
            TooltipIconButton(
                icon = Icons.Outlined.Schedule,
                description = stringResource(R.string.circle_bulk_custom_checkin),
                onClick = onCustomCheckIn,
            )
            TooltipIconButton(
                icon = Icons.Outlined.MoreVert,
                description = stringResource(R.string.circle_bulk_more),
                onClick = { menuExpanded = true },
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                BulkMenuItem(Icons.Outlined.NotificationsActive, R.string.circle_bulk_notifications_on) {
                    menuExpanded = false
                    onNotifications(true)
                }
                BulkMenuItem(Icons.Outlined.NotificationsOff, R.string.circle_bulk_notifications_off) {
                    menuExpanded = false
                    onNotifications(false)
                }
                BulkMenuItem(Icons.Outlined.Cake, R.string.circle_bulk_birthday_only_on) {
                    menuExpanded = false
                    onBirthdayOnly(true)
                }
                BulkMenuItem(Icons.Outlined.Cake, R.string.circle_bulk_birthday_only_off) {
                    menuExpanded = false
                    onBirthdayOnly(false)
                }
                BulkMenuItem(Icons.Outlined.Schedule, R.string.circle_bulk_custom_checkin) {
                    menuExpanded = false
                    onCustomCheckIn()
                }
                BulkMenuItem(Icons.Outlined.EventBusy, R.string.circle_bulk_disable_checkin) {
                    menuExpanded = false
                    onDisableCheckIn()
                }
                BulkMenuItem(Icons.Outlined.SelectAll, R.string.circle_bulk_select_all) {
                    menuExpanded = false
                    onSelectAll()
                }
            }
        },
    )
}

@Composable
private fun BulkMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** Lets the user pick a warning/critical day cadence to apply to the whole selection. */
@Composable
private fun CustomCheckInDialog(onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var warning by remember { mutableIntStateOf(14) }
    var critical by remember { mutableIntStateOf(30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.circle_custom_checkin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.circle_custom_checkin_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DayStepper(
                    label = stringResource(R.string.edit_threshold_warning),
                    value = warning,
                    onChange = { warning = it.coerceIn(1, 364) },
                )
                DayStepper(
                    label = stringResource(R.string.edit_threshold_critical),
                    value = critical,
                    onChange = { critical = it.coerceIn(2, 365) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(warning, critical) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DayStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipIconButton(
                icon = Icons.Outlined.Remove,
                description = stringResource(R.string.action_remove),
                onClick = { onChange(value - 1) },
            )
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            TooltipIconButton(
                icon = Icons.Filled.Add,
                description = stringResource(R.string.action_add),
                onClick = { onChange(value + 1) },
            )
        }
    }
}

/** A compact "🎁 October 12 · in 5 days" line shown on directory cards under the upcoming-birthday sort. */
@Composable
private fun BirthdayLine(date: java.time.LocalDate, daysUntil: Int?) {
    val suffix =
        when {
            daysUntil == null -> ""
            daysUntil == 0 -> " · ${stringResource(R.string.circle_birthday_today)}"
            else -> " · ${stringResource(R.string.circle_birthday_in_days, daysUntil)}"
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.CardGiftcard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = date.format(BirthdayCardFormatter) + suffix,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenu(current: PeopleSort, onSortChange: (PeopleSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TooltipIconButton(
        icon = Icons.AutoMirrored.Outlined.Sort,
        description = stringResource(R.string.people_sort),
        onClick = { expanded = true },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        sortOptions().forEach { (sort, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                onClick = {
                    onSortChange(sort)
                    expanded = false
                },
                trailingIcon =
                    if (sort == current) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
            )
        }
    }
}

private fun sortOptions(): List<Pair<PeopleSort, Int>> =
    listOf(
        PeopleSort.NAME_ASC to R.string.sort_name,
        PeopleSort.LAST_CHECK_IN to R.string.sort_last_checkin,
        PeopleSort.UPCOMING_BIRTHDAY to R.string.sort_birthday,
    )

@Preview(name = "Phone", device = "spec:width=411dp,height=891dp")
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp")
@Composable
private fun PeopleListPreview() {
    PeopleHubTheme {
        PeopleListContent(
            state =
                PeopleListScreenState(
                    listState =
                        UiState.Success(
                            listOf(
                                PersonListItem(1, "Eleanor Vance", "EV", null, "Family", CheckInStatus.OVERDUE, 40),
                                PersonListItem(2, "Marcus Thorne", "MT", null, "Work", CheckInStatus.FRESH, 2),
                                PersonListItem(3, "Sophia Lin", "SL", null, "Friend", CheckInStatus.DUE, 16),
                            ),
                        ),
                    allTags = listOf("Family", "Work", "Friend"),
                    query = "",
                    selectedTags = setOf("Family"),
                    sort = PeopleSort.NAME_ASC,
                    importPreview = null,
                    importMessage = null,
                ),
            contentPadding = PaddingValues(0.dp),
            onQueryChange = {},
            onToggleTag = {},
            onPersonClick = {},
        )
    }
}

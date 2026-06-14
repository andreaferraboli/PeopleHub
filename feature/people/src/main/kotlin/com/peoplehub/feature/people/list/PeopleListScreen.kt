package com.peoplehub.feature.people.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import java.time.format.DateTimeFormatter
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
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.util.RelativeTime
import com.peoplehub.feature.people.R

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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.brand_wordmark),
                centered = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    SortMenu(current = state.sort, onSortChange = viewModel::onSortChange)
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.people_import))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPerson,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.people_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PeopleListContent(
            state = state,
            contentPadding = innerPadding,
            onQueryChange = viewModel::onQueryChange,
            onToggleTag = viewModel::onToggleTag,
            onPersonClick = onPersonClick,
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
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

        peopleListBody(state.listState, state.sort == PeopleSort.UPCOMING_BIRTHDAY, onPersonClick)
    }
}

private fun LazyListScope.peopleListBody(
    listState: UiState<List<PersonListItem>>,
    showBirthday: Boolean,
    onPersonClick: (Long) -> Unit,
) {
    when (listState) {
        UiState.Loading -> item(key = "loading") { StateBox { LoadingView() } }
        UiState.Empty -> item(key = "empty") {
            StateBox {
                EmptyView(
                    title = stringResource(R.string.people_empty_title),
                    description = stringResource(R.string.people_empty_desc),
                )
            }
        }
        is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = listState.message) } }
        is UiState.Success -> items(listState.data, key = { it.id }) { person ->
            PersonCard(person = person, showBirthday = showBirthday, onClick = { onPersonClick(person.id) })
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
private fun PersonCard(person: PersonListItem, showBirthday: Boolean, onClick: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(initials = person.initials, photoPath = person.photoPath, size = 64.dp)
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
                if (showBirthday && person.nextBirthday != null) {
                    BirthdayLine(date = person.nextBirthday, daysUntil = person.daysUntilBirthday)
                } else {
                    CheckInStatusBadge(label = RelativeTime.seenLabel(person.daysSince), status = person.status)
                }
            }
        }
    }
}

/** A compact "🎁 October 12 · in 5 days" line shown on directory cards under the upcoming-birthday sort. */
@Composable
private fun BirthdayLine(date: java.time.LocalDate, daysUntil: Int?) {
    val suffix = when {
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
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.people_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        sortOptions().forEach { (sort, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                onClick = {
                    onSortChange(sort)
                    expanded = false
                },
                trailingIcon = if (sort == current) {
                    { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
            )
        }
    }
}

private fun sortOptions(): List<Pair<PeopleSort, Int>> = listOf(
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
            state = PeopleListScreenState(
                listState = UiState.Success(
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

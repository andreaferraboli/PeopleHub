package com.peoplehub.feature.people.list

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.PeopleSort
import com.peoplehub.core.ui.components.AccentFab
import com.peoplehub.core.ui.components.CheckInStatusBadge
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.LocalNavBarClearance
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.SceneArt
import com.peoplehub.core.ui.components.ScreenHero
import com.peoplehub.core.ui.components.SearchField
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.StepperRow
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.core.ui.theme.statusColorsFor
import com.peoplehub.core.ui.util.RelativeTime
import com.peoplehub.feature.people.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val BirthdayCardFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")

/** Entrance cascade: the hero is index 0, search 1, tags 2, then the first cards. */
private const val FIRST_CARD_RISE_INDEX = 3

/** Only the first screenful rises in; deeper rows would replay it on every scroll. */
private const val MAX_RISE_INDEX = 8

/** Stateful entry point for the people directory ("The Circle"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    onPersonClick: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onRecordMeetup: () -> Unit,
    viewModel: PeopleListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val navClearance = LocalNavBarClearance.current

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

    VellutoBackground(secondGlow = true) {
        Scaffold(
            // Only while the selection bar is on screen: with no top app bar laid out, the
            // scroll behaviour's offset limit stays at -Float.MAX_VALUE and its nested-scroll
            // connection eats every upward drag, so the list could never scroll.
            modifier =
                if (state.inSelectionMode) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            containerColor = Color.Transparent,
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
                }
            },
            floatingActionButton = {
                if (!state.inSelectionMode) {
                    AccentFab(
                        icon = VellutoIcons.UserPlus,
                        description = stringResource(R.string.people_add),
                        text = stringResource(R.string.people_add),
                        onClick = onAddPerson,
                        modifier = Modifier.padding(bottom = navClearance),
                    )
                }
            },
            snackbarHost = {
                // With the FAB shown the snackbar already sits above it; without it, clear the nav bar.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = if (state.inSelectionMode) Modifier.padding(bottom = navClearance) else Modifier,
                )
            },
        ) { innerPadding ->
            PeopleListContent(
                state = state,
                contentPadding = innerPadding,
                onQueryChange = viewModel::onQueryChange,
                onToggleTag = viewModel::onToggleTag,
                onSortChange = viewModel::onSortChange,
                onRecordMeetup = onRecordMeetup,
                onImport = { importLauncher.launch(arrayOf("application/json")) },
                onPersonClick = { id ->
                    if (state.inSelectionMode) viewModel.onToggleSelection(id) else onPersonClick(id)
                },
                onPersonLongClick = viewModel::onToggleSelection,
            )
        }
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
            containerColor = PeopleHubTheme.colors.surface,
            titleContentColor = PeopleHubTheme.colors.text,
            textContentColor = PeopleHubTheme.colors.text2,
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_message, preview.fullName)) },
            confirmButton = {
                VellutoButton(
                    text = stringResource(R.string.action_import),
                    onClick = viewModel::onConfirmImport,
                    height = PeopleHubTheme.spacing.touchTarget,
                )
            },
            dismissButton = {
                VellutoButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = viewModel::onDismissImport,
                    style = VellutoButtonStyle.Ghost,
                )
            },
        )
    }
}

/**
 * Stateless directory layout ("La *Cerchia*"): hero with the header actions and the floating rings,
 * glass search with the sort chip, tag filters and the people cards. Internal so previews and
 * screenshot tests can render it with sample data.
 */
@Composable
internal fun PeopleListContent(
    state: PeopleListScreenState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSortChange: (PeopleSort) -> Unit,
    onRecordMeetup: () -> Unit,
    onImport: () -> Unit,
    onPersonClick: (Long) -> Unit,
    onPersonLongClick: (Long) -> Unit = {},
) {
    val spacing = PeopleHubTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + LocalNavBarClearance.current + 60.dp,
            ),
    ) {
        item(key = "hero") {
            ScreenHero(
                title = stringResource(R.string.people_title),
                eyebrow = stringResource(R.string.brand_wordmark),
                subtitle = stringResource(R.string.people_subtitle),
                scene = SceneArt.Rings,
                subtitleMaxWidth = 250.dp,
                actions = {
                    GlassIconButton(
                        icon = VellutoIcons.CalendarPlus,
                        description = stringResource(R.string.group_meetup_title),
                        onClick = onRecordMeetup,
                    )
                    GlassIconButton(
                        icon = VellutoIcons.Download,
                        description = stringResource(R.string.people_import),
                        onClick = onImport,
                    )
                    SortMenu(current = state.sort, onSortChange = onSortChange) { open ->
                        GlassIconButton(
                            icon = VellutoIcons.ArrowUpDown,
                            description = stringResource(R.string.people_sort),
                            onClick = open,
                        )
                    }
                },
            )
        }

        item(key = "search") {
            SearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.people_search_hint),
                modifier =
                    Modifier
                        .padding(start = spacing.gutter, end = spacing.gutter, top = 22.dp)
                        .riseIn(1),
                trailing = {
                    SortMenu(current = state.sort, onSortChange = onSortChange) { open ->
                        SortChip(label = stringResource(sortLabelRes(state.sort)), onClick = open)
                    }
                },
            )
        }

        if (state.allTags.isNotEmpty()) {
            item(key = "tags") {
                LazyRow(
                    modifier = Modifier.padding(top = spacing.ml).riseIn(2),
                    contentPadding = PaddingValues(horizontal = spacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    items(state.allTags, key = { it }) { tag ->
                        TagChip(label = tag, selected = tag in state.selectedTags, onClick = { onToggleTag(tag) })
                    }
                }
            }
        }

        item(key = "gap") { Box(Modifier.height(spacing.l)) }

        peopleListBody(
            listState = state.listState,
            showBirthday = state.sort == PeopleSort.UPCOMING_BIRTHDAY,
            selectedIds = state.selectedIds,
            onPersonClick = onPersonClick,
            onPersonLongClick = onPersonLongClick,
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
                        icon = VellutoIcons.Users,
                    )
                }
            }
        is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = listState.message) } }
        is UiState.Success ->
            itemsIndexed(listState.data, key = { _, person -> person.id }) { index, person ->
                val rise = FIRST_CARD_RISE_INDEX + index
                PersonCard(
                    person = person,
                    showBirthday = showBirthday,
                    selectionMode = selectedIds.isNotEmpty(),
                    selected = person.id in selectedIds,
                    onClick = { onPersonClick(person.id) },
                    onLongClick = { onPersonLongClick(person.id) },
                    modifier =
                        Modifier
                            .padding(horizontal = PeopleHubTheme.spacing.gutter)
                            .padding(bottom = PeopleHubTheme.spacing.sm)
                            .then(if (rise <= MAX_RISE_INDEX) Modifier.riseIn(rise) else Modifier),
                )
            }
    }
}

@Composable
private fun StateBox(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(horizontal = PeopleHubTheme.spacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * The sort chip inside the search pill: a surface2 pill with the ArrowUpDown glyph and the current
 * sort, inside a full 48dp touch target.
 */
@Composable
private fun SortChip(
    label: String,
    onClick: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val description = stringResource(R.string.people_sort)
    Box(
        modifier =
            Modifier
                .heightIn(min = PeopleHubTheme.spacing.touchTarget)
                .widthIn(max = 168.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .height(32.dp)
                    .background(c.surface2, CircleShape)
                    .lightEdge(CircleShape, Color.Transparent, c.line)
                    .padding(horizontal = PeopleHubTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xs),
        ) {
            Icon(VellutoIcons.ArrowUpDown, contentDescription = null, tint = c.text2, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = c.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonCard(
    person: PersonListItem,
    showBirthday: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val isSelected = selected
    val ring = if (person.checkInDisabled) c.track else statusColorsFor(person.status).ring
    val ringGlow =
        if (!person.checkInDisabled && person.status == CheckInStatus.OVERDUE) c.errSoft else Color.Transparent
    GlassPanel(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.lightEdge(VellutoShapes.Card, Color.Transparent, c.accentEdge, 2.dp) else Modifier),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .then(if (selectionMode) Modifier.semantics { this.selected = isSelected } else Modifier)
                    .padding(start = spacing.ml, top = spacing.ml, bottom = spacing.ml, end = spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.ml),
        ) {
            PersonAvatar(
                initials = person.initials,
                photoPath = person.photoPath,
                size = 58.dp,
                ringColor = ring,
                ringGlow = ringGlow,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = person.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (person.primaryTag != null) {
                    Text(
                        text = person.primaryTag,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PersonStatusPill(person = person, showBirthday = showBirthday)
            }
            CardTrailingMark(
                selectionMode = selectionMode,
                selected = selected,
            )
        }
    }
}

/** The one pill under a name: family, tracking off, the upcoming birthday or the check-in recency. */
@Composable
private fun PersonStatusPill(
    person: PersonListItem,
    showBirthday: Boolean,
) {
    val c = PeopleHubTheme.colors
    when {
        person.isFamily ->
            StatusPill(
                label = stringResource(R.string.label_family),
                content = c.accentOnSoft,
                container = c.accentSoft,
                icon = VellutoIcons.Heart,
                border = c.accentEdge,
            )
        person.checkInDisabled ->
            StatusPill(
                label = stringResource(R.string.circle_checkin_off),
                content = c.text2,
                container = c.surface2,
                icon = VellutoIcons.EyeOff,
                border = c.line,
            )
        showBirthday && person.nextBirthday != null ->
            BirthdayPill(date = person.nextBirthday, daysUntil = person.daysUntilBirthday)
        else ->
            CheckInStatusBadge(label = RelativeTime.seenLabel(person.daysSince), status = person.status)
    }
}

/**
 * The mark at the end of a card. In selection mode it is the round selection check (solid accent
 * when selected). Otherwise it is a bare "open" chevron with no button styling: the list offers no
 * per-row action, the whole card is the target. Decorative in both cases (no semantics).
 */
@Composable
private fun CardTrailingMark(
    selectionMode: Boolean,
    selected: Boolean,
) {
    val c = PeopleHubTheme.colors
    val look =
        when {
            selectionMode && selected -> MarkLook(c.accent, Color.Transparent, c.onAccent, VellutoIcons.Check)
            selectionMode -> MarkLook(Color.Transparent, c.line, c.text2, null)
            else -> MarkLook(Color.Transparent, Color.Transparent, c.text2, VellutoIcons.ChevronRight)
        }
    Box(
        modifier =
            Modifier
                .size(if (selectionMode) PeopleHubTheme.spacing.touchTarget else CHEVRON_BOX)
                .clearAndSetSemantics {}
                .then(
                    if (selectionMode && selected) {
                        Modifier.softShadow(c.glowSoft, CircleShape, VellutoElevation.GlowSmall)
                    } else {
                        Modifier
                    },
                ).background(look.fill, CircleShape)
                .lightEdge(CircleShape, Color.Transparent, look.edge),
        contentAlignment = Alignment.Center,
    ) {
        if (look.icon != null) {
            Icon(look.icon, contentDescription = null, tint = look.tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** Size of the bare chevron outside selection mode (it is not a touch target). */
private val CHEVRON_BOX = 24.dp

/** Fill, hairline, glyph colour and glyph of a [CardTrailingMark]. */
private data class MarkLook(
    val fill: Color,
    val edge: Color,
    val tint: Color,
    val icon: ImageVector?,
)

/**
 * The contextual action bar shown while one or more people are selected for a bulk operation:
 * serif count title, glass round actions, a Velluto-dressed overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    scrollBehavior: TopAppBarScrollBehavior,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onNotifications: (Boolean) -> Unit,
    onBirthdayOnly: (Boolean) -> Unit,
    onCustomCheckIn: () -> Unit,
    onDisableCheckIn: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val spacing = PeopleHubTheme.spacing
    PeopleHubTopBar(
        title = stringResource(R.string.circle_selected_count, selectedCount),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            GlassIconButton(
                icon = VellutoIcons.X,
                description = stringResource(R.string.circle_selection_clear),
                onClick = onClear,
                modifier = Modifier.padding(start = spacing.s, end = spacing.xs),
            )
        },
        actions = {
            GlassIconButton(
                icon = VellutoIcons.BellRing,
                description = stringResource(R.string.circle_bulk_notifications_on),
                onClick = { onNotifications(true) },
                modifier = Modifier.padding(end = spacing.s),
            )
            GlassIconButton(
                icon = VellutoIcons.Clock,
                description = stringResource(R.string.circle_bulk_custom_checkin),
                onClick = onCustomCheckIn,
                modifier = Modifier.padding(end = spacing.s),
            )
            Box {
                GlassIconButton(
                    icon = VellutoIcons.MoreVertical,
                    description = stringResource(R.string.circle_bulk_more),
                    onClick = { menuExpanded = true },
                    modifier = Modifier.padding(end = spacing.s),
                )
                VellutoMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    BulkMenuItem(VellutoIcons.BellRing, R.string.circle_bulk_notifications_on) {
                        menuExpanded = false
                        onNotifications(true)
                    }
                    BulkMenuItem(VellutoIcons.BellOff, R.string.circle_bulk_notifications_off) {
                        menuExpanded = false
                        onNotifications(false)
                    }
                    BulkMenuItem(VellutoIcons.Cake, R.string.circle_bulk_birthday_only_on) {
                        menuExpanded = false
                        onBirthdayOnly(true)
                    }
                    BulkMenuItem(VellutoIcons.Cake, R.string.circle_bulk_birthday_only_off) {
                        menuExpanded = false
                        onBirthdayOnly(false)
                    }
                    BulkMenuItem(VellutoIcons.Clock, R.string.circle_bulk_custom_checkin) {
                        menuExpanded = false
                        onCustomCheckIn()
                    }
                    BulkMenuItem(VellutoIcons.CalendarX, R.string.circle_bulk_disable_checkin) {
                        menuExpanded = false
                        onDisableCheckIn()
                    }
                    BulkMenuItem(VellutoIcons.ListChecks, R.string.circle_bulk_select_all) {
                        menuExpanded = false
                        onSelectAll()
                    }
                }
            }
        },
    )
}

/** A Material dropdown dressed in Velluto: opaque surface, inner radius, hairline, no tonal tint. */
@Composable
private fun VellutoMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = PeopleHubTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = VellutoShapes.Inner,
        containerColor = c.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, c.line),
    ) {
        content()
    }
}

@Composable
private fun BulkMenuItem(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = c.text, leadingIconColor = c.text2),
    )
}

/** Lets the user pick a warning/critical day cadence to apply to the whole selection. */
@Composable
private fun CustomCheckInDialog(
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var warning by remember { mutableIntStateOf(14) }
    var critical by remember { mutableIntStateOf(30) }
    val c = PeopleHubTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.text2,
        title = { Text(stringResource(R.string.circle_custom_checkin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.sm)) {
                Text(
                    text = stringResource(R.string.circle_custom_checkin_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text2,
                )
                DayStepper(
                    label = stringResource(R.string.edit_threshold_warning),
                    value = warning,
                    dotColor = c.warn,
                    onChange = { warning = it.coerceIn(1, 364) },
                )
                DayStepper(
                    label = stringResource(R.string.edit_threshold_critical),
                    value = critical,
                    dotColor = c.err,
                    onChange = { critical = it.coerceIn(2, 365) },
                )
            }
        },
        confirmButton = {
            VellutoButton(
                text = stringResource(R.string.action_confirm),
                onClick = { onConfirm(warning, critical) },
                height = PeopleHubTheme.spacing.touchTarget,
            )
        },
        dismissButton = {
            VellutoButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = VellutoButtonStyle.Ghost,
            )
        },
    )
}

@Composable
private fun DayStepper(
    label: String,
    value: Int,
    dotColor: Color,
    onChange: (Int) -> Unit,
) {
    StepperRow(
        label = label,
        value = value.toString(),
        onDecrement = { onChange(value - 1) },
        onIncrement = { onChange(value + 1) },
        decrementLabel = stringResource(R.string.action_remove),
        incrementLabel = stringResource(R.string.action_add),
        minusIcon = VellutoIcons.Minus,
        plusIcon = VellutoIcons.Plus,
        dotColor = dotColor,
    )
}

/**
 * The upcoming-birthday pill shown on directory cards under the upcoming-birthday sort
 * ("October 12 · in 5 days"): solid accent on the day itself, soft accent before.
 */
@Composable
private fun BirthdayPill(
    date: LocalDate,
    daysUntil: Int?,
) {
    val c = PeopleHubTheme.colors
    val suffix =
        when {
            daysUntil == null -> ""
            daysUntil == 0 -> " · ${stringResource(R.string.circle_birthday_today)}"
            else -> " · ${stringResource(R.string.circle_birthday_in_days, daysUntil)}"
        }
    val today = daysUntil == 0
    StatusPill(
        label = date.format(BirthdayCardFormatter) + suffix,
        content = if (today) c.onAccent else c.accentOnSoft,
        container = if (today) c.accent else c.accentSoft,
        icon = VellutoIcons.Gift,
        border = if (today) Color.Transparent else c.accentEdge,
        modifier = if (today) Modifier.softShadow(c.glowSoft, CircleShape, VellutoElevation.GlowSmall) else Modifier,
    )
}

/**
 * Wraps a sort trigger ([anchor], handed the "open" action) with the existing sort menu, anchored to
 * it. Used by the header sort button and by the sort chip in the search pill.
 */
@Composable
private fun SortMenu(
    current: PeopleSort,
    onSortChange: (PeopleSort) -> Unit,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val c = PeopleHubTheme.colors
    Box {
        anchor { expanded = true }
        VellutoMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sortOptions().forEach { (sort, labelRes) ->
                val isCurrent = sort == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(labelRes),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                        )
                    },
                    onClick = {
                        onSortChange(sort)
                        expanded = false
                    },
                    trailingIcon =
                        if (isCurrent) {
                            { Icon(VellutoIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    colors = MenuDefaults.itemColors(textColor = c.text, trailingIconColor = c.accentTextSurface),
                )
            }
        }
    }
}

private fun sortOptions(): List<Pair<PeopleSort, Int>> =
    listOf(
        PeopleSort.NAME_ASC to R.string.sort_name,
        PeopleSort.LAST_CHECK_IN to R.string.sort_last_checkin,
        PeopleSort.UPCOMING_BIRTHDAY to R.string.sort_birthday,
    )

private fun sortLabelRes(sort: PeopleSort): Int = sortOptions().firstOrNull { it.first == sort }?.second ?: R.string.people_sort

private fun previewState(): PeopleListScreenState =
    PeopleListScreenState(
        listState =
            UiState.Success(
                listOf(
                    PersonListItem(1, "Francesca Lombardi", "FL", null, "Amici", CheckInStatus.OVERDUE, 42),
                    PersonListItem(2, "Matteo Galli", "MG", null, "Università", CheckInStatus.OVERDUE, 37),
                    PersonListItem(3, "Chiara Conti", "CC", null, "Lavoro", CheckInStatus.DUE, 18),
                    PersonListItem(4, "Luca Moretti", "LM", null, "Calcetto", CheckInStatus.FRESH, 2),
                    PersonListItem(5, "Marta Colombo", "MC", null, "Coppia", CheckInStatus.FRESH, 0),
                    PersonListItem(6, "Davide Ricci", "DR", null, "Amici", CheckInStatus.NEVER, null, checkInDisabled = true),
                    PersonListItem(7, "Elena Ferraboli", "EF", null, "Famiglia", CheckInStatus.FRESH, 5, isFamily = true),
                ),
            ),
        allTags = listOf("Famiglia", "Amici", "Università", "Lavoro", "Calcetto", "Coppia"),
        query = "",
        selectedTags = setOf("Amici"),
        sort = PeopleSort.LAST_CHECK_IN,
        importPreview = null,
        importMessage = null,
    )

@Composable
private fun PeopleListPreviewFrame(dark: Boolean) {
    PeopleHubTheme(darkTheme = dark) {
        VellutoBackground(secondGlow = true) {
            PeopleListContent(
                state = previewState(),
                contentPadding = PaddingValues(0.dp),
                onQueryChange = {},
                onToggleTag = {},
                onSortChange = {},
                onRecordMeetup = {},
                onImport = {},
                onPersonClick = {},
            )
            AccentFab(
                icon = VellutoIcons.UserPlus,
                description = stringResource(R.string.people_add),
                text = stringResource(R.string.people_add),
                onClick = {},
                modifier = Modifier.align(Alignment.BottomEnd).padding(PeopleHubTheme.spacing.gutter),
            )
        }
    }
}

@Preview(name = "Cerchia, chiaro", widthDp = 390, heightDp = 844, locale = "it")
@Composable
private fun PeopleListLightPreview() {
    PeopleListPreviewFrame(dark = false)
}

@Preview(name = "Cerchia, scuro", widthDp = 390, heightDp = 844, locale = "it", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleListDarkPreview() {
    PeopleListPreviewFrame(dark = true)
}

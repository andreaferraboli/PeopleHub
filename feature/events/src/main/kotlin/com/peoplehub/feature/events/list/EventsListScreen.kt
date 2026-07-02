package com.peoplehub.feature.events.list

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.peoplehub.core.domain.model.EventTimeFilter
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
import com.peoplehub.feature.events.R
import java.io.File
import kotlin.math.absoluteValue

/** Stateful entry point for the events timeline ("Timeline"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListScreen(
    onEventClick: (Long) -> Unit,
    onAddEvent: () -> Unit,
    viewModel: EventsListViewModel = hiltViewModel(),
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
            )
        },
        floatingActionButton = {
            WithTooltip(description = stringResource(R.string.events_add)) {
                FloatingActionButton(
                    onClick = onAddEvent,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.events_add))
                }
            }
        },
    ) { innerPadding ->
        EventsListContent(
            state = state,
            contentPadding = innerPadding,
            onTimeFilterChange = viewModel::onTimeFilterChange,
            onCategoryChange = viewModel::onCategoryChange,
            onEventClick = onEventClick,
            onTogglePin = viewModel::onTogglePin,
        )
    }
}

/** Stateless timeline layout, suitable for previews. */
@Composable
private fun EventsListContent(
    state: EventsListScreenState,
    contentPadding: PaddingValues,
    onTimeFilterChange: (EventTimeFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onEventClick: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
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
                    text = stringResource(R.string.events_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.events_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "time-filter") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(timeFilterOptions(), key = { it.first.name }) { (filter, labelRes) ->
                    TagChip(
                        label = stringResource(labelRes),
                        selected = state.timeFilter == filter,
                        onClick = { onTimeFilterChange(filter) },
                    )
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            item(key = "category-filter") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories, key = { it }) { cat ->
                        TagChip(
                            label = cat,
                            selected = state.category == cat,
                            onClick = { onCategoryChange(cat) },
                        )
                    }
                }
            }
        }

        eventsListBody(state.listState, onEventClick, onTogglePin)
    }
}

private fun LazyListScope.eventsListBody(
    listState: UiState<List<EventListItem>>,
    onEventClick: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
) {
    when (listState) {
        UiState.Loading -> item(key = "loading") { StateBox { LoadingView() } }
        UiState.Empty ->
            item(key = "empty") {
                StateBox {
                    EmptyView(
                        title = stringResource(R.string.events_empty_title),
                        description = stringResource(R.string.events_empty_desc),
                    )
                }
            }
        is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = listState.message) } }
        is UiState.Success ->
            items(listState.data, key = { it.id }) { event ->
                EventCard(
                    event = event,
                    onClick = { onEventClick(event.id) },
                    onTogglePin = { onTogglePin(event.id, !event.pinned) },
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
private fun EventCard(event: EventListItem, onClick: () -> Unit, onTogglePin: () -> Unit) {
    val hasBackground = event.backgroundImagePath != null
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (hasBackground) {
                AsyncImage(
                    model = File(event.backgroundImagePath!!),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // Scrim so the gold/serif text stays legible over any photo.
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.65f)),
                                ),
                            ),
                )
            }
            val contentColor = if (hasBackground) Color.White else MaterialTheme.colorScheme.onSurface
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (event.category != null) {
                        CategoryChip(label = event.category)
                    }
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = contentColor,
                    )
                    val days = event.signedDays.absoluteValue.toInt()
                    if (event.isPast) {
                        DayCountDisplay(
                            number = days,
                            unitLabel = stringResource(R.string.event_days_ago),
                            emphasized = false,
                        )
                    } else {
                        DayCountDisplay(
                            number = days,
                            unitLabel = stringResource(R.string.event_days),
                            prefix = stringResource(R.string.event_in),
                            emphasized = true,
                        )
                    }
                }
                TooltipIconButton(
                    icon = if (event.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    description =
                        if (event.pinned) {
                            stringResource(R.string.event_unpin)
                        } else {
                            stringResource(R.string.event_pin)
                        },
                    onClick = onTogglePin,
                    tint = if (event.pinned) MaterialTheme.colorScheme.primary else contentColor,
                )
            }
        }
    }
}

private fun timeFilterOptions(): List<Pair<EventTimeFilter, Int>> =
    listOf(
        EventTimeFilter.ALL to R.string.filter_all,
        EventTimeFilter.UPCOMING to R.string.filter_upcoming,
        EventTimeFilter.PAST to R.string.filter_past,
    )

@Preview(name = "Phone", device = "spec:width=411dp,height=891dp")
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp")
@Composable
private fun EventsListPreview() {
    PeopleHubTheme {
        EventsListContent(
            state =
                EventsListScreenState(
                    listState =
                        UiState.Success(
                            listOf(
                                EventListItem(1, "Metropolitan Opera Opening", "Gala", 14, isPast = false, pinned = true),
                                EventListItem(2, "Alpine Solitude", "Retreat", 42, isPast = false, pinned = false),
                                EventListItem(3, "Venice Biennale Preview", "Exhibition", -5, isPast = true, pinned = false),
                            ),
                        ),
                    categories = listOf("Gala", "Retreat", "Exhibition"),
                    timeFilter = EventTimeFilter.ALL,
                    category = null,
                ),
            contentPadding = PaddingValues(0.dp),
            onTimeFilterChange = {},
            onCategoryChange = {},
            onEventClick = {},
            onTogglePin = { _, _ -> },
        )
    }
}

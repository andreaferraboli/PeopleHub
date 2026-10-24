package com.peoplehub.feature.events.list

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.EventTimeFilter
import com.peoplehub.core.ui.components.AccentFab
import com.peoplehub.core.ui.components.ChipSelectedStyle
import com.peoplehub.core.ui.components.CoverCountdown
import com.peoplehub.core.ui.components.CoverPin
import com.peoplehub.core.ui.components.CoverScene
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.EventCover
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.LocalNavBarClearance
import com.peoplehub.core.ui.components.SceneArt
import com.peoplehub.core.ui.components.ScreenHero
import com.peoplehub.core.ui.components.SectionHeader
import com.peoplehub.core.ui.components.SegmentedPill
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.coverSceneFor
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.feature.events.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

/** Stateful entry point for the events timeline ("Cronologia"). */
@Composable
fun EventsListScreen(
    onEventClick: (Long) -> Unit,
    onAddEvent: () -> Unit,
    viewModel: EventsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                AccentFab(
                    icon = VellutoIcons.Plus,
                    description = stringResource(R.string.events_add),
                    onClick = onAddEvent,
                    modifier = Modifier.padding(bottom = LocalNavBarClearance.current),
                )
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
}

/**
 * Stateless timeline layout: hero, period switch, category chips, then the events as covers split
 * into "upcoming" and "archive". [today] anchors the date line of each cover (the list item carries
 * the signed day count from today).
 */
@Composable
internal fun EventsListContent(
    state: EventsListScreenState,
    contentPadding: PaddingValues,
    onTimeFilterChange: (EventTimeFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onEventClick: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
    today: LocalDate = rememberToday(),
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
        item(key = "hero") { EventsHero() }

        item(key = "time-filter") {
            val options = timeFilterOptions()
            SegmentedPill(
                options = options.map { stringResource(it.second) },
                selectedIndex = options.indexOfFirst { it.first == state.timeFilter }.coerceAtLeast(0),
                onSelect = { index -> onTimeFilterChange(options[index].first) },
                modifier =
                    Modifier
                        .padding(start = spacing.gutter, end = spacing.gutter, top = 24.dp)
                        .riseIn(1),
            )
        }

        if (state.categories.isNotEmpty()) {
            item(key = "category-filter") {
                CategoryFilterRow(
                    categories = state.categories,
                    selected = state.category,
                    onCategoryChange = onCategoryChange,
                    modifier = Modifier.padding(top = spacing.m).riseIn(2),
                )
            }
        }

        eventsListBody(state.listState, today, onEventClick, onTogglePin)
    }
}

@Composable
private fun rememberToday(): LocalDate = remember { LocalDate.now() }

@Composable
private fun EventsHero() {
    val c = PeopleHubTheme.colors
    ScreenHero(
        title = stringResource(R.string.events_title),
        eyebrow = stringResource(R.string.brand_wordmark),
        scene = SceneArt.Pin,
        sceneOffsetX = (-14).dp,
        sceneOffsetY = 34.dp,
    ) {
        Text(
            text = stringResource(R.string.events_subtitle),
            style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
            color = c.text2,
            modifier = Modifier.widthIn(max = 240.dp),
        )
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String?,
    onCategoryChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        item(key = "all") {
            TagChip(
                label = stringResource(R.string.filter_all),
                selected = selected == null,
                onClick = { onCategoryChange(null) },
                dot = c.accent,
                selectedStyle = ChipSelectedStyle.Soft,
            )
        }
        items(categories, key = { it }) { cat ->
            TagChip(
                label = cat,
                selected = selected == cat,
                onClick = { onCategoryChange(cat) },
                dot = categoryDot(cat),
                selectedStyle = ChipSelectedStyle.Soft,
            )
        }
    }
}

private fun LazyListScope.eventsListBody(
    listState: UiState<List<EventListItem>>,
    today: LocalDate,
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
                        icon = VellutoIcons.CalendarX,
                    )
                }
            }
        is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = listState.message) } }
        is UiState.Success -> {
            val upcoming = listState.data.filterNot { it.isPast }
            val archive = listState.data.filter { it.isPast }
            eventSection(
                key = "upcoming",
                titleRes = R.string.events_section_upcoming,
                events = upcoming,
                firstIndex = FIRST_COVER_INDEX,
                today = today,
                topPadding = 28.dp,
                onEventClick = onEventClick,
                onTogglePin = onTogglePin,
            )
            eventSection(
                key = "archive",
                titleRes = R.string.events_section_archive,
                events = archive,
                firstIndex = FIRST_COVER_INDEX + upcoming.size,
                today = today,
                topPadding = if (upcoming.isEmpty()) 28.dp else 34.dp,
                onEventClick = onEventClick,
                onTogglePin = onTogglePin,
            )
        }
    }
}

private fun LazyListScope.eventSection(
    key: String,
    titleRes: Int,
    events: List<EventListItem>,
    firstIndex: Int,
    today: LocalDate,
    topPadding: Dp,
    onEventClick: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
) {
    if (events.isEmpty()) return
    item(key = "header-$key") {
        val spacing = PeopleHubTheme.spacing
        SectionHeader(
            title = stringResource(titleRes),
            count = events.size.toString(),
            fadeLine = true,
            modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = topPadding),
        )
    }
    itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
        val spacing = PeopleHubTheme.spacing
        val lead = !event.isPast && index == 0
        val rise = firstIndex + index
        EventListCover(
            event = event,
            today = today,
            lead = lead,
            onClick = { onEventClick(event.id) },
            onTogglePin = { onTogglePin(event.id, !event.pinned) },
            modifier =
                Modifier
                    .padding(start = spacing.gutter, end = spacing.gutter, top = if (index == 0) spacing.s else spacing.ml)
                    .then(if (rise <= LAST_RISE_INDEX) Modifier.riseIn(rise) else Modifier),
        )
    }
}

@Composable
private fun StateBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

/** One event as a cover. [lead] is the next upcoming event: taller, highlighted, slowly drifting. */
@Composable
private fun EventListCover(
    event: EventListItem,
    today: LocalDate,
    lead: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = event.signedDays.absoluteValue.toInt()
    val countdown =
        if (event.isPast) {
            CoverCountdown(number = days, unit = stringResource(R.string.event_days_ago))
        } else {
            CoverCountdown(number = days, unit = stringResource(R.string.event_days), prefix = stringResource(R.string.event_in))
        }
    val pinLabel = if (event.pinned) stringResource(R.string.event_unpin) else stringResource(R.string.event_pin)
    EventCover(
        title = event.title,
        dateLine = rememberDateLine(today, event.signedDays),
        onClick = onClick,
        modifier = modifier,
        category = event.category,
        categoryDot = event.category?.let(::categoryDot),
        countdown = countdown,
        imagePath = event.backgroundImagePath,
        scene = coverSceneFor(event.category, event.title),
        pin = CoverPin(pinned = event.pinned, label = pinLabel, onToggle = onTogglePin),
        height =
            when {
                lead -> 224.dp
                event.isPast -> 196.dp
                else -> 210.dp
            },
        past = event.isPast,
        highlighted = lead,
        drift = lead,
    )
}

/** "Ven 6 novembre" in the user's locale (with the year when it is not the current one). */
@Composable
private fun rememberDateLine(today: LocalDate, signedDays: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(today, signedDays, locale) {
        val date = today.plusDays(signedDays)
        val skeleton = if (date.year == today.year) DATE_SKELETON else DATE_SKELETON_WITH_YEAR
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        DateTimeFormatter.ofPattern(pattern, locale).format(date).replaceFirstChar { it.titlecase(locale) }
    }
}

/**
 * A stable dot colour for a free-form category: the hue of the cover scene its keyword picks
 * (travel, milestone, dinner, wedding, anniversary, home), as in the Eventi mockup's chip row.
 * Unknown categories get a stable pick from the same palette (by hash, via [coverSceneFor]).
 */
private fun categoryDot(category: String): Color =
    when (coverSceneFor(category, category)) {
        CoverScene.Travel -> DotTravel
        CoverScene.Milestone -> DotMilestone
        CoverScene.Dinner -> DotDinner
        CoverScene.Wedding -> DotWedding
        CoverScene.Anniversary -> DotAnniversary
        CoverScene.Home -> DotHome
    }

// Category dot hues from the Eventi mockup (decorative, never text: no contrast requirement).
private val DotTravel = Color(0xFFFF9A5A)
private val DotMilestone = Color(0xFF8FA8FF)
private val DotDinner = Color(0xFFFF6F61)
private val DotWedding = Color(0xFF5FC9D4)
private val DotAnniversary = Color(0xFFFF8FA8)
private val DotHome = Color(0xFFD9B27A)

private const val DATE_SKELETON = "EEEdMMMM"
private const val DATE_SKELETON_WITH_YEAR = "EEEdMMMMy"

/** Entrance cascade: hero 0, period switch 1, categories 2, then the first covers up to 8. */
private const val FIRST_COVER_INDEX = 3
private const val LAST_RISE_INDEX = 8

private fun timeFilterOptions(): List<Pair<EventTimeFilter, Int>> =
    listOf(
        EventTimeFilter.ALL to R.string.filter_all,
        EventTimeFilter.UPCOMING to R.string.filter_upcoming,
        EventTimeFilter.PAST to R.string.filter_past,
    )

private val PreviewState =
    EventsListScreenState(
        listState =
            UiState.Success(
                listOf(
                    EventListItem(4, "Le chiavi di casa nuova", "Casa", -133, isPast = true, pinned = false),
                    EventListItem(5, "Tre anni con Marta", "Anniversario", -21, isPast = true, pinned = false),
                    EventListItem(6, "Matrimonio di Giacomo e Irene", "Matrimonio", -13, isPast = true, pinned = false),
                    EventListItem(1, "Weekend a Lisbona", "Viaggio", 14, isPast = false, pinned = true),
                    EventListItem(2, "Laurea di Matteo", "Traguardo", 28, isPast = false, pinned = false),
                    EventListItem(3, "Cena di Natale del calcetto", "Cena", 56, isPast = false, pinned = false),
                ),
            ),
        categories = listOf("Anniversario", "Casa", "Cena", "Matrimonio", "Traguardo", "Viaggio"),
        timeFilter = EventTimeFilter.ALL,
        category = null,
    )

@Composable
private fun EventsListPreviewBody(dark: Boolean) {
    PeopleHubTheme(darkTheme = dark) {
        VellutoBackground {
            EventsListContent(
                state = PreviewState,
                contentPadding = PaddingValues(0.dp),
                onTimeFilterChange = {},
                onCategoryChange = {},
                onEventClick = {},
                onTogglePin = { _, _ -> },
                today = LocalDate.of(2026, 10, 23),
            )
        }
    }
}

@Preview(name = "Eventi chiaro", widthDp = 390, heightDp = 844)
@Composable
private fun EventsListLightPreview() {
    EventsListPreviewBody(dark = false)
}

@Preview(name = "Eventi scuro", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EventsListDarkPreview() {
    EventsListPreviewBody(dark = true)
}

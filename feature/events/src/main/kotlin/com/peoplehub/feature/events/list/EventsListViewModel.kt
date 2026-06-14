package com.peoplehub.feature.events.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.EventFilter
import com.peoplehub.core.domain.model.EventTimeFilter
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.usecase.GetEventsUseCase
import com.peoplehub.core.domain.usecase.ObserveEventCategoriesUseCase
import com.peoplehub.core.domain.usecase.SetEventPinnedUseCase
import com.peoplehub.core.domain.util.DateCalculations
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.state.toListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Immutable state for the events timeline screen. */
data class EventsListScreenState(
    val listState: UiState<List<EventListItem>>,
    val categories: List<String>,
    val timeFilter: EventTimeFilter,
    val category: String?,
)

/** An event as rendered in the timeline list. */
data class EventListItem(
    val id: Long,
    val title: String,
    val category: String?,
    val signedDays: Long,
    val isPast: Boolean,
    val pinned: Boolean,
    val backgroundImagePath: String? = null,
)

private data class Controls(val timeFilter: EventTimeFilter, val category: String?)

/** Backs the events timeline ("Timeline") with filtering and pin toggling. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventsListViewModel
    @Inject
    constructor(
        getEvents: GetEventsUseCase,
        observeCategories: ObserveEventCategoriesUseCase,
        private val setEventPinned: SetEventPinnedUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private val timeFilter = MutableStateFlow(EventTimeFilter.ALL)
        private val category = MutableStateFlow<String?>(null)

        private val controls = combine(timeFilter, category, ::Controls)

        private val listState: Flow<UiState<List<EventListItem>>> =
            controls
                .flatMapLatest { c ->
                    getEvents(EventFilter(timeFilter = c.timeFilter, category = c.category))
                        .map { events -> events.map { it.toListItem() } }
                }.map { it.toListUiState() }
                .catch { throwable -> emit(UiState.Error(throwable.message ?: "Unexpected error")) }
                .onStart { emit(UiState.Loading) }

        val state: StateFlow<EventsListScreenState> =
            combine(
                listState,
                observeCategories(),
                controls,
            ) { list, categories, c ->
                EventsListScreenState(
                    listState = list,
                    categories = categories,
                    timeFilter = c.timeFilter,
                    category = c.category,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue =
                    EventsListScreenState(
                        listState = UiState.Loading,
                        categories = emptyList(),
                        timeFilter = EventTimeFilter.ALL,
                        category = null,
                    ),
            )

        fun onTimeFilterChange(value: EventTimeFilter) = timeFilter.update { value }

        fun onCategoryChange(value: String?) = category.update { current -> if (current == value) null else value }

        fun onTogglePin(eventId: Long, pinned: Boolean) {
            viewModelScope.launch { setEventPinned(eventId, pinned) }
        }

        private fun PersonEvent.toListItem(): EventListItem {
            val signedDays = DateCalculations.signedDaysFromToday(dateTime.toLocalDate(), LocalDate.now(clock))
            return EventListItem(
                id = id,
                title = title,
                category = category,
                signedDays = signedDays,
                isPast = signedDays < 0,
                pinned = pinnedToWidget,
                backgroundImagePath = backgroundImagePath,
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

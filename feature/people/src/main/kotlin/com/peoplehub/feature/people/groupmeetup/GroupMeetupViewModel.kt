package com.peoplehub.feature.people.groupmeetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.RecordMeetupUseCase
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.state.toListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** A person shown in the group-meetup picker, with whether they are currently selected. */
data class SelectablePerson(
    val id: Long,
    val fullName: String,
    val initials: String,
    val photoPath: String?,
    val selected: Boolean,
)

/** The date/note portion of the "record an outing" form. */
data class GroupMeetupForm(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val multiDay: Boolean,
    val note: String,
    val selectedCount: Int,
)

/**
 * Drives the "record an outing" screen: pick several people at once and log a meetup for each of
 * them in one action, optionally spanning several days. Every selected person independently gets the
 * meetup in their own history via [RecordMeetupUseCase].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupMeetupViewModel
    @Inject
    constructor(
        getPeople: GetPeopleUseCase,
        private val recordMeetup: RecordMeetupUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private val today = LocalDate.now(clock)

        private val query = MutableStateFlow("")
        private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
        private val startDate = MutableStateFlow(today)
        private val endDate = MutableStateFlow(today)
        private val multiDay = MutableStateFlow(false)
        private val note = MutableStateFlow("")
        private val savedSignal = MutableStateFlow(false)

        /** Emits `true` once the outing has been recorded so the screen can navigate back. */
        val saved: StateFlow<Boolean> = savedSignal.asStateFlow()

        /** The searchable, selection-annotated list of pickable people (birthday-only entries excluded). */
        val people: StateFlow<UiState<List<SelectablePerson>>> =
            query
                .flatMapLatest { q -> getPeople(PeopleFilter(query = q, includeBirthdayOnly = false)) }
                .combine(selectedIds) { list, selected ->
                    list.map {
                        SelectablePerson(
                            id = it.id,
                            fullName = it.fullName,
                            initials = it.initials,
                            photoPath = it.photoPath,
                            selected = it.id in selected,
                        )
                    }
                }.map { it.toListUiState() }
                .catch { throwable -> emit(UiState.Error(throwable.message ?: "Unexpected error")) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

        /** The current date/note/selection-count form state. */
        val form: StateFlow<GroupMeetupForm> =
            combine(startDate, endDate, multiDay, note, selectedIds) { start, end, multi, text, selected ->
                GroupMeetupForm(
                    startDate = start,
                    endDate = end,
                    multiDay = multi,
                    note = text,
                    selectedCount = selected.size,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                GroupMeetupForm(today, today, multiDay = false, note = "", selectedCount = 0),
            )

        fun onQueryChange(value: String) = query.update { value }

        fun onToggle(personId: Long) =
            selectedIds.update { current ->
                if (personId in current) current - personId else current + personId
            }

        fun onNoteChange(value: String) = note.update { value }

        fun onToggleMultiDay(enabled: Boolean) {
            multiDay.value = enabled
            if (!enabled) endDate.value = startDate.value
        }

        fun onStartDate(date: LocalDate) {
            startDate.value = date
            if (endDate.value.isBefore(date)) endDate.value = date
        }

        fun onEndDate(date: LocalDate) {
            endDate.value = date
        }

        /** Records the meetup for every selected person over the chosen (possibly multi-day) range. */
        fun onSave() {
            val ids = selectedIds.value.toList()
            if (ids.isEmpty()) return
            val start = startDate.value
            val end = if (multiDay.value) endDate.value else start
            val lo = minOf(start, end)
            val hi = maxOf(start, end)
            val days = generateSequence(lo) { it.plusDays(1) }.takeWhile { !it.isAfter(hi) }.toList()
            viewModelScope.launch {
                recordMeetup(ids, days, note.value.ifBlank { null })
                savedSignal.value = true
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

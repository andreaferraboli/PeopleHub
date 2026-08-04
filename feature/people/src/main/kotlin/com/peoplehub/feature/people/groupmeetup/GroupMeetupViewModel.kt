package com.peoplehub.feature.people.groupmeetup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.usecase.DeleteOutingUseCase
import com.peoplehub.core.domain.usecase.GetOutingUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.RecordMeetupUseCase
import com.peoplehub.core.domain.usecase.UpdateOutingUseCase
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

/**
 * The date/note portion of the "record an outing" form.
 *
 * @property editing `true` when an existing outing is being rewritten rather than a new one recorded.
 * An edit always concerns a single day, so the multi-day range is only offered while recording.
 * @property error a validation or persistence failure to show inline, or `null`.
 */
data class GroupMeetupForm(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val multiDay: Boolean,
    val note: String,
    val selectedCount: Int,
    val editing: Boolean = false,
    val error: String? = null,
)

/**
 * Drives the "record an outing" screen in both of its modes.
 *
 * **Recording**: pick several people at once and log a meetup for each of them in one action,
 * optionally spanning several days. Every selected person independently gets the meetup in their own
 * history via [RecordMeetupUseCase].
 *
 * **Editing** (the route carries an `outingId`): the form is seeded from the stored outing and saving
 * rewrites it as a whole through [UpdateOutingUseCase] — the new day and description are applied to
 * every attendee, unticked people lose their check-in and newly ticked people gain one. That is what
 * makes an outing shared between several people editable without splitting it up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupMeetupViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        getPeople: GetPeopleUseCase,
        private val getOuting: GetOutingUseCase,
        private val recordMeetup: RecordMeetupUseCase,
        private val updateOuting: UpdateOutingUseCase,
        private val deleteOuting: DeleteOutingUseCase,
        clock: Clock,
    ) : ViewModel() {
        private val today = LocalDate.now(clock)

        /** The outing being edited, or `null` while recording a new one. */
        private val outingId: Long? = savedStateHandle.get<Long>(OUTING_ID_KEY)?.takeIf { it > 0L }

        private val query = MutableStateFlow("")
        private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

        /**
         * Who the edited outing was recorded with, as stored — unaffected by the user then unticking
         * them, so an attendee never disappears from the picker mid-edit and can always be put back.
         */
        private val attendeeIds = MutableStateFlow<Set<Long>>(emptySet())
        private val startDate = MutableStateFlow(today)
        private val endDate = MutableStateFlow(today)
        private val multiDay = MutableStateFlow(false)
        private val note = MutableStateFlow("")
        private val error = MutableStateFlow<String?>(null)
        private val savedSignal = MutableStateFlow(false)

        /** Emits `true` once the outing has been saved or deleted so the screen can navigate back. */
        val saved: StateFlow<Boolean> = savedSignal.asStateFlow()

        /**
         * The searchable, selection-annotated list of pickable people.
         *
         * Birthday-only entries are bare birthdays rather than tracked relationships, so they are not
         * offered when recording — **except** when they are already attending the outing being edited.
         * A quick check-in on such a person is possible from their own screen, and hiding them here
         * would leave that outing with nothing ticked: unremovable, and impossible to save at all
         * (saving needs at least one attendee).
         */
        val people: StateFlow<UiState<List<SelectablePerson>>> =
            query
                .flatMapLatest { q -> getPeople(PeopleFilter(query = q, includeBirthdayOnly = true)) }
                .combine(attendeeIds) { list, attendees -> list.filter { !it.birthdayOnly || it.id in attendees } }
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

        /**
         * The names of everyone currently ticked, unaffected by the search filter, so the attendee list
         * of the outing stays readable while typing in the search field.
         */
        val selectedNames: StateFlow<List<String>> =
            getPeople(PeopleFilter(includeBirthdayOnly = true))
                .combine(selectedIds) { list, selected ->
                    list.filter { it.id in selected }.map { it.fullName }
                }.catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

        /** The current date/note/selection form state. */
        val form: StateFlow<GroupMeetupForm> =
            combine(startDate, endDate, multiDay, note, selectedIds) { start, end, multi, text, selected ->
                GroupMeetupForm(
                    startDate = start,
                    endDate = end,
                    multiDay = multi,
                    note = text,
                    selectedCount = selected.size,
                    editing = outingId != null,
                )
            }.combine(error) { current, message -> current.copy(error = message) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    GroupMeetupForm(
                        startDate = today,
                        endDate = today,
                        multiDay = false,
                        note = "",
                        selectedCount = 0,
                        editing = outingId != null,
                    ),
                )

        init {
            val editedId = outingId
            if (editedId != null) {
                viewModelScope.launch {
                    val outing = getOuting(editedId)
                    if (outing == null) {
                        // Deleted from under us (e.g. its people were removed) — nothing left to edit.
                        savedSignal.value = true
                        return@launch
                    }
                    startDate.value = outing.date
                    endDate.value = outing.date
                    note.value = outing.note.orEmpty()
                    val attendees = outing.attendees.map { it.personId }.toSet()
                    attendeeIds.value = attendees
                    selectedIds.value = attendees
                }
            }
        }

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

        /**
         * Saves the form: rewrites the outing being edited, or records a new meetup for every selected
         * person over the chosen (possibly multi-day) range.
         */
        fun onSave() {
            val ids = selectedIds.value.toList()
            if (ids.isEmpty()) return
            val editedId = outingId
            if (editedId != null) {
                saveEdit(editedId, ids)
                return
            }
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

        /** Deletes the whole outing being edited, for every attendee at once. */
        fun onDelete() {
            val editedId = outingId ?: return
            viewModelScope.launch {
                deleteOuting(editedId)
                savedSignal.value = true
            }
        }

        private fun saveEdit(editedId: Long, personIds: List<Long>) {
            viewModelScope.launch {
                updateOuting(
                    outingId = editedId,
                    day = startDate.value,
                    personIds = personIds,
                    note = note.value.ifBlank { null },
                ).fold(
                    onSuccess = { savedSignal.value = true },
                    onFailure = { throwable -> error.value = throwable.message ?: "Could not save the outing" },
                )
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** Type-safe navigation stores each route property under its own name. */
            const val OUTING_ID_KEY = "outingId"
        }
    }

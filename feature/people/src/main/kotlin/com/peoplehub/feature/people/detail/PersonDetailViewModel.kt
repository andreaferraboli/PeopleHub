package com.peoplehub.feature.people.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.EventFilter
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.domain.usecase.CheckInPersonUseCase
import com.peoplehub.core.domain.usecase.DeletePersonUseCase
import com.peoplehub.core.domain.usecase.GetEventsUseCase
import com.peoplehub.core.domain.usecase.GetSettingsUseCase
import com.peoplehub.core.domain.usecase.ObserveCheckInHistoryUseCase
import com.peoplehub.core.domain.usecase.ObservePersonUseCase
import com.peoplehub.core.domain.util.DateCalculations
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.feature.people.navigation.PersonDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Aggregated detail state for a single person across the Info / Check-in / Related tabs. */
data class PersonDetailData(
    val person: Person,
    val status: CheckInStatus,
    val daysSince: Long?,
    val threshold: CheckInThreshold,
    val isCustomThreshold: Boolean,
    val nextBirthday: UpcomingBirthday?,
    val history: List<CheckIn>,
    val relatedEvents: List<PersonEvent>,
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePerson: ObservePersonUseCase,
    observeHistory: ObserveCheckInHistoryUseCase,
    getEvents: GetEventsUseCase,
    getSettings: GetSettingsUseCase,
    private val checkInPerson: CheckInPersonUseCase,
    private val deletePerson: DeletePersonUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val personId: Long = savedStateHandle.toRoute<PersonDetailRoute>().personId

    private val closedSignal = MutableStateFlow(false)

    /** Emits `true` once the person has been deleted so the screen can navigate back. */
    val closed: StateFlow<Boolean> = closedSignal

    val state: StateFlow<UiState<PersonDetailData>> = combine(
        observePerson(personId),
        observeHistory(personId),
        getEvents(EventFilter(personId = personId)),
        getSettings(),
    ) { person, history, events, settings ->
        if (person == null) {
            UiState.Error("Person not found")
        } else {
            val days = person.lastCheckInAt?.let { DateCalculations.daysSince(it, Instant.now(clock)) }
            val threshold = person.checkInThreshold ?: settings.defaultCheckInThreshold
            UiState.Success(
                PersonDetailData(
                    person = person,
                    status = CheckInStatus.of(days, threshold),
                    daysSince = days,
                    threshold = threshold,
                    isCustomThreshold = person.checkInThreshold != null,
                    nextBirthday = person.birthday?.let { birthdayOf(person, it) },
                    history = history,
                    relatedEvents = events,
                ),
            )
        }
    }.catch { throwable ->
        emit(UiState.Error(throwable.message ?: "Unexpected error"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
    )

    fun onCheckIn(note: String?) {
        viewModelScope.launch { checkInPerson(personId, note) }
    }

    fun onDelete() {
        viewModelScope.launch {
            deletePerson(personId)
            closedSignal.value = true
        }
    }

    private fun birthdayOf(person: Person, birthday: LocalDate): UpcomingBirthday {
        val today = LocalDate.now(clock)
        return UpcomingBirthday(
            personId = person.id,
            fullName = person.fullName,
            photoPath = person.photoPath,
            birthday = birthday,
            nextOccurrence = DateCalculations.nextBirthdayOccurrence(birthday, today),
            daysUntil = DateCalculations.daysUntilBirthday(birthday, today),
            turningAge = DateCalculations.ageOnNextBirthday(birthday, today),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

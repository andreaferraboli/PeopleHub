package com.peoplehub.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.CheckInUrgency
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.domain.usecase.CheckInPersonUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetPinnedEventUseCase
import com.peoplehub.core.domain.usecase.GetUpcomingBirthdaysUseCase
import com.peoplehub.core.domain.usecase.GetUrgentCheckInsUseCase
import com.peoplehub.core.domain.usecase.ObserveOutingsUseCase
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.work.PeopleHubWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Aggregated home ("Reflect") state across people, check-ins, birthdays, the pinned event and the
 * outings history.
 *
 * @property recentOutings the most recent outings, newest first; the full history lives in the
 * outings calendar.
 */
data class DashboardData(
    val peopleCount: Int,
    val urgentCount: Int,
    val upcomingBirthdayCount: Int,
    val urgentCheckIns: List<CheckInUrgency>,
    val upcomingBirthdays: List<UpcomingBirthday>,
    val pinnedEvent: PersonEvent?,
    val recentOutings: List<Outing> = emptyList(),
)

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        getPeople: GetPeopleUseCase,
        getUrgentCheckIns: GetUrgentCheckInsUseCase,
        getUpcomingBirthdays: GetUpcomingBirthdaysUseCase,
        getPinnedEvent: GetPinnedEventUseCase,
        observeOutings: ObserveOutingsUseCase,
        private val checkInPerson: CheckInPersonUseCase,
        private val workScheduler: PeopleHubWorkScheduler,
    ) : ViewModel() {
        val state: StateFlow<UiState<DashboardData>> =
            combine(
                getPeople(PeopleFilter()),
                getUrgentCheckIns(),
                getUpcomingBirthdays(BIRTHDAY_WINDOW_DAYS),
                getPinnedEvent(),
                observeOutings(),
            ) { people, urgent, birthdays, pinned, outings ->
                val nothingToShow =
                    listOf(people, urgent, birthdays, outings).all(List<*>::isEmpty) && pinned == null
                if (nothingToShow) {
                    UiState.Empty
                } else {
                    UiState.Success(
                        DashboardData(
                            peopleCount = people.size,
                            urgentCount = urgent.size,
                            upcomingBirthdayCount = birthdays.size,
                            urgentCheckIns = urgent.take(PREVIEW_LIMIT),
                            upcomingBirthdays = birthdays.take(PREVIEW_LIMIT),
                            pinnedEvent = pinned,
                            recentOutings = outings.take(OUTINGS_PREVIEW_LIMIT),
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

        /** Quick "I saw them today" from the home urgent list; refreshes widgets immediately. */
        fun onQuickCheckIn(personId: Long) {
            viewModelScope.launch {
                checkInPerson(personId)
                workScheduler.updateWidgetsNow()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val BIRTHDAY_WINDOW_DAYS = 30
            const val PREVIEW_LIMIT = 3
            const val OUTINGS_PREVIEW_LIMIT = 5
        }
    }

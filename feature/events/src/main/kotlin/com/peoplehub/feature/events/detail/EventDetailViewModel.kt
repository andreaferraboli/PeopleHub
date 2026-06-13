package com.peoplehub.feature.events.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.usecase.DeleteEventUseCase
import com.peoplehub.core.domain.usecase.ObserveEventUseCase
import com.peoplehub.core.domain.usecase.ObservePersonUseCase
import com.peoplehub.core.domain.usecase.SetEventPinnedUseCase
import com.peoplehub.core.domain.util.DateCalculations
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.feature.events.navigation.EventDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Aggregated detail state for a single event, including its optionally linked person. */
data class EventDetailData(
    val event: PersonEvent,
    val personName: String?,
    val signedDays: Long,
)

/** Backs the event detail screen, resolving the linked person and exposing pin/delete actions. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeEvent: ObserveEventUseCase,
    observePerson: ObservePersonUseCase,
    private val setEventPinned: SetEventPinnedUseCase,
    private val deleteEvent: DeleteEventUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val eventId: Long = savedStateHandle.toRoute<EventDetailRoute>().eventId

    private val closedSignal = MutableStateFlow(false)

    /** Emits `true` once the event has been deleted so the screen can navigate back. */
    val closed: StateFlow<Boolean> = closedSignal

    private val eventFlow: Flow<PersonEvent?> = observeEvent(eventId)

    val state: StateFlow<UiState<EventDetailData>> = eventFlow
        .flatMapLatest { event ->
            val personFlow = event?.personId?.let { observePerson(it) } ?: flowOf(null)
            combine(flowOf(event), personFlow) { current, person ->
                if (current == null) {
                    UiState.Error("Event not found")
                } else {
                    UiState.Success(
                        EventDetailData(
                            event = current,
                            personName = person?.fullName,
                            signedDays = DateCalculations.signedDaysFromToday(
                                current.dateTime.toLocalDate(),
                                LocalDate.now(clock),
                            ),
                        ),
                    )
                }
            }
        }
        .catch { throwable -> emit(UiState.Error(throwable.message ?: "Unexpected error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    fun onTogglePin(pinned: Boolean) {
        viewModelScope.launch { setEventPinned(eventId, pinned) }
    }

    fun onDelete() {
        viewModelScope.launch {
            deleteEvent(eventId)
            closedSignal.value = true
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

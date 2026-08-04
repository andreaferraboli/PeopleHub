package com.peoplehub.feature.people.outings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.domain.usecase.ObserveOutingsUseCase
import com.peoplehub.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The whole outings history, ready for both of the calendar screen's readings of it.
 *
 * @property outings every recorded outing, most recent first.
 * @property byDate the same outings indexed by day, which is what the month grid marks and what the
 * per-day list below it renders.
 */
data class OutingsCalendarData(
    val outings: List<Outing>,
    val byDate: Map<LocalDate, List<Outing>>,
)

/** Feeds the outings calendar: the full history, grouped by day. */
@HiltViewModel
class OutingsCalendarViewModel
    @Inject
    constructor(
        observeOutings: ObserveOutingsUseCase,
        clock: Clock,
    ) : ViewModel() {
        /** Today, so the grid can mark it without reaching for a clock in the composition. */
        val today: LocalDate = LocalDate.now(clock)

        val state: StateFlow<UiState<OutingsCalendarData>> =
            observeOutings()
                .map { outings ->
                    if (outings.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Success(OutingsCalendarData(outings, outings.groupBy { it.date }))
                    }
                }.catch { throwable -> emit(UiState.Error(throwable.message ?: "Unexpected error")) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading,
                )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

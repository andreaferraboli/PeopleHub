package com.peoplehub.feature.reminders.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.model.ReminderFilter
import com.peoplehub.core.domain.usecase.DeleteReminderUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetRemindersUseCase
import com.peoplehub.core.domain.usecase.SetReminderEnabledUseCase
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

/** Immutable state for the global reminders screen. */
data class RemindersScreenState(
    val listState: UiState<List<ReminderListItem>>,
    val category: ReminderCategory?,
)

/** A reminder as rendered in the global list. */
data class ReminderListItem(
    val id: Long,
    val personId: Long,
    val personName: String,
    val title: String,
    val note: String?,
    val category: ReminderCategory,
    val enabled: Boolean,
    val daysUntil: Long,
) {
    /** Whether the reminder is due now (its next fire is today or already passed). */
    val isDue: Boolean get() = daysUntil <= 0L
}

/** Backs the global reminders list with category filtering, enable toggling and deletion. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RemindersListViewModel
    @Inject
    constructor(
        getReminders: GetRemindersUseCase,
        getPeople: GetPeopleUseCase,
        private val setReminderEnabled: SetReminderEnabledUseCase,
        private val deleteReminder: DeleteReminderUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private val category = MutableStateFlow<ReminderCategory?>(null)

        private val listState: Flow<UiState<List<ReminderListItem>>> =
            category
                .flatMapLatest { selected ->
                    combine(
                        getReminders(ReminderFilter(category = selected)),
                        getPeople(PeopleFilter()),
                    ) { reminders, people ->
                        val names = people.associate { it.id to it.fullName }
                        reminders.map { it.toListItem(names[it.personId].orEmpty()) }
                    }
                }.map { it.toListUiState() }
                .catch { throwable -> emit(UiState.Error(throwable.message ?: "Unexpected error")) }
                .onStart { emit(UiState.Loading) }

        val state: StateFlow<RemindersScreenState> =
            combine(listState, category) { list, selected ->
                RemindersScreenState(listState = list, category = selected)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = RemindersScreenState(listState = UiState.Loading, category = null),
            )

        fun onCategoryChange(value: ReminderCategory?) =
            category.update { current -> if (current == value) null else value }

        fun onToggleEnabled(id: Long, enabled: Boolean) {
            viewModelScope.launch { setReminderEnabled(id, enabled) }
        }

        fun onDelete(id: Long) {
            viewModelScope.launch { deleteReminder(id) }
        }

        private fun Reminder.toListItem(personName: String): ReminderListItem {
            val nextFireDate = nextFireAt.atZone(clock.zone).toLocalDate()
            return ReminderListItem(
                id = id,
                personId = personId,
                personName = personName,
                title = title,
                note = note,
                category = category,
                enabled = enabled,
                daysUntil = DateCalculations.signedDaysFromToday(nextFireDate, LocalDate.now(clock)),
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

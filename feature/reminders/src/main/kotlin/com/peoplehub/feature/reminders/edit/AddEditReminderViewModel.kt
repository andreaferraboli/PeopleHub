package com.peoplehub.feature.reminders.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.model.ReminderCompletion
import com.peoplehub.core.domain.reminder.ReminderPreset
import com.peoplehub.core.domain.reminder.ReminderPresets
import com.peoplehub.core.domain.usecase.AddReminderCompletionUseCase
import com.peoplehub.core.domain.usecase.AddReminderUseCase
import com.peoplehub.core.domain.usecase.DeleteReminderCompletionUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetReminderUseCase
import com.peoplehub.core.domain.usecase.ObserveReminderCompletionsUseCase
import com.peoplehub.core.domain.usecase.UpdateReminderCompletionUseCase
import com.peoplehub.core.domain.usecase.UpdateReminderUseCase
import com.peoplehub.core.domain.util.ReminderScheduling
import com.peoplehub.feature.reminders.navigation.AddEditReminderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** A person available for linking, as shown in the picker. */
data class PersonOption(val id: String, val name: String)

/**
 * Editable form model backing the add/edit reminder screen.
 *
 * @property scheduledNext the next occurrence as stored (moved again whenever a history edit
 * reschedules it), or `null` for a reminder that is not saved yet.
 * @property nextOverride a next occurrence picked by hand, which wins over the cadence on save, or
 * `null` to leave it automatic.
 */
data class ReminderForm(
    val id: String = "",
    val personId: String? = null,
    val title: String = "",
    val note: String = "",
    val category: ReminderCategory = ReminderCategory.CUSTOM,
    val targetIntervalDays: Int = DEFAULT_INTERVAL_DAYS,
    val jitterPercent: Int = ReminderScheduling.DEFAULT_JITTER_PERCENT,
    val presetKey: String? = null,
    val enabled: Boolean = true,
    val scheduledNext: LocalDate? = null,
    val nextOverride: LocalDate? = null,
) {
    val canSave: Boolean get() = !personId.isNullOrEmpty() && title.isNotBlank() && targetIntervalDays >= 1

    /** The inclusive day window the current target/jitter resolve to, e.g. `11..17`. */
    val window: IntRange get() = ReminderScheduling.window(targetIntervalDays, jitterPercent)

    /** The day shown as "next time": the one picked by hand, else the stored one (`null` = automatic). */
    val nextShown: LocalDate? get() = nextOverride ?: scheduledNext

    companion object {
        const val DEFAULT_INTERVAL_DAYS = 14
    }
}

/**
 * One entry of the editable done history, in the device's time zone.
 *
 * @property time the local time of day, or `null` for old entries that kept only the day.
 * @property note the optional note on this time.
 */
data class HistoryEntry(
    val id: String,
    val day: LocalDate,
    val time: LocalTime?,
    val note: String? = null,
)

/** Why a history edit was not applied, shown as a localised message by the screen. */
enum class HistoryError {
    /** The chosen moment lies in the future. */
    FUTURE,

    /** The write failed (or the entry/reminder was gone). */
    FAILED,
}

/**
 * Backs the add/edit reminder screen: seeds the form when editing, persists every field of the reminder
 * via the use cases, and edits its done history (add a past time, move one, delete one), each change
 * applied immediately and rescheduling the next occurrence when it touches the latest time.
 */
@HiltViewModel
class AddEditReminderViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        getPeople: GetPeopleUseCase,
        observeCompletions: ObserveReminderCompletionsUseCase,
        private val getReminder: GetReminderUseCase,
        private val addReminder: AddReminderUseCase,
        private val updateReminder: UpdateReminderUseCase,
        private val addCompletion: AddReminderCompletionUseCase,
        private val updateCompletion: UpdateReminderCompletionUseCase,
        private val deleteCompletion: DeleteReminderCompletionUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<AddEditReminderRoute>()
        private val reminderId: String = route.reminderId

        /** Whether this screen is editing an existing reminder rather than creating a new one. */
        val isEditing: Boolean = reminderId != AddEditReminderRoute.NEW_REMINDER

        /** Whether the person is fixed by the entry point (opened from a person's detail screen). */
        val personLocked: Boolean = route.personId != AddEditReminderRoute.NO_PERSON

        /** The full reminder loaded when editing, kept so fields the form does not show survive a save. */
        private var loaded: Reminder? = null

        private val initialPersonId: String? = route.personId.takeIf { it != AddEditReminderRoute.NO_PERSON }

        private val _form = MutableStateFlow(ReminderForm(personId = initialPersonId))
        val form: StateFlow<ReminderForm> = _form.asStateFlow()

        private val _saved = MutableStateFlow(false)
        val saved: StateFlow<Boolean> = _saved.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _historyError = MutableStateFlow<HistoryError?>(null)

        /** The last history edit that was refused, until the screen has shown it. */
        val historyError: StateFlow<HistoryError?> = _historyError.asStateFlow()

        /** Today in the device's zone, for the pickers (a done time cannot lie in the future). */
        val today: LocalDate get() = LocalDate.now(clock)

        /** The built-in presets, grouped by category, offered when creating a reminder. */
        val presets: List<ReminderPreset> = ReminderPresets.all

        /** The people available for linking to this reminder. */
        val people: StateFlow<List<PersonOption>> =
            getPeople(PeopleFilter())
                .map { list -> list.map { PersonOption(id = it.id, name = it.fullName) } }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = emptyList(),
                )

        /** Every time this reminder was done, most recent first (always empty for a new reminder). */
        val history: StateFlow<List<HistoryEntry>> =
            (if (isEditing) observeCompletions() else flowOf(emptyMap<String, ReminderCompletion>()))
                .map { all ->
                    all[reminderId]?.history.orEmpty().map { entry ->
                        HistoryEntry(
                            id = entry.id,
                            day = entry.day,
                            time = entry.at?.atZone(clock.zone)?.toLocalTime(),
                            note = entry.note,
                        )
                    }
                }.catch { emit(emptyList()) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = emptyList(),
                )

        init {
            if (isEditing) {
                viewModelScope.launch {
                    getReminder(reminderId)?.let { reminder ->
                        loaded = reminder
                        _form.value = reminder.toForm()
                    }
                }
            }
        }

        fun onTitleChange(value: String) = _form.update { it.copy(title = value) }

        fun onNoteChange(value: String) = _form.update { it.copy(note = value) }

        fun onCategoryChange(value: ReminderCategory) = _form.update { it.copy(category = value) }

        fun onPersonChange(value: String?) = _form.update { it.copy(personId = value) }

        fun onEnabledChange(value: Boolean) = _form.update { it.copy(enabled = value) }

        fun onTargetChange(value: Int) =
            _form.update { it.copy(targetIntervalDays = value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)) }

        fun onJitterChange(value: Int) =
            _form.update { it.copy(jitterPercent = value.coerceIn(0, ReminderScheduling.MAX_JITTER_PERCENT)) }

        /** Picks the next occurrence by hand; `null` goes back to letting the cadence decide. */
        fun onNextOverrideChange(value: LocalDate?) = _form.update { it.copy(nextOverride = value) }

        /**
         * Prefills the form from [preset]; its localised [name] and [description] are resolved by the
         * screen (the domain preset carries only the machine-readable knobs). The linked person is left
         * untouched.
         */
        fun onApplyPreset(preset: ReminderPreset, name: String, description: String) =
            _form.update {
                it.copy(
                    title = name,
                    note = description,
                    category = preset.category,
                    targetIntervalDays = preset.targetIntervalDays,
                    jitterPercent = preset.jitterPercent,
                    presetKey = preset.key,
                )
            }

        fun onSave() {
            val form = _form.value
            val override = form.nextOverride?.atStartOfDay(clock.zone)?.toInstant()
            viewModelScope.launch {
                val result =
                    if (isEditing) {
                        updateReminder(form.toReminder(), override)
                    } else {
                        addReminder(form.toReminder(), override)
                    }
                result.fold(
                    onSuccess = { _saved.value = true },
                    onFailure = { _error.value = it.message ?: "Could not save" },
                )
            }
        }

        fun onErrorShown() {
            _error.value = null
        }

        fun onHistoryErrorShown() {
            _historyError.value = null
        }

        /** Adds a time the reminder was done on [day] at [time] (in the past, typically forgotten to tick). */
        fun onAddCompletion(day: LocalDate, time: LocalTime, note: String) {
            val at = toInstantOrReport(day, time) ?: return
            viewModelScope.launch { addCompletion(reminderId, at, note).applyHistoryResult() }
        }

        /** Moves the history entry [id] to [day] at [time], with [note] (blank clears it). */
        fun onUpdateCompletion(id: String, day: LocalDate, time: LocalTime, note: String) {
            val at = toInstantOrReport(day, time) ?: return
            viewModelScope.launch { updateCompletion(reminderId, id, at, note).applyHistoryResult() }
        }

        /** Deletes the history entry [id]. */
        fun onDeleteCompletion(id: String) {
            viewModelScope.launch { deleteCompletion(reminderId, id).applyHistoryResult() }
        }

        /** The instant of [day] at [time], or `null` (with the error raised) when it lies in the future. */
        private fun toInstantOrReport(day: LocalDate, time: LocalTime): Instant? {
            val at = day.atTime(time).atZone(clock.zone).toInstant()
            if (at.isAfter(Instant.now(clock))) {
                _historyError.value = HistoryError.FUTURE
                return null
            }
            return at
        }

        /** Mirrors the rescheduled next occurrence into the form, or raises the error. */
        private fun Result<Instant>.applyHistoryResult() =
            fold(
                onSuccess = { next ->
                    loaded = loaded?.copy(nextFireAt = next)
                    _form.update { it.copy(scheduledNext = next.atZone(clock.zone).toLocalDate()) }
                },
                onFailure = { _historyError.value = HistoryError.FAILED },
            )

        private fun Reminder.toForm(): ReminderForm =
            ReminderForm(
                id = id,
                personId = personId,
                title = title,
                note = note.orEmpty(),
                category = category,
                targetIntervalDays = targetIntervalDays,
                jitterPercent = jitterPercent,
                presetKey = presetKey,
                enabled = enabled,
                scheduledNext = nextFireAt.atZone(clock.zone).toLocalDate(),
            )

        private fun ReminderForm.toReminder(): Reminder {
            val base =
                loaded ?: Reminder(
                    personId = personId.orEmpty(),
                    title = title,
                    targetIntervalDays = targetIntervalDays,
                    jitterPercent = jitterPercent,
                )
            return base.copy(
                id = id,
                personId = personId ?: base.personId,
                title = title,
                note = note.ifBlank { null },
                category = category,
                targetIntervalDays = targetIntervalDays,
                jitterPercent = jitterPercent,
                presetKey = presetKey,
                enabled = enabled,
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val MIN_INTERVAL = 1
            const val MAX_INTERVAL = 365
        }
    }

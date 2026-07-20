package com.peoplehub.feature.reminders.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.reminder.ReminderPreset
import com.peoplehub.core.domain.reminder.ReminderPresets
import com.peoplehub.core.domain.usecase.AddReminderUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetReminderUseCase
import com.peoplehub.core.domain.usecase.UpdateReminderUseCase
import com.peoplehub.core.domain.util.ReminderScheduling
import com.peoplehub.feature.reminders.navigation.AddEditReminderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A person available for linking, as shown in the picker. */
data class PersonOption(val id: Long, val name: String)

/** Editable form model backing the add/edit reminder screen. */
data class ReminderForm(
    val id: Long = 0L,
    val personId: Long? = null,
    val title: String = "",
    val note: String = "",
    val category: ReminderCategory = ReminderCategory.CUSTOM,
    val targetIntervalDays: Int = DEFAULT_INTERVAL_DAYS,
    val jitterPercent: Int = ReminderScheduling.DEFAULT_JITTER_PERCENT,
    val presetKey: String? = null,
) {
    val canSave: Boolean get() = personId != null && personId > 0L && title.isNotBlank() && targetIntervalDays >= 1

    /** The inclusive day window the current target/jitter resolve to, e.g. `11..17`. */
    val window: IntRange get() = ReminderScheduling.window(targetIntervalDays, jitterPercent)

    companion object {
        const val DEFAULT_INTERVAL_DAYS = 14
    }
}

/** Backs the add/edit reminder screen, seeding the form when editing and persisting via use cases. */
@HiltViewModel
class AddEditReminderViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        getPeople: GetPeopleUseCase,
        private val getReminder: GetReminderUseCase,
        private val addReminder: AddReminderUseCase,
        private val updateReminder: UpdateReminderUseCase,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<AddEditReminderRoute>()
        private val reminderId: Long = route.reminderId

        /** Whether this screen is editing an existing reminder rather than creating a new one. */
        val isEditing: Boolean = reminderId != AddEditReminderRoute.NEW_REMINDER

        /** Whether the person is fixed by the entry point (opened from a person's detail screen). */
        val personLocked: Boolean = route.personId != AddEditReminderRoute.NO_PERSON

        /** The full reminder loaded when editing, kept so non-editable fields survive a save. */
        private var loaded: Reminder? = null

        private val initialPersonId: Long? = route.personId.takeIf { it != AddEditReminderRoute.NO_PERSON }

        private val _form = MutableStateFlow(ReminderForm(personId = initialPersonId))
        val form: StateFlow<ReminderForm> = _form.asStateFlow()

        private val _saved = MutableStateFlow(false)
        val saved: StateFlow<Boolean> = _saved.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

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

        fun onPersonChange(value: Long?) = _form.update { it.copy(personId = value) }

        fun onTargetChange(value: Int) =
            _form.update { it.copy(targetIntervalDays = value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)) }

        fun onJitterChange(value: Int) =
            _form.update { it.copy(jitterPercent = value.coerceIn(0, ReminderScheduling.MAX_JITTER_PERCENT)) }

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
            viewModelScope.launch {
                val result =
                    if (isEditing) {
                        updateReminder(form.toReminder())
                    } else {
                        addReminder(form.toReminder())
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
            )

        private fun ReminderForm.toReminder(): Reminder {
            val base =
                loaded ?: Reminder(
                    personId = personId ?: 0L,
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
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val MIN_INTERVAL = 1
            const val MAX_INTERVAL = 365
        }
    }

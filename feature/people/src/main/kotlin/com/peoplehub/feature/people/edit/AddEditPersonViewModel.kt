package com.peoplehub.feature.people.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.ObservePersonUseCase
import com.peoplehub.core.domain.usecase.UpsertPersonUseCase
import com.peoplehub.feature.people.navigation.AddEditPersonRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Editable form model backing the add/edit screen. */
data class PersonForm(
    val id: Long = 0L,
    val firstName: String = "",
    val lastName: String = "",
    val birthday: LocalDate? = null,
    val photoPath: String? = null,
    val tags: List<String> = emptyList(),
    val interests: List<Interest> = emptyList(),
    val notes: String = "",
    val checkInThreshold: CheckInThreshold? = null,
    val createdAt: Instant = Instant.EPOCH,
    val lastCheckInAt: Instant? = null,
    val notificationsEnabled: Boolean = false,
    val birthdayOnly: Boolean = false,
) {
    val canSave: Boolean get() = firstName.isNotBlank()
}

@HiltViewModel
class AddEditPersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observePerson: ObservePersonUseCase,
    private val upsertPerson: UpsertPersonUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val personId: Long = savedStateHandle.toRoute<AddEditPersonRoute>().personId

    /** Whether this screen is editing an existing person rather than creating a new one. */
    val isEditing: Boolean = personId != AddEditPersonRoute.NEW_PERSON

    private val _form = MutableStateFlow(PersonForm())
    val form: StateFlow<PersonForm> = _form.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (isEditing) {
            viewModelScope.launch {
                observePerson(personId).first()?.let { person ->
                    _form.value = person.toForm()
                }
            }
        }
    }

    fun onFirstNameChange(value: String) = _form.update { it.copy(firstName = value) }
    fun onLastNameChange(value: String) = _form.update { it.copy(lastName = value) }
    fun onNotesChange(value: String) = _form.update { it.copy(notes = value) }
    fun onBirthdayChange(value: LocalDate?) = _form.update { it.copy(birthday = value) }
    fun onPhotoChange(path: String?) = _form.update { it.copy(photoPath = path) }

    fun onAddTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        _form.update { if (trimmed in it.tags) it else it.copy(tags = it.tags + trimmed) }
    }

    fun onRemoveTag(tag: String) = _form.update { it.copy(tags = it.tags - tag) }

    fun onAddInterest() = _form.update { it.copy(interests = it.interests + Interest(key = "", value = "")) }

    fun onInterestChange(index: Int, key: String, value: String) = _form.update { form ->
        val updated = form.interests.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(key = key, value = value)
        }
        form.copy(interests = updated)
    }

    fun onRemoveInterest(index: Int) = _form.update { form ->
        form.copy(interests = form.interests.filterIndexed { i, _ -> i != index })
    }

    /** Toggles whether this person may trigger check-in and birthday notifications. */
    fun onNotificationsEnabledChange(enabled: Boolean) = _form.update { it.copy(notificationsEnabled = enabled) }

    /** Toggles whether this entry is a bare birthday (hidden from the directory). */
    fun onBirthdayOnlyChange(enabled: Boolean) = _form.update { it.copy(birthdayOnly = enabled) }

    /** Enables or disables a per-person check-in cadence override (off falls back to the global default). */
    fun onThresholdEnabledChange(enabled: Boolean) = _form.update {
        it.copy(checkInThreshold = if (enabled) (it.checkInThreshold ?: CheckInThreshold.Default) else null)
    }

    /** Updates the per-person cadence, keeping the critical window strictly beyond the warning window. */
    fun onThresholdChange(warningDays: Int, criticalDays: Int) = _form.update {
        val warning = warningDays.coerceAtLeast(1)
        it.copy(checkInThreshold = CheckInThreshold(warning, criticalDays.coerceAtLeast(warning + 1)))
    }

    fun onSave() {
        val form = _form.value
        viewModelScope.launch {
            upsertPerson(form.toPerson()).fold(
                onSuccess = { _saved.value = true },
                onFailure = { _error.value = it.message ?: "Could not save" },
            )
        }
    }

    fun onErrorShown() {
        _error.value = null
    }

    private fun Person.toForm(): PersonForm = PersonForm(
        id = id,
        firstName = firstName,
        lastName = lastName,
        birthday = birthday,
        photoPath = photoPath,
        tags = tags,
        interests = interests,
        notes = notes,
        checkInThreshold = checkInThreshold,
        createdAt = createdAt,
        lastCheckInAt = lastCheckInAt,
        notificationsEnabled = notificationsEnabled,
        birthdayOnly = birthdayOnly,
    )

    private fun PersonForm.toPerson(): Person = Person(
        id = id,
        firstName = firstName,
        lastName = lastName,
        photoPath = photoPath,
        birthday = birthday,
        tags = tags,
        interests = interests.filter { it.key.isNotBlank() || it.value.isNotBlank() },
        notes = notes,
        lastCheckInAt = lastCheckInAt,
        checkInThreshold = checkInThreshold,
        createdAt = if (isEditing) createdAt else clock.instant(),
        notificationsEnabled = notificationsEnabled,
        birthdayOnly = birthdayOnly,
    )
}

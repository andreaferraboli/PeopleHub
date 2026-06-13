package com.peoplehub.feature.events.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.usecase.AddEventUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.ObserveEventUseCase
import com.peoplehub.feature.events.navigation.AddEditEventRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** A person available for linking, as shown in the picker. */
data class PersonOption(val id: Long, val name: String)

/** Editable form model backing the add/edit event screen. */
data class EventForm(
    val id: Long = 0L,
    val title: String = "",
    val dateTime: LocalDateTime = LocalDateTime.MIN,
    val description: String = "",
    val category: String = "",
    val personId: Long? = null,
    val pinnedToWidget: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()
}

/** Backs the add/edit event screen, seeding the form when editing and persisting via the use case. */
@HiltViewModel
class AddEditEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeEvent: ObserveEventUseCase,
    getPeople: GetPeopleUseCase,
    private val addEvent: AddEventUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val eventId: Long = savedStateHandle.toRoute<AddEditEventRoute>().eventId

    /** Whether this screen is editing an existing event rather than creating a new one. */
    val isEditing: Boolean = eventId != AddEditEventRoute.NEW_EVENT

    private val _form = MutableStateFlow(EventForm(dateTime = LocalDateTime.now(clock)))
    val form: StateFlow<EventForm> = _form.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The people available for linking to this event. */
    val people: StateFlow<List<PersonOption>> = getPeople(PeopleFilter())
        .map { list -> list.map { PersonOption(id = it.id, name = it.fullName) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    init {
        if (isEditing) {
            viewModelScope.launch {
                observeEvent(eventId).first()?.let { event ->
                    _form.value = event.toForm()
                }
            }
        }
    }

    fun onTitleChange(value: String) = _form.update { it.copy(title = value) }
    fun onDescriptionChange(value: String) = _form.update { it.copy(description = value) }
    fun onCategoryChange(value: String) = _form.update { it.copy(category = value) }
    fun onPersonChange(value: Long?) = _form.update { it.copy(personId = value) }
    fun onPinnedChange(value: Boolean) = _form.update { it.copy(pinnedToWidget = value) }

    fun onDateChange(date: LocalDate) = _form.update { it.copy(dateTime = it.dateTime.with(date)) }

    fun onTimeChange(time: LocalTime) = _form.update {
        it.copy(dateTime = it.dateTime.withHour(time.hour).withMinute(time.minute))
    }

    fun onSave() {
        val form = _form.value
        viewModelScope.launch {
            addEvent(form.toEvent()).fold(
                onSuccess = { _saved.value = true },
                onFailure = { _error.value = it.message ?: "Could not save" },
            )
        }
    }

    fun onErrorShown() {
        _error.value = null
    }

    private fun PersonEvent.toForm(): EventForm = EventForm(
        id = id,
        title = title,
        dateTime = dateTime,
        description = description.orEmpty(),
        category = category.orEmpty(),
        personId = personId,
        pinnedToWidget = pinnedToWidget,
    )

    private fun EventForm.toEvent(): PersonEvent = PersonEvent(
        id = id,
        title = title,
        dateTime = dateTime,
        description = description.ifBlank { null },
        category = category.ifBlank { null },
        personId = personId,
        pinnedToWidget = pinnedToWidget,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

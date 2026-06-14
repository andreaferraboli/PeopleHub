package com.peoplehub.feature.people.birthdayonly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.UpsertPersonUseCase
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.state.toListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Birthday-only" management screen: every person who has a birthday is listed with a
 * toggle that marks them as a bare birthday (hidden from the directory but kept in the calendar).
 */
@HiltViewModel
class BirthdayOnlyViewModel @Inject constructor(
    getPeople: GetPeopleUseCase,
    private val upsertPerson: UpsertPersonUseCase,
) : ViewModel() {

    val state: StateFlow<UiState<List<Person>>> =
        getPeople(PeopleFilter(includeBirthdayOnly = true))
            .map { people -> people.filter { it.birthday != null } }
            .map { it.toListUiState() }
            .catch { throwable -> emit(UiState.Error(throwable.message ?: "Unexpected error")) }
            .onStart { emit(UiState.Loading) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    /** Marks [person] as birthday-only (or back to a full profile) and persists the change. */
    fun onToggle(person: Person, birthdayOnly: Boolean) {
        viewModelScope.launch {
            upsertPerson(person.copy(birthdayOnly = birthdayOnly))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

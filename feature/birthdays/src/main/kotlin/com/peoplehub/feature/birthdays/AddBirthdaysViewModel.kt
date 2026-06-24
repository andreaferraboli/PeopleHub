package com.peoplehub.feature.birthdays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.UpsertPersonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * A single editable "birthday-only" draft in the bulk add screen: just a [name] and an optional
 * [date]. [rowId] is a stable, monotonically increasing key so Compose can track each row across
 * insertions and removals.
 */
data class BirthdayDraft(
    val rowId: Int,
    val name: String = "",
    val date: LocalDate? = null,
) {
    /** A draft is persistable once it has both a non-blank name and a chosen date. */
    val isComplete: Boolean
        get() = name.isNotBlank() && date != null
}

/**
 * Backs the "Add birthdays" screen: the user fills in a list of name + date drafts and saves them all
 * at once as bare birthday-only [Person] entries (hidden from The Circle, kept in Milestones).
 */
@HiltViewModel
class AddBirthdaysViewModel
    @Inject
    constructor(
        private val upsertPerson: UpsertPersonUseCase,
    ) : ViewModel() {
        private var nextRowId = 1

        private val draftsState = MutableStateFlow(listOf(BirthdayDraft(rowId = 0)))
        private val savedState = MutableStateFlow(false)

        /** The current list of editable drafts; always holds at least one row. */
        val drafts: StateFlow<List<BirthdayDraft>> = draftsState.asStateFlow()

        /** Flips to `true` once every complete draft has been persisted, signalling the screen to close. */
        val saved: StateFlow<Boolean> = savedState.asStateFlow()

        /** Appends a fresh, empty draft row. */
        fun onAddRow() {
            draftsState.update { it + BirthdayDraft(rowId = nextRowId++) }
        }

        /** Removes the row with [rowId]; the last remaining row is reset rather than removed. */
        fun onRemoveRow(rowId: Int) {
            draftsState.update { current ->
                if (current.size <= 1) {
                    listOf(BirthdayDraft(rowId = nextRowId++))
                } else {
                    current.filterNot { it.rowId == rowId }
                }
            }
        }

        /** Updates the name of the row with [rowId]. */
        fun onNameChange(rowId: Int, name: String) {
            draftsState.update { current ->
                current.map { if (it.rowId == rowId) it.copy(name = name) else it }
            }
        }

        /** Updates the date of the row with [rowId]. */
        fun onDateChange(rowId: Int, date: LocalDate) {
            draftsState.update { current ->
                current.map { if (it.rowId == rowId) it.copy(date = date) else it }
            }
        }

        /** Persists every complete draft as a birthday-only person, then signals completion. */
        fun onSave() {
            val complete = draftsState.value.filter { it.isComplete }
            if (complete.isEmpty()) return
            viewModelScope.launch {
                complete.forEach { draft ->
                    val (first, last) = splitName(draft.name)
                    upsertPerson(
                        Person(
                            firstName = first,
                            lastName = last,
                            birthday = draft.date,
                            birthdayOnly = true,
                        ),
                    )
                }
                savedState.value = true
            }
        }

        private fun splitName(raw: String): Pair<String, String> {
            val trimmed = raw.trim()
            val firstSpace = trimmed.indexOf(' ')
            return if (firstSpace < 0) {
                trimmed to ""
            } else {
                trimmed.substring(0, firstSpace) to trimmed.substring(firstSpace + 1).trim()
            }
        }
    }

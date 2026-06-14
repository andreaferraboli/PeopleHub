package com.peoplehub.feature.people.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.PeopleSort
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetSettingsUseCase
import com.peoplehub.core.domain.usecase.ObserveAllTagsUseCase
import com.peoplehub.core.domain.util.DateCalculations
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.state.toListUiState
import com.peoplehub.feature.people.ImportPersonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Immutable state for the people directory screen. */
data class PeopleListScreenState(
    val listState: UiState<List<PersonListItem>>,
    val allTags: List<String>,
    val query: String,
    val selectedTags: Set<String>,
    val sort: PeopleSort,
    val importPreview: Person?,
    val importMessage: String?,
)

/** A person as rendered in the directory list. */
data class PersonListItem(
    val id: Long,
    val fullName: String,
    val initials: String,
    val photoPath: String?,
    val primaryTag: String?,
    val status: CheckInStatus,
    val daysSince: Long?,
    val nextBirthday: LocalDate? = null,
    val daysUntilBirthday: Int? = null,
)

private data class Controls(val query: String, val tags: Set<String>, val sort: PeopleSort)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeopleListViewModel
    @Inject
    constructor(
        getPeople: GetPeopleUseCase,
        observeAllTags: ObserveAllTagsUseCase,
        getSettings: GetSettingsUseCase,
        private val importPerson: ImportPersonUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
        private val sort = MutableStateFlow(PeopleSort.NAME_ASC)
        private val importPreview = MutableStateFlow<Person?>(null)
        private val importMessage = MutableStateFlow<String?>(null)

        private val controls = combine(query, selectedTags, sort, ::Controls)

        private val listState: kotlinx.coroutines.flow.Flow<UiState<List<PersonListItem>>> =
            controls
                .flatMapLatest { c ->
                    val filter = PeopleFilter(c.query, c.tags, c.sort, includeBirthdayOnly = false)
                    combine(getPeople(filter), getSettings()) { people, settings ->
                        people.map { it.toListItem(settings.defaultCheckInThreshold) }
                    }
                }.map { it.toListUiState() }
                .catch { throwable -> emit(UiState.Error(throwable.message ?: "Unexpected error")) }
                .onStart { emit(UiState.Loading) }

        val state: StateFlow<PeopleListScreenState> =
            combine(
                listState,
                observeAllTags(),
                controls,
                importPreview,
                importMessage,
            ) { list, tags, c, preview, message ->
                PeopleListScreenState(
                    listState = list,
                    allTags = tags,
                    query = c.query,
                    selectedTags = c.tags,
                    sort = c.sort,
                    importPreview = preview,
                    importMessage = message,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue =
                    PeopleListScreenState(
                        listState = UiState.Loading,
                        allTags = emptyList(),
                        query = "",
                        selectedTags = emptySet(),
                        sort = PeopleSort.NAME_ASC,
                        importPreview = null,
                        importMessage = null,
                    ),
            )

        fun onQueryChange(value: String) = query.update { value }

        fun onToggleTag(tag: String) =
            selectedTags.update { current ->
                if (tag in current) current - tag else current + tag
            }

        fun onSortChange(value: PeopleSort) = sort.update { value }

        /** Parses a selected JSON document and, on success, raises a confirmation preview. */
        fun onImportFileLoaded(json: String) {
            importPerson.preview(json).fold(
                onSuccess = { importPreview.value = it },
                onFailure = { importMessage.value = it.message ?: "Invalid file" },
            )
        }

        fun onConfirmImport() {
            val candidate = importPreview.value ?: return
            importPreview.value = null
            viewModelScope.launch {
                importMessage.value =
                    importPerson.confirm(candidate).fold(
                        onSuccess = { "Imported ${it.fullName}" },
                        onFailure = { it.message ?: "Import failed" },
                    )
            }
        }

        fun onDismissImport() {
            importPreview.value = null
        }

        fun onMessageShown() {
            importMessage.value = null
        }

        private fun Person.toListItem(defaultThreshold: CheckInThreshold): PersonListItem {
            val threshold = checkInThreshold ?: defaultThreshold
            val days = lastCheckInAt?.let { DateCalculations.daysSince(it, Instant.now(clock)) }
            val today = LocalDate.now(clock)
            return PersonListItem(
                id = id,
                fullName = fullName,
                initials = initials,
                photoPath = photoPath,
                primaryTag = tags.firstOrNull(),
                status = CheckInStatus.of(days, threshold),
                daysSince = days,
                nextBirthday = birthday?.let { DateCalculations.nextBirthdayOccurrence(it, today) },
                daysUntilBirthday = birthday?.let { DateCalculations.daysUntilBirthday(it, today) },
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

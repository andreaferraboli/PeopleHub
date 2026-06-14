package com.peoplehub.feature.people.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.PeopleSort
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.BulkUpdatePeopleUseCase
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
    val selectedIds: Set<Long> = emptySet(),
) {
    /** Whether the multi-select action bar should be shown. */
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

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
    val checkInDisabled: Boolean = false,
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
        private val bulkUpdatePeople: BulkUpdatePeopleUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
        private val sort = MutableStateFlow(PeopleSort.NAME_ASC)
        private val importPreview = MutableStateFlow<Person?>(null)
        private val importMessage = MutableStateFlow<String?>(null)
        private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

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

        private val baseState =
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
            }

        val state: StateFlow<PeopleListScreenState> =
            combine(baseState, selectedIds) { base, ids ->
                // Drop selections for people no longer visible (e.g. filtered out) to avoid stale ids.
                val visible = (base.listState as? UiState.Success)?.data?.mapTo(mutableSetOf()) { it.id }
                base.copy(selectedIds = if (visible == null) ids else ids intersect visible)
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

        /** Adds or removes [id] from the current multi-selection. */
        fun onToggleSelection(id: Long) =
            selectedIds.update { current -> if (id in current) current - id else current + id }

        /** Selects every person currently visible in the directory. */
        fun onSelectAll() {
            val ids = (state.value.listState as? UiState.Success)?.data?.map { it.id } ?: return
            selectedIds.value = ids.toSet()
        }

        /** Leaves multi-select mode, clearing the selection. */
        fun onClearSelection() {
            selectedIds.value = emptySet()
        }

        /** Enables or disables per-person reminders for the whole selection. */
        fun onBulkNotifications(enabled: Boolean) =
            runBulk(if (enabled) "Notifications enabled" else "Notifications disabled") { ids ->
                bulkUpdatePeople.setNotificationsEnabled(ids, enabled)
            }

        /** Marks the selection as birthday-only (or restores them to tracked relationships). */
        fun onBulkBirthdayOnly(enabled: Boolean) =
            runBulk(if (enabled) "Marked as birthday-only" else "Restored to the circle") { ids ->
                bulkUpdatePeople.setBirthdayOnly(ids, enabled)
            }

        /** Applies a custom check-in cadence to the whole selection. */
        fun onBulkCustomCheckIn(warningDays: Int, criticalDays: Int) {
            val warning = warningDays.coerceAtLeast(1)
            val threshold = CheckInThreshold(warning, criticalDays.coerceAtLeast(warning + 1))
            runBulk("Check-in cadence updated") { ids -> bulkUpdatePeople.setCheckInThreshold(ids, threshold) }
        }

        /** The "never" option: disables check-in tracking for the whole selection. */
        fun onBulkDisableCheckIn() =
            runBulk("Check-in disabled") { ids -> bulkUpdatePeople.disableCheckIn(ids) }

        private fun runBulk(successMessage: String, action: suspend (List<Long>) -> Result<Unit>) {
            val ids = selectedIds.value.toList()
            if (ids.isEmpty()) return
            viewModelScope.launch {
                importMessage.value =
                    action(ids).fold(
                        onSuccess = { successMessage },
                        onFailure = { it.message ?: "Operation failed" },
                    )
                selectedIds.value = emptySet()
            }
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
                checkInDisabled = checkInDisabled,
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

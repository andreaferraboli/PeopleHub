package com.peoplehub.feature.birthdays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.dataio.BackupSerializer
import com.peoplehub.core.dataio.CsvBirthdaySupport
import com.peoplehub.core.dataio.PersonJsonImporter
import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.domain.usecase.GetAllBirthdaysUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.UpsertPersonUseCase
import com.peoplehub.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/** The three ways the milestones calendar can be viewed. */
enum class BirthdayViewMode {
    /** A twelve-month overview grid for the whole year. */
    YEAR,

    /** A single-month calendar grid. */
    MONTH,

    /** A chronological list of upcoming birthdays. */
    LIST,
}

/**
 * Projected birthday data backing the milestones screen.
 *
 * @property all every birthday, sorted by [UpcomingBirthday.daysUntil].
 * @property upcoming30 the subset falling within the next 30 days.
 * @property byMonthDay birthdays keyed by `monthValue * 100 + dayOfMonth`, used to place the gold
 * dots on the calendar grids; the key is month/day only so it is year-agnostic.
 */
data class BirthdaysData(
    val all: List<UpcomingBirthday>,
    val upcoming30: List<UpcomingBirthday>,
    val byMonthDay: Map<Int, List<UpcomingBirthday>>,
)

/**
 * A one-shot, resource-agnostic result of an import/export action. The screen turns it into a
 * localized snackbar message so the ViewModel stays free of `android.*` resource lookups.
 */
sealed interface BirthdayMessage {
    /** A CSV import finished with [imported] new people and [errors] malformed rows. */
    data class CsvImported(val imported: Int, val errors: Int) : BirthdayMessage

    /** A single JSON profile named [name] was imported. */
    data class ProfileImported(val name: String) : BirthdayMessage

    /** An import failed before anything was persisted. */
    data object ImportFailed : BirthdayMessage
}

/**
 * Drives the birthdays ("Milestones") screen: it projects [GetAllBirthdaysUseCase] into calendar and
 * list data, tracks the active view mode and visible month, and handles CSV/JSON import & export.
 *
 * SAF file reading and writing happens in the composable layer; this ViewModel only consumes/produces
 * plain strings so it stays free of any `android.*` dependency.
 */
@HiltViewModel
class BirthdaysViewModel
    @Inject
    constructor(
        getAllBirthdays: GetAllBirthdaysUseCase,
        getPeople: GetPeopleUseCase,
        private val csvBirthdaySupport: CsvBirthdaySupport,
        private val personJsonImporter: PersonJsonImporter,
        private val backupSerializer: BackupSerializer,
        private val upsertPerson: UpsertPersonUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        /** Latest people snapshot, kept so exports can reconstruct full [Person] records. */
        private var peopleSnapshot: List<Person> = emptyList()

        private val viewModeState = MutableStateFlow(BirthdayViewMode.MONTH)
        private val visibleMonthState = MutableStateFlow(YearMonth.now(clock))
        private val messageState = MutableStateFlow<BirthdayMessage?>(null)

        /** The active calendar view mode. */
        val viewMode: StateFlow<BirthdayViewMode> = viewModeState.asStateFlow()

        /** The month currently shown in [BirthdayViewMode.MONTH]. */
        val visibleMonth: StateFlow<YearMonth> = visibleMonthState.asStateFlow()

        /** A one-shot import/export result, cleared via [onMessageShown]. */
        val message: StateFlow<BirthdayMessage?> = messageState.asStateFlow()

        val state: StateFlow<UiState<BirthdaysData>> =
            getAllBirthdays()
                .map { birthdays -> birthdays.toData() }
                .map { data -> if (data.all.isEmpty()) UiState.Empty else UiState.Success(data) }
                .catch { throwable -> emit(UiState.Error(throwable.message ?: "Unexpected error")) }
                .onStart { emit(UiState.Loading) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading,
                )

        init {
            getPeople(PeopleFilter())
                .onEach { people -> peopleSnapshot = people }
                .catch { /* export simply falls back to the last good snapshot */ }
                .launchIn(viewModelScope)
        }

        /** Switches the calendar between year, month and list views. */
        fun onViewModeChange(mode: BirthdayViewMode) = viewModeState.update { mode }

        /** Steps the month view back one month. */
        fun onPreviousMonth() = visibleMonthState.update { it.minusMonths(1) }

        /** Steps the month view forward one month. */
        fun onNextMonth() = visibleMonthState.update { it.plusMonths(1) }

        /**
         * Parses a birthday [csv], persists each successfully parsed person, and reports how many rows
         * imported versus failed.
         */
        fun onCsvImported(csv: String) {
            val result = csvBirthdaySupport.parse(csv)
            viewModelScope.launch {
                result.people.forEach { upsertPerson(it) }
                messageState.value =
                    BirthdayMessage.CsvImported(
                        imported = result.people.size,
                        errors = result.errors.size,
                    )
            }
        }

        /** Parses a single-profile [json] document, persists the person, and reports the outcome. */
        fun onJsonImported(json: String) {
            personJsonImporter.parse(json).fold(
                onSuccess = { person ->
                    viewModelScope.launch {
                        messageState.value =
                            upsertPerson(person).fold(
                                onSuccess = { BirthdayMessage.ProfileImported(person.fullName) },
                                onFailure = { BirthdayMessage.ImportFailed },
                            )
                    }
                },
                onFailure = { messageState.value = BirthdayMessage.ImportFailed },
            )
        }

        /** Builds a birthday CSV export over the people who currently have a birthday. */
        fun exportCsv(): String = csvBirthdaySupport.export(peopleSnapshot)

        /** Builds a JSON backup containing the current people (no check-ins or events). */
        fun exportJson(): String =
            backupSerializer.encode(
                BackupData(
                    schemaVersion = BackupData.CURRENT_SCHEMA_VERSION,
                    people = peopleSnapshot,
                    checkIns = emptyList(),
                    events = emptyList(),
                ),
            )

        /** Clears the one-shot [message] after it has been displayed. */
        fun onMessageShown() {
            messageState.value = null
        }

        private fun List<UpcomingBirthday>.toData(): BirthdaysData =
            BirthdaysData(
                all = this,
                upcoming30 = filter { it.daysUntil <= UPCOMING_WINDOW_DAYS },
                byMonthDay = groupBy { it.birthday.monthValue * MONTH_KEY_FACTOR + it.birthday.dayOfMonth },
            )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val UPCOMING_WINDOW_DAYS = 30
            const val MONTH_KEY_FACTOR = 100
        }
    }

/** Resolves the year-agnostic calendar key used by [BirthdaysData.byMonthDay] for a given date. */
internal fun monthDayKey(month: Int, dayOfMonth: Int): Int = month * 100 + dayOfMonth

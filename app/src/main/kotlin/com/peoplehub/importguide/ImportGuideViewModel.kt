package com.peoplehub.importguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.dataio.BackupSerializer
import com.peoplehub.core.dataio.CsvBirthdaySupport
import com.peoplehub.core.dataio.PersonJsonImporter
import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.ImportStrategy
import com.peoplehub.core.domain.model.MergeReport
import com.peoplehub.core.domain.usecase.ImportBackupUseCase
import com.peoplehub.core.domain.usecase.UpsertPersonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Outcome of an import attempt, rendered as a result banner on the guide screen. */
data class ImportOutcome(val success: Boolean, val message: String)

@HiltViewModel
class ImportGuideViewModel
    @Inject
    constructor(
        private val personJsonImporter: PersonJsonImporter,
        private val backupSerializer: BackupSerializer,
        private val csvBirthdaySupport: CsvBirthdaySupport,
        private val upsertPerson: UpsertPersonUseCase,
        private val importBackup: ImportBackupUseCase,
    ) : ViewModel() {
        private val _isBusy = MutableStateFlow(false)
        val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

        private val _outcome = MutableStateFlow<ImportOutcome?>(null)
        val outcome: StateFlow<ImportOutcome?> = _outcome.asStateFlow()

        /** Imports a single person profile from an app-schema JSON object. */
        fun importPersonJson(json: String) =
            run("Couldn't read that JSON profile") {
                personJsonImporter.parse(json).fold(
                    onSuccess = { person ->
                        upsertPerson(person).fold(
                            onSuccess = { ImportOutcome(true, "Imported ${person.fullName}. Open Circle to see them.") },
                            onFailure = { ImportOutcome(false, it.message ?: "Could not save the person") },
                        )
                    },
                    onFailure = { ImportOutcome(false, it.message ?: "That JSON isn't a valid person profile") },
                )
            }

        /** Imports birthdays from a `nome,cognome,data_nascita` CSV file. */
        fun importBirthdaysCsv(csv: String) =
            run("Couldn't read that CSV file") {
                val result = csvBirthdaySupport.parse(csv)
                var added = 0
                result.people.forEach { person -> if (upsertPerson(person).isSuccess) added++ }
                val summary =
                    buildString {
                        append("Imported $added ")
                        append(if (added == 1) "person" else "people")
                        if (result.errors.isNotEmpty()) {
                            append(" · ${result.errors.size} row(s) skipped")
                        }
                        append(". Open Milestones to see them.")
                    }
                ImportOutcome(success = added > 0 || result.errors.isEmpty(), message = summary)
            }

        /** Imports a full backup JSON snapshot using the chosen [strategy]. */
        fun importBackup(json: String, strategy: ImportStrategy) =
            run("Couldn't read that backup file") {
                backupSerializer.decode(json).fold(
                    onSuccess = { data ->
                        val report = importBackup(data, strategy)
                        ImportOutcome(true, backupSummary(data, report, strategy))
                    },
                    onFailure = { ImportOutcome(false, it.message ?: "That file isn't a valid PeopleHub backup") },
                )
            }

        fun onOutcomeShown() {
            _outcome.value = null
        }

        private fun run(failureMessage: String, block: suspend () -> ImportOutcome) {
            viewModelScope.launch {
                _isBusy.value = true
                _outcome.value = runCatching { block() }.getOrElse { ImportOutcome(false, failureMessage) }
                _isBusy.value = false
            }
        }

        private fun backupSummary(data: BackupData, report: MergeReport, strategy: ImportStrategy): String =
            when (strategy) {
                ImportStrategy.REPLACE ->
                    "Replaced everything with ${data.people.size} people, ${data.events.size} events."
                ImportStrategy.MERGE ->
                    "Merged: ${report.peopleAdded} added, ${report.peopleSkipped} skipped, ${report.eventsAdded} events added."
            }
    }

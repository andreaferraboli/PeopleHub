package com.peoplehub.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.dataio.BackupSerializer
import com.peoplehub.core.dataio.CsvBirthdaySupport
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.ImportStrategy
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.domain.usecase.DeleteAllPeopleUseCase
import com.peoplehub.core.domain.usecase.ExportBackupUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetSettingsUseCase
import com.peoplehub.core.domain.usecase.ImportBackupUseCase
import com.peoplehub.core.domain.usecase.UpdateBirthdayRemindersUseCase
import com.peoplehub.core.domain.usecase.UpdateDailyReminderHourUseCase
import com.peoplehub.core.domain.usecase.UpdateDefaultThresholdUseCase
import com.peoplehub.core.domain.usecase.UpdateExactAlarmsUseCase
import com.peoplehub.work.PeopleHubWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for the settings ("Vault") screen. */
data class SettingsUiState(
    val settings: AppSettings,
    val isBusy: Boolean,
    val message: String?,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        getSettings: GetSettingsUseCase,
        private val getPeople: GetPeopleUseCase,
        private val updateDefaultThreshold: UpdateDefaultThresholdUseCase,
        private val updateBirthdayReminders: UpdateBirthdayRemindersUseCase,
        private val updateExactAlarms: UpdateExactAlarmsUseCase,
        private val updateDailyReminderHour: UpdateDailyReminderHourUseCase,
        private val exportBackup: ExportBackupUseCase,
        private val importBackup: ImportBackupUseCase,
        private val deleteAllPeople: DeleteAllPeopleUseCase,
        private val backupSerializer: BackupSerializer,
        private val csvBirthdaySupport: CsvBirthdaySupport,
        private val workScheduler: PeopleHubWorkScheduler,
    ) : ViewModel() {
        private val isBusy = MutableStateFlow(false)
        private val message = MutableStateFlow<String?>(null)

        val state: StateFlow<SettingsUiState> =
            combine(
                getSettings(),
                isBusy,
                message,
            ) { settings, busy, msg ->
                SettingsUiState(settings = settings, isBusy = busy, message = msg)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(AppSettings(), isBusy = false, message = null),
            )

        fun onThresholdChange(warningDays: Int, criticalDays: Int) {
            viewModelScope.launch { updateDefaultThreshold(warningDays, criticalDays) }
        }

        fun onToggleReminder(offset: ReminderOffset, enabled: Boolean) {
            viewModelScope.launch {
                val current = state.value.settings.birthdayReminderOffsets
                val updated = if (enabled) current + offset else current - offset
                updateBirthdayReminders(updated)
            }
        }

        fun onToggleExactAlarms(enabled: Boolean) {
            viewModelScope.launch { updateExactAlarms(enabled) }
        }

        fun onReminderHourChange(hour: Int) {
            viewModelScope.launch { updateDailyReminderHour(hour) }
        }

        /** Builds the full backup JSON (called from the screen before writing it to the chosen file). */
        suspend fun buildBackupJson(): String = backupSerializer.encode(exportBackup())

        /** Builds a Google-Contacts-compatible CSV of everyone with a birthday. */
        suspend fun buildBirthdaysCsv(): String =
            csvBirthdaySupport.export(getPeople(PeopleFilter()).first())

        /** Decodes and imports a full backup using the chosen [strategy]. */
        fun importFullBackup(json: String, strategy: ImportStrategy) {
            viewModelScope.launch {
                isBusy.value = true
                backupSerializer.decode(json).fold(
                    onSuccess = { data ->
                        val report = importBackup(data, strategy)
                        message.value = importSummary(data, report.peopleAdded, report.peopleSkipped, strategy)
                        workScheduler.updateWidgetsNow()
                    },
                    onFailure = { message.value = it.message ?: "Import failed" },
                )
                isBusy.value = false
            }
        }

        /** Deletes every person (and their check-ins). Used by the Settings danger zone. */
        fun deleteEveryone() {
            viewModelScope.launch {
                isBusy.value = true
                val removed = getPeople(PeopleFilter()).first().size
                deleteAllPeople()
                workScheduler.updateWidgetsNow()
                message.value = "Deleted $removed people"
                isBusy.value = false
            }
        }

        fun onMessageShown() {
            message.value = null
        }

        private fun importSummary(data: BackupData, added: Int, skipped: Int, strategy: ImportStrategy): String =
            when (strategy) {
                ImportStrategy.REPLACE -> "Replaced with ${data.people.size} people"
                ImportStrategy.MERGE -> "Merged: $added added, $skipped skipped"
            }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

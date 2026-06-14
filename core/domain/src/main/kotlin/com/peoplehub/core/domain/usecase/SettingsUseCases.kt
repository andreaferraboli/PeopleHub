package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the current application settings. */
class GetSettingsUseCase
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) {
        operator fun invoke(): Flow<AppSettings> = repository.settings
    }

/** Updates the global default check-in thresholds, coercing them into a valid order. */
class UpdateDefaultThresholdUseCase
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) {
        suspend operator fun invoke(warningDays: Int, criticalDays: Int) {
            val warning = warningDays.coerceAtLeast(1)
            val critical = criticalDays.coerceAtLeast(warning + 1)
            repository.setDefaultThreshold(CheckInThreshold(warning, critical))
        }
    }

/** Replaces the set of globally enabled birthday reminder offsets. */
class UpdateBirthdayRemindersUseCase
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) {
        suspend operator fun invoke(offsets: Set<ReminderOffset>) = repository.setBirthdayReminderOffsets(offsets)
    }

/** Toggles the preference for exact alarms. */
class UpdateExactAlarmsUseCase
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) {
        suspend operator fun invoke(enabled: Boolean) = repository.setUseExactAlarms(enabled)
    }

/** Updates the hour of day (0–23) for the daily reminder sweep. */
class UpdateDailyReminderHourUseCase
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) {
        suspend operator fun invoke(hour: Int) = repository.setDailyReminderHour(hour.coerceIn(0, 23))
    }

package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.ReminderOffset
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to user preferences, backed by DataStore.
 */
interface SettingsRepository {

    /** Reactive stream of the current [AppSettings]. */
    val settings: Flow<AppSettings>

    /** Updates the global default check-in thresholds. */
    suspend fun setDefaultThreshold(threshold: CheckInThreshold)

    /** Replaces the set of globally enabled birthday reminder offsets. */
    suspend fun setBirthdayReminderOffsets(offsets: Set<ReminderOffset>)

    /** Toggles the preference for exact alarms. */
    suspend fun setUseExactAlarms(enabled: Boolean)

    /** Updates the hour of day (0–23) for the daily reminder sweep. */
    suspend fun setDailyReminderHour(hour: Int)
}

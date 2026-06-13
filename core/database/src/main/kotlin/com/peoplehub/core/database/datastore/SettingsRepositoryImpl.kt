package com.peoplehub.core.database.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** DataStore-backed [SettingsRepository]. */
internal class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val warning = prefs[Keys.WARNING_DAYS] ?: CheckInThreshold.Default.warningDays
        val critical = prefs[Keys.CRITICAL_DAYS] ?: CheckInThreshold.Default.criticalDays
        val offsets = prefs[Keys.REMINDER_OFFSETS]
            ?.mapNotNull { name -> runCatching { ReminderOffset.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: AppSettings().birthdayReminderOffsets
        AppSettings(
            defaultCheckInThreshold = CheckInThreshold(warning, critical),
            birthdayReminderOffsets = offsets,
            useExactAlarms = prefs[Keys.USE_EXACT_ALARMS] ?: true,
            dailyReminderHour = prefs[Keys.DAILY_REMINDER_HOUR] ?: AppSettings.DEFAULT_REMINDER_HOUR,
        )
    }

    override suspend fun setDefaultThreshold(threshold: CheckInThreshold) {
        dataStore.edit { prefs ->
            prefs[Keys.WARNING_DAYS] = threshold.warningDays
            prefs[Keys.CRITICAL_DAYS] = threshold.criticalDays
        }
    }

    override suspend fun setBirthdayReminderOffsets(offsets: Set<ReminderOffset>) {
        dataStore.edit { prefs ->
            prefs[Keys.REMINDER_OFFSETS] = offsets.map { it.name }.toSet()
        }
    }

    override suspend fun setUseExactAlarms(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.USE_EXACT_ALARMS] = enabled }
    }

    override suspend fun setDailyReminderHour(hour: Int) {
        dataStore.edit { prefs -> prefs[Keys.DAILY_REMINDER_HOUR] = hour }
    }

    private object Keys {
        val WARNING_DAYS = intPreferencesKey("warning_days")
        val CRITICAL_DAYS = intPreferencesKey("critical_days")
        val REMINDER_OFFSETS = stringSetPreferencesKey("reminder_offsets")
        val USE_EXACT_ALARMS = booleanPreferencesKey("use_exact_alarms")
        val DAILY_REMINDER_HOUR = intPreferencesKey("daily_reminder_hour")
    }
}

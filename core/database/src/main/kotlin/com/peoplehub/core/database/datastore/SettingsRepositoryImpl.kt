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
internal class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            dataStore.data.map { prefs ->
                val warning = prefs[Keys.WARNING_DAYS] ?: CheckInThreshold.Default.warningDays
                val critical = prefs[Keys.CRITICAL_DAYS] ?: CheckInThreshold.Default.criticalDays
                val offsets = readOffsets(prefs)
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
                prefs[Keys.REMINDER_OFFSETS_V2] = offsets.map { it.name }.toSet()
            }
        }

        /**
         * Reads the enabled reminder offsets, migrating the pre-`SAME_DAY` v1 set on the fly.
         *
         * Before `SAME_DAY` existed the same-day greeting fired unconditionally, so a v1 set is
         * upgraded by adding it — otherwise upgrading users would silently stop being reminded on the
         * birthday itself. Writes always go to the v2 key, so a user who then unticks "on the day"
         * keeps that choice.
         */
        private fun readOffsets(prefs: Preferences): Set<ReminderOffset> {
            prefs[Keys.REMINDER_OFFSETS_V2]?.let { return it.toOffsets() }
            prefs[Keys.REMINDER_OFFSETS]?.let { return it.toOffsets() + ReminderOffset.SAME_DAY }
            return AppSettings().birthdayReminderOffsets
        }

        private fun Set<String>.toOffsets(): Set<ReminderOffset> =
            mapNotNull { name -> runCatching { ReminderOffset.valueOf(name) }.getOrNull() }.toSet()

        override suspend fun setUseExactAlarms(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.USE_EXACT_ALARMS] = enabled }
        }

        override suspend fun setDailyReminderHour(hour: Int) {
            dataStore.edit { prefs -> prefs[Keys.DAILY_REMINDER_HOUR] = hour }
        }

        private object Keys {
            val WARNING_DAYS = intPreferencesKey("warning_days")
            val CRITICAL_DAYS = intPreferencesKey("critical_days")

            /** Legacy (pre-`SAME_DAY`) set, read-only — see `readOffsets`. */
            val REMINDER_OFFSETS = stringSetPreferencesKey("reminder_offsets")
            val REMINDER_OFFSETS_V2 = stringSetPreferencesKey("reminder_offsets_v2")
            val USE_EXACT_ALARMS = booleanPreferencesKey("use_exact_alarms")
            val DAILY_REMINDER_HOUR = intPreferencesKey("daily_reminder_hour")
        }
    }

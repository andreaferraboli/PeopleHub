package com.peoplehub.core.database.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.peoplehub.core.domain.repository.ReminderStateRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** DataStore-backed [ReminderStateRepository]. Dates are stored as epoch days. */
internal class ReminderStateRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ReminderStateRepository {
        override suspend fun lastBirthdaySweepDate(): LocalDate? =
            dataStore.data.first()[Keys.LAST_BIRTHDAY_SWEEP_EPOCH_DAY]?.let(LocalDate::ofEpochDay)

        override suspend fun setLastBirthdaySweepDate(date: LocalDate) {
            dataStore.edit { prefs -> prefs[Keys.LAST_BIRTHDAY_SWEEP_EPOCH_DAY] = date.toEpochDay() }
        }

        override suspend fun lastRelationshipSweepDate(): LocalDate? =
            dataStore.data.first()[Keys.LAST_RELATIONSHIP_SWEEP_EPOCH_DAY]?.let(LocalDate::ofEpochDay)

        override suspend fun setLastRelationshipSweepDate(date: LocalDate) {
            dataStore.edit { prefs -> prefs[Keys.LAST_RELATIONSHIP_SWEEP_EPOCH_DAY] = date.toEpochDay() }
        }

        override suspend fun scheduledReminderHour(): Int? = dataStore.data.first()[Keys.SCHEDULED_REMINDER_HOUR]

        override suspend fun setScheduledReminderHour(hour: Int) {
            dataStore.edit { prefs -> prefs[Keys.SCHEDULED_REMINDER_HOUR] = hour }
        }

        private object Keys {
            val LAST_BIRTHDAY_SWEEP_EPOCH_DAY = longPreferencesKey("last_birthday_sweep_epoch_day")
            val LAST_RELATIONSHIP_SWEEP_EPOCH_DAY = longPreferencesKey("last_relationship_sweep_epoch_day")
            val SCHEDULED_REMINDER_HOUR = intPreferencesKey("scheduled_reminder_hour")
        }
    }

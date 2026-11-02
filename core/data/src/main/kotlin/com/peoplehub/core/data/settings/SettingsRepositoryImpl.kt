package com.peoplehub.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.CloudWrite
import com.peoplehub.core.data.mapping.SettingsMapper
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import javax.inject.Inject

/**
 * [SettingsRepository] split between the cloud and the phone: the check-in thresholds, the birthday
 * offsets and the reminder hour live in `settings/main` (shared with the MCP server, which also reads
 * the phone's `timeZone` written alongside), while "use exact alarms" is a device preference in the
 * `peoplehub_settings` DataStore. Signed out, or before the document exists, the cloud part reads as
 * the defaults.
 */
internal class SettingsRepositoryImpl
    @Inject
    constructor(
        private val store: CloudStore,
        private val dataStore: DataStore<Preferences>,
        private val clock: Clock,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            combine(store.observeDocument(Cloud.SETTINGS, Cloud.SETTINGS_MAIN), dataStore.data) { doc, prefs ->
                val cloud = SettingsMapper.fromDoc(doc?.data)
                AppSettings(
                    defaultCheckInThreshold = cloud.threshold,
                    birthdayReminderOffsets = cloud.offsets,
                    useExactAlarms = prefs[USE_EXACT_ALARMS] ?: true,
                    dailyReminderHour = cloud.dailyReminderHour,
                )
            }

        override suspend fun setDefaultThreshold(threshold: CheckInThreshold) = write(SettingsMapper.thresholdDoc(threshold))

        override suspend fun setBirthdayReminderOffsets(offsets: Set<ReminderOffset>) = write(SettingsMapper.offsetsDoc(offsets))

        override suspend fun setUseExactAlarms(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[USE_EXACT_ALARMS] = enabled }
        }

        override suspend fun setDailyReminderHour(hour: Int) = write(SettingsMapper.hourDoc(hour))

        private suspend fun write(fields: Map<String, Any?>) =
            store.commit(
                listOf(CloudWrite.Put(Cloud.SETTINGS, Cloud.SETTINGS_MAIN, fields + SettingsMapper.timeZoneDoc(clock.zone.id))),
            )

        private companion object {
            val USE_EXACT_ALARMS = booleanPreferencesKey("use_exact_alarms")
        }
    }

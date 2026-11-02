package com.peoplehub.core.sync.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.peoplehub.core.sync.engine.CursorStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the sync engine's own DataStore (`peoplehub_sync`), separate from the app settings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SyncDataStore

/** The persisted part of [com.peoplehub.core.domain.model.SyncStatus]. */
internal data class SyncPrefsSnapshot(
    val initialDoneUids: Set<String>,
    val lastSyncAt: Long?,
    val lastError: String?,
)

/**
 * The sync engine's bookkeeping, kept out of the app settings DataStore on purpose (none of it is
 * synced): the install id behind `updatedBy`, the per-account "first upload or download done" flag,
 * per-account pull cursors, the last synced settings, and the last outcome.
 */
@Singleton
internal class SyncPreferences
    @Inject
    constructor(
        @SyncDataStore private val dataStore: DataStore<Preferences>,
    ) : CursorStore {
        val snapshot: Flow<SyncPrefsSnapshot> =
            dataStore.data.map { prefs ->
                SyncPrefsSnapshot(
                    initialDoneUids =
                        prefs
                            .asMap()
                            .keys
                            .map { it.name }
                            .filter { it.startsWith(INITIAL_DONE_PREFIX) && prefs[stringPreferencesKey(it)] == TRUE }
                            .map { it.removePrefix(INITIAL_DONE_PREFIX) }
                            .toSet(),
                    lastSyncAt = prefs[LAST_SYNC_AT],
                    lastError = prefs[LAST_ERROR],
                )
            }

        /** A random id for this install, drawn once; documents it writes carry `android:<id>`. */
        suspend fun installId(): String {
            dataStore.data.first()[INSTALL_ID]?.let { return it }
            val fresh = UUID.randomUUID().toString()
            dataStore.edit { prefs -> if (prefs[INSTALL_ID] == null) prefs[INSTALL_ID] = fresh }
            return dataStore.data.first()[INSTALL_ID] ?: fresh
        }

        suspend fun isInitialDone(uid: String): Boolean = dataStore.data.first()[initialDoneKey(uid)] == TRUE

        suspend fun setInitialDone(uid: String) {
            dataStore.edit { it[initialDoneKey(uid)] = TRUE }
        }

        override suspend fun cursor(uid: String, collection: String): Long = dataStore.data.first()[cursorKey(uid, collection)] ?: 0L

        override suspend fun setCursor(uid: String, collection: String, micros: Long) {
            dataStore.edit { it[cursorKey(uid, collection)] = micros }
        }

        /** Forgets every cursor of [uid] (and its synced-settings snapshot), before a full download. */
        suspend fun resetAccount(uid: String) {
            dataStore.edit { prefs ->
                prefs
                    .asMap()
                    .keys
                    .filter { it.name.startsWith("$CURSOR_PREFIX$uid/") || it.name == settingsKey(uid).name }
                    .forEach { prefs.remove(it) }
            }
        }

        /** The canonical form of the settings last sent to or received from the cloud for [uid]. */
        suspend fun syncedSettings(uid: String): String? = dataStore.data.first()[settingsKey(uid)]

        suspend fun setSyncedSettings(uid: String, canonical: String) {
            dataStore.edit {
                it[settingsKey(uid)] = canonical
                it.remove(SETTINGS_CHANGED_AT)
            }
        }

        /** When the local settings were first seen differing from the synced ones, or `null`. */
        suspend fun settingsChangedAt(): Long? = dataStore.data.first()[SETTINGS_CHANGED_AT]

        suspend fun markSettingsChanged(at: Long) {
            dataStore.edit { if (it[SETTINGS_CHANGED_AT] == null) it[SETTINGS_CHANGED_AT] = at }
        }

        suspend fun recordSuccess(at: Long) {
            dataStore.edit {
                it[LAST_SYNC_AT] = at
                it.remove(LAST_ERROR)
            }
        }

        suspend fun recordError(message: String) {
            dataStore.edit { it[LAST_ERROR] = message }
        }

        private fun initialDoneKey(uid: String) = stringPreferencesKey("$INITIAL_DONE_PREFIX$uid")

        private fun cursorKey(uid: String, collection: String) = longPreferencesKey("$CURSOR_PREFIX$uid/$collection")

        private fun settingsKey(uid: String) = stringPreferencesKey("synced_settings_$uid")

        private companion object {
            const val INITIAL_DONE_PREFIX = "sync_initial_upload_done_"
            const val CURSOR_PREFIX = "cursor_"
            const val TRUE = "true"
            val INSTALL_ID = stringPreferencesKey("install_id")
            val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
            val LAST_ERROR = stringPreferencesKey("last_error")
            val SETTINGS_CHANGED_AT = longPreferencesKey("settings_changed_at")
        }
    }

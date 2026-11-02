package com.peoplehub.core.sync.engine

import com.peoplehub.core.database.sync.SyncCollections
import com.peoplehub.core.domain.repository.SettingsRepository
import com.peoplehub.core.sync.mapping.SettingsMapper
import com.peoplehub.core.sync.mapping.SyncedSettings
import com.peoplehub.core.sync.prefs.SyncPreferences
import kotlinx.coroutines.flow.first

/**
 * Syncs `settings/main`. Settings live in DataStore, not Room, so there is no trigger: a change is
 * noticed by comparing the local values with the last synced snapshot, and its time is recorded the
 * first time the engine sees the difference ([SyncPreferences.markSettingsChanged]). The same
 * last-write-wins rule as the documents applies.
 */
internal class SettingsSync(
    private val remote: RemoteStore,
    private val settings: SettingsRepository,
    private val prefs: SyncPreferences,
    private val ownTag: String,
    private val timeZone: () -> String,
    private val now: () -> Long,
) {
    /** The local synced settings, with the phone's current time zone. */
    suspend fun local(): SyncedSettings = SettingsMapper.fromApp(settings.settings.first(), timeZone())

    /** Whether the local settings differ from what was last synced for [uid]. */
    suspend fun hasLocalChange(uid: String): Boolean = local().canonical != prefs.syncedSettings(uid)

    /**
     * Applies a newer cloud version. With [force] (a full download) the cloud version is taken even
     * if the phone wrote it or holds a newer local change. Returns whether the local settings changed.
     */
    suspend fun pull(uid: String, force: Boolean = false): Boolean {
        val cursor = prefs.cursor(uid, SyncCollections.SETTINGS)
        val doc =
            remote
                .fetchChangedSince(uid, SyncCollections.SETTINGS, cursor)
                .filter { it.id == MAIN_DOC }
                .maxByOrNull { it.serverUpdatedMicros } ?: return false
        prefs.setCursor(uid, SyncCollections.SETTINGS, doc.serverUpdatedMicros)
        if (doc.deleted || (!force && doc.updatedBy == ownTag)) return false
        val current = local()
        val pendingAt = if (current.canonical != prefs.syncedSettings(uid)) prefs.settingsChangedAt() ?: now() else null
        if (!force && !ConflictRule.remoteWins(doc.updatedAt, pendingAt)) return false
        val incoming = SettingsMapper.fromDoc(doc.data, current)
        settings.setDefaultThreshold(incoming.threshold)
        settings.setBirthdayReminderOffsets(incoming.offsets)
        settings.setDailyReminderHour(incoming.dailyReminderHour)
        prefs.setSyncedSettings(uid, incoming.canonical)
        return incoming.copy(timeZone = current.timeZone).canonical != current.canonical
    }

    /** Sends the local settings when they differ from the last synced ones (or always, with [force]). */
    suspend fun push(uid: String, force: Boolean = false) {
        val current = local()
        if (!force && current.canonical == prefs.syncedSettings(uid)) return
        val changedAt = prefs.settingsChangedAt() ?: now()
        val data =
            current.toDoc() +
                mapOf(
                    SyncFields.UPDATED_AT to changedAt,
                    SyncFields.DELETED to false,
                    SyncFields.UPDATED_BY to ownTag,
                )
        remote.commit(uid, listOf(RemoteWrite(SyncCollections.SETTINGS, MAIN_DOC, data, merge = false)))
        prefs.setSyncedSettings(uid, current.canonical)
    }

    private companion object {
        const val MAIN_DOC = "main"
    }
}

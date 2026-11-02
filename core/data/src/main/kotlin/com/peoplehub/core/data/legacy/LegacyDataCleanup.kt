package com.peoplehub.core.data.legacy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudDoc
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.local.EventMedia
import com.peoplehub.core.data.local.LocalMedia
import com.peoplehub.core.data.mapping.long
import com.peoplehub.core.data.mapping.string
import com.peoplehub.core.domain.model.CropTransform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-off removal of the local database the app used before it kept its data only in the cloud
 * (Room `peoplehub.db`, plus the `peoplehub_sync` DataStore of the old sync engine).
 *
 * Runs after sign-in, and only does something while that file exists:
 * - the cloud already has people: the photo and event-background references stored in the old tables
 *   are copied into the phone's photo map (matched by document id, else by name and birthday / title
 *   and date), then the database and the sync DataStore are deleted. The photo files stay where they are.
 * - the cloud is empty but the old database still has people: nothing is deleted and nothing uploaded,
 *   the file is left for a manual recovery and the case is logged.
 * - both are empty: the file is deleted.
 */
@Singleton
class LegacyDataCleanup
    @Inject
    internal constructor(
        @ApplicationContext private val context: Context,
        private val store: CloudStore,
        private val media: LocalMedia,
    ) {
        private val lock = Mutex()

        /** Does the cleanup if the old database is still there; failures are logged and retried next launch. */
        suspend fun runIfNeeded() =
            lock.withLock {
                withContext(Dispatchers.IO) {
                    val file = context.getDatabasePath(DATABASE_NAME)
                    if (!file.exists()) return@withContext
                    runCatching { cleanUp(file) }.onFailure { Log.w(TAG, "Legacy cleanup postponed", it) }
                }
            }

        private suspend fun cleanUp(file: File) {
            val legacy = readLegacy(file)
            if (!store.hasLiveDocuments(Cloud.PEOPLE)) {
                if (legacy.peopleCount > 0) {
                    Log.w(TAG, "Cloud is empty but the old database has ${legacy.peopleCount} people: left untouched")
                    return
                }
            } else {
                val people = store.observe(Cloud.PEOPLE).first()
                val events = store.observe(Cloud.EVENTS).first()
                val photos = matchPhotos(legacy.people, people)
                val backgrounds = matchEvents(legacy.events, events)
                val pinned = legacy.events.firstOrNull { it.pinned }?.let { matchEvent(it, events)?.id }
                media.putAll(photos, backgrounds.mapValues { it.value.media }, pinned)
                Log.i(TAG, "Recovered ${photos.size} photos and ${backgrounds.size} event backgrounds")
            }
            context.deleteDatabase(DATABASE_NAME)
            File(context.filesDir, SYNC_DATASTORE).delete()
        }

        private fun matchPhotos(rows: List<LegacyPerson>, people: List<CloudDoc>): Map<String, String> {
            val ids = people.map { it.id }.toSet()
            val byKey =
                people.associateBy { personKey(it.data.string("firstName"), it.data.string("lastName"), it.data.string("birthday")) }
            return rows
                .filter { it.photoPath != null && File(it.photoPath).exists() }
                .mapNotNull { row ->
                    val id = row.remoteId?.takeIf { it in ids } ?: byKey[personKey(row.firstName, row.lastName, row.birthday)]?.id
                    id?.let { it to requireNotNull(row.photoPath) }
                }.toMap()
        }

        private fun matchEvents(rows: List<LegacyEvent>, events: List<CloudDoc>): Map<String, LegacyEvent> =
            rows
                .filter { it.media.imagePath != null && File(it.media.imagePath).exists() }
                .mapNotNull { row -> matchEvent(row, events)?.let { it.id to row } }
                .toMap()

        private fun matchEvent(row: LegacyEvent, events: List<CloudDoc>): CloudDoc? =
            events.firstOrNull { it.id == row.remoteId }
                ?: events.firstOrNull { it.data.string("title") == row.title && it.data.long("at") == row.at }

        private fun personKey(first: String?, last: String?, birthday: String?): String =
            "${first.orEmpty().trim().lowercase()}|${last.orEmpty().trim().lowercase()}|${birthday.orEmpty()}"

        private fun readLegacy(file: File): LegacyData =
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val hasRemoteIds = db.hasColumn("person", "remote_id")
                val people =
                    db
                        .rawQuery(
                            "SELECT first_name, last_name, birthday, photo_path${if (hasRemoteIds) ", remote_id" else ""} FROM person",
                            null,
                        ).use { c ->
                            buildList {
                                while (c.moveToNext()) {
                                    add(
                                        LegacyPerson(
                                            firstName = c.getString(0),
                                            lastName = c.getString(1),
                                            birthday = c.getStringOrNull(2),
                                            photoPath = c.getStringOrNull(3),
                                            remoteId = if (hasRemoteIds) c.getStringOrNull(4) else null,
                                        ),
                                    )
                                }
                            }
                        }
                val eventRemoteIds = db.hasColumn("event", "remote_id")
                val events =
                    db
                        .rawQuery(
                            "SELECT title, date_time_epoch_millis, background_image_path, background_source_path, " +
                                "background_zoom, background_pan_x, background_pan_y, pinned_to_widget" +
                                "${if (eventRemoteIds) ", remote_id" else ""} FROM event",
                            null,
                        ).use { c ->
                            buildList {
                                while (c.moveToNext()) {
                                    add(
                                        LegacyEvent(
                                            title = c.getString(0),
                                            at = c.getLong(1),
                                            media =
                                                EventMedia(
                                                    imagePath = c.getStringOrNull(2),
                                                    sourcePath = c.getStringOrNull(3),
                                                    crop = CropTransform(c.getFloat(4), c.getFloat(5), c.getFloat(6)),
                                                ),
                                            pinned = c.getInt(7) != 0,
                                            remoteId = if (eventRemoteIds) c.getStringOrNull(8) else null,
                                        ),
                                    )
                                }
                            }
                        }
                LegacyData(people, events)
            }

        private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
            rawQuery("PRAGMA table_info($table)", null).use { c ->
                val nameIndex = c.getColumnIndex("name")
                generateSequence { if (c.moveToNext()) c.getString(nameIndex) else null }.any { it == column }
            }

        private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

        private data class LegacyPerson(
            val firstName: String?,
            val lastName: String?,
            val birthday: String?,
            val photoPath: String?,
            val remoteId: String?,
        )

        private data class LegacyEvent(
            val title: String?,
            val at: Long,
            val media: EventMedia,
            val pinned: Boolean,
            val remoteId: String?,
        )

        private data class LegacyData(
            val people: List<LegacyPerson>,
            val events: List<LegacyEvent>,
        ) {
            val peopleCount: Int get() = people.size
        }

        private companion object {
            const val TAG = "LegacyCleanup"
            const val DATABASE_NAME = "peoplehub.db"
            const val SYNC_DATASTORE = "datastore/peoplehub_sync.preferences_pb"
        }
    }

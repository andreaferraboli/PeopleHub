package com.peoplehub.core.sync.local

import androidx.room.withTransaction
import com.peoplehub.core.database.PeopleHubDatabase
import com.peoplehub.core.database.dao.CheckInDao
import com.peoplehub.core.database.dao.EventDao
import com.peoplehub.core.database.dao.PeopleDao
import com.peoplehub.core.database.dao.ReminderDao
import com.peoplehub.core.database.dao.SyncDao
import com.peoplehub.core.database.entity.PersonFtsEntity
import com.peoplehub.core.database.entity.newRemoteId
import com.peoplehub.core.database.sync.SyncCollections
import com.peoplehub.core.sync.engine.LocalSyncStore
import com.peoplehub.core.sync.engine.PendingChange
import com.peoplehub.core.sync.engine.RemoteApplier
import com.peoplehub.core.sync.engine.UpsertResult
import com.peoplehub.core.sync.mapping.DocumentMapper
import com.peoplehub.core.sync.mapping.string
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LocalSyncStore] on the app's Room database.
 *
 * Cloud changes are written in one transaction with `sync_state.applying_remote` set, so the outbox
 * triggers stay silent. After the writes it re-derives what the app keeps denormalised: the search
 * index of every person written, and the last check-in of every person whose check-ins changed.
 */
@Singleton
internal class RoomLocalSyncStore
    @Inject
    constructor(
        private val database: PeopleHubDatabase,
        private val syncDao: SyncDao,
        private val peopleDao: PeopleDao,
        private val checkInDao: CheckInDao,
        private val eventDao: EventDao,
        private val reminderDao: ReminderDao,
    ) : LocalSyncStore {
        override fun observePendingCount(): Flow<Int> = syncDao.observePendingCount()

        override suspend fun pendingChanges(limit: Int): List<PendingChange> =
            syncDao.pendingChanges(limit).map { PendingChange(it.collection, it.remoteId, it.changedAt) }

        override suspend fun documentFor(change: PendingChange): Map<String, Any?>? {
            val id = change.remoteId
            return when (change.collection) {
                SyncCollections.PEOPLE -> syncDao.personByRemoteId(id)?.let(DocumentMapper::personToDoc)
                SyncCollections.CHECK_INS ->
                    syncDao.checkInByRemoteId(id)?.let { row ->
                        syncDao.personRemoteIdOf(row.personId)?.let { DocumentMapper.checkInToDoc(row, it) }
                    }
                SyncCollections.EVENTS ->
                    syncDao.eventByRemoteId(id)?.let { row ->
                        DocumentMapper.eventToDoc(row, row.personId?.let { syncDao.personRemoteIdOf(it) })
                    }
                SyncCollections.REMINDERS ->
                    syncDao.reminderByRemoteId(id)?.let { row ->
                        syncDao.personRemoteIdOf(row.personId)?.let { DocumentMapper.reminderToDoc(row, it) }
                    }
                SyncCollections.REMINDER_COMPLETIONS ->
                    syncDao.completionByRemoteId(id)?.let { row ->
                        syncDao.reminderRemoteIdOf(row.reminderId)?.let { DocumentMapper.completionToDoc(row, it) }
                    }
                else -> null
            }
        }

        override suspend fun acknowledge(changes: List<PendingChange>) {
            database.withTransaction {
                changes.forEach { syncDao.acknowledge(it.collection, it.remoteId, it.changedAt) }
            }
        }

        override suspend fun enqueueEverything(now: Long) {
            database.withTransaction {
                syncDao.enqueueAllPeople(now)
                syncDao.enqueueAllReminders(now)
                syncDao.enqueueAllEvents(now)
                syncDao.enqueueAllCheckIns(now)
                syncDao.enqueueAllCompletions(now)
            }
        }

        override suspend fun wipeForDownload() {
            database.withTransaction {
                syncDao.ensureStateRow()
                syncDao.setApplyingRemote(true)
                try {
                    syncDao.wipeAll()
                } finally {
                    syncDao.setApplyingRemote(false)
                }
            }
        }

        override suspend fun <T> applyRemote(block: suspend RemoteApplier.() -> T): T =
            database.withTransaction {
                syncDao.ensureStateRow()
                syncDao.setApplyingRemote(true)
                try {
                    val applier = RoomApplier()
                    val result = applier.block()
                    applier.finish()
                    result
                } finally {
                    syncDao.setApplyingRemote(false)
                }
            }

        /** Writes cloud documents into Room; used only inside [applyRemote]'s transaction. */
        private inner class RoomApplier : RemoteApplier {
            /** People whose check-ins were added, moved or removed: their last check-in is re-derived. */
            private val checkInPeople = HashSet<Long>()

            override suspend fun pendingChangedAt(collection: String, remoteId: String): Long? =
                syncDao.pendingChangedAt(collection, remoteId)

            override suspend fun discardPending(collection: String, remoteId: String) = syncDao.discard(collection, remoteId)

            override suspend fun upsert(
                collection: String,
                remoteId: String,
                data: Map<String, Any?>,
                allowMissingParent: Boolean,
            ): UpsertResult =
                when (collection) {
                    SyncCollections.PEOPLE -> upsertPerson(remoteId, data)
                    SyncCollections.CHECK_INS -> upsertCheckIn(remoteId, data)
                    SyncCollections.EVENTS -> upsertEvent(remoteId, data, allowMissingParent)
                    SyncCollections.REMINDERS -> upsertReminder(remoteId, data)
                    SyncCollections.REMINDER_COMPLETIONS -> upsertCompletion(remoteId, data)
                    else -> UpsertResult.APPLIED
                }

            override suspend fun delete(collection: String, remoteId: String) {
                when (collection) {
                    SyncCollections.PEOPLE ->
                        syncDao.personIdOf(remoteId)?.let { id ->
                            peopleDao.deleteFts(id)
                            syncDao.deletePerson(remoteId)
                            checkInPeople -= id
                        }
                    SyncCollections.CHECK_INS ->
                        syncDao.checkInByRemoteId(remoteId)?.let { row ->
                            syncDao.deleteCheckIn(remoteId)
                            checkInPeople += row.personId
                        }
                    SyncCollections.EVENTS -> syncDao.deleteEvent(remoteId)
                    SyncCollections.REMINDERS -> syncDao.deleteReminder(remoteId)
                    SyncCollections.REMINDER_COMPLETIONS -> syncDao.deleteCompletion(remoteId)
                }
            }

            suspend fun finish() {
                checkInPeople.toList().chunked(SQL_VARIABLE_CHUNK).forEach { syncDao.recomputeLastCheckIn(it) }
            }

            private suspend fun upsertPerson(remoteId: String, data: Map<String, Any?>): UpsertResult {
                val existing = syncDao.personByRemoteId(remoteId)?.person
                val rows = DocumentMapper.personFromDoc(remoteId, data, existing)
                val id =
                    if (existing == null) {
                        peopleDao.insertPerson(rows.person)
                    } else {
                        peopleDao.updatePerson(rows.person)
                        existing.id
                    }
                peopleDao.deleteTagsFor(id)
                peopleDao.insertTags(rows.tagEntities(id))
                peopleDao.deleteInterestsFor(id)
                peopleDao.insertInterests(rows.interestEntities(id))
                peopleDao.upsertFts(
                    PersonFtsEntity(
                        rowId = id,
                        firstName = rows.person.firstName,
                        lastName = rows.person.lastName,
                        notes = rows.person.notes,
                    ),
                )
                return UpsertResult.APPLIED
            }

            private suspend fun upsertCheckIn(remoteId: String, data: Map<String, Any?>): UpsertResult {
                val personId = data.string("personId")?.let { syncDao.personIdOf(it) } ?: return UpsertResult.ORPHAN
                val existing = syncDao.checkInByRemoteId(remoteId)
                val outingRemoteId = data.string("outingId") ?: existing?.outingRemoteId ?: newRemoteId()
                val outingId =
                    if (existing != null && existing.outingRemoteId == outingRemoteId) {
                        existing.outingId
                    } else {
                        syncDao.outingIdOf(outingRemoteId) ?: syncDao.nextOutingId()
                    }
                val row = DocumentMapper.checkInFromDoc(remoteId, data, existing, personId, outingId, outingRemoteId)
                if (existing == null) checkInDao.insert(row) else checkInDao.update(row)
                checkInPeople += personId
                existing?.let { checkInPeople += it.personId }
                return UpsertResult.APPLIED
            }

            private suspend fun upsertEvent(
                remoteId: String,
                data: Map<String, Any?>,
                allowMissingParent: Boolean,
            ): UpsertResult {
                val personRemoteId = data.string("personId")
                val personId = personRemoteId?.let { syncDao.personIdOf(it) }
                if (personRemoteId != null && personId == null && !allowMissingParent) return UpsertResult.ORPHAN
                val existing = syncDao.eventByRemoteId(remoteId)
                val row = DocumentMapper.eventFromDoc(remoteId, data, existing, personId)
                if (existing == null) eventDao.insert(row) else eventDao.update(row)
                return UpsertResult.APPLIED
            }

            private suspend fun upsertReminder(remoteId: String, data: Map<String, Any?>): UpsertResult {
                val personId = data.string("personId")?.let { syncDao.personIdOf(it) } ?: return UpsertResult.ORPHAN
                val existing = syncDao.reminderByRemoteId(remoteId)
                val row = DocumentMapper.reminderFromDoc(remoteId, data, existing, personId)
                if (existing == null) reminderDao.insert(row) else reminderDao.update(row)
                return UpsertResult.APPLIED
            }

            private suspend fun upsertCompletion(remoteId: String, data: Map<String, Any?>): UpsertResult {
                val reminderId = data.string("reminderId")?.let { syncDao.reminderIdOf(it) } ?: return UpsertResult.ORPHAN
                val existing = syncDao.completionByRemoteId(remoteId)
                val row = DocumentMapper.completionFromDoc(remoteId, data, existing, reminderId)
                if (existing == null) syncDao.insertCompletion(row) else syncDao.updateCompletion(row)
                return UpsertResult.APPLIED
            }
        }

        private companion object {
            /** Stays well under SQLite's 999 bound-variable limit on old Android versions. */
            const val SQL_VARIABLE_CHUNK = 500
        }
    }

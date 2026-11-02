package com.peoplehub.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.peoplehub.core.database.entity.CheckInEntity
import com.peoplehub.core.database.entity.EventEntity
import com.peoplehub.core.database.entity.PersonWithDetails
import com.peoplehub.core.database.entity.ReminderCompletionEntity
import com.peoplehub.core.database.entity.ReminderEntity
import com.peoplehub.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for the cloud sync engine (`:core:sync`): the outbox the triggers fill, the
 * "applying a remote change" switch, and reads and writes keyed by `remote_id`.
 *
 * Everything that writes rows here is meant to run inside a transaction that has set
 * [setApplyingRemote], so none of it is queued back to the cloud.
 */
@Dao
@Suppress("TooManyFunctions") // One method per query, like every DAO; grouping them would only hide SQL.
interface SyncDao {
    // --- Outbox ---------------------------------------------------------------------------------

    /** The oldest pending changes, parents first (people, reminders, events, check-ins, completions). */
    @Query(
        "SELECT * FROM sync_outbox ORDER BY CASE collection " +
            "WHEN 'people' THEN 0 WHEN 'reminders' THEN 1 WHEN 'events' THEN 2 " +
            "WHEN 'checkIns' THEN 3 ELSE 4 END, changed_at ASC LIMIT :limit",
    )
    suspend fun pendingChanges(limit: Int): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int

    @Query("SELECT changed_at FROM sync_outbox WHERE collection = :collection AND remote_id = :remoteId")
    suspend fun pendingChangedAt(collection: String, remoteId: String): Long?

    /** Removes an entry once pushed, unless it changed again meanwhile (then it is pushed next time). */
    @Query("DELETE FROM sync_outbox WHERE collection = :collection AND remote_id = :remoteId AND changed_at = :changedAt")
    suspend fun acknowledge(collection: String, remoteId: String, changedAt: Long)

    /** Drops a pending entry that lost to a newer cloud version. */
    @Query("DELETE FROM sync_outbox WHERE collection = :collection AND remote_id = :remoteId")
    suspend fun discard(collection: String, remoteId: String)

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query("UPDATE sync_state SET applying_remote = :applying WHERE id = 1")
    suspend fun setApplyingRemote(applying: Boolean)

    @Query("INSERT OR IGNORE INTO sync_state (id, applying_remote) VALUES (1, 0)")
    suspend fun ensureStateRow()

    // Queue every existing row, for the first full upload.

    @Query("INSERT OR REPLACE INTO sync_outbox (collection, remote_id, changed_at) SELECT 'people', remote_id, :now FROM person")
    suspend fun enqueueAllPeople(now: Long)

    @Query("INSERT OR REPLACE INTO sync_outbox (collection, remote_id, changed_at) SELECT 'reminders', remote_id, :now FROM reminder")
    suspend fun enqueueAllReminders(now: Long)

    @Query("INSERT OR REPLACE INTO sync_outbox (collection, remote_id, changed_at) SELECT 'events', remote_id, :now FROM event")
    suspend fun enqueueAllEvents(now: Long)

    @Query("INSERT OR REPLACE INTO sync_outbox (collection, remote_id, changed_at) SELECT 'checkIns', remote_id, :now FROM check_in")
    suspend fun enqueueAllCheckIns(now: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox (collection, remote_id, changed_at) " +
            "SELECT 'reminderCompletions', remote_id, :now FROM reminder_completion",
    )
    suspend fun enqueueAllCompletions(now: Long)

    // --- Reads by remote id ---------------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM person WHERE remote_id = :remoteId")
    suspend fun personByRemoteId(remoteId: String): PersonWithDetails?

    @Query("SELECT * FROM check_in WHERE remote_id = :remoteId")
    suspend fun checkInByRemoteId(remoteId: String): CheckInEntity?

    @Query("SELECT * FROM event WHERE remote_id = :remoteId")
    suspend fun eventByRemoteId(remoteId: String): EventEntity?

    @Query("SELECT * FROM reminder WHERE remote_id = :remoteId")
    suspend fun reminderByRemoteId(remoteId: String): ReminderEntity?

    @Query("SELECT * FROM reminder_completion WHERE remote_id = :remoteId")
    suspend fun completionByRemoteId(remoteId: String): ReminderCompletionEntity?

    @Query("SELECT id FROM person WHERE remote_id = :remoteId")
    suspend fun personIdOf(remoteId: String): Long?

    @Query("SELECT remote_id FROM person WHERE id = :id")
    suspend fun personRemoteIdOf(id: Long): String?

    @Query("SELECT id FROM reminder WHERE remote_id = :remoteId")
    suspend fun reminderIdOf(remoteId: String): Long?

    @Query("SELECT remote_id FROM reminder WHERE id = :id")
    suspend fun reminderRemoteIdOf(id: Long): String?

    /** The local outing a cloud outing id already maps to, if any of its rows is on the phone. */
    @Query("SELECT outing_id FROM check_in WHERE outing_remote_id = :outingRemoteId LIMIT 1")
    suspend fun outingIdOf(outingRemoteId: String): Long?

    @Query("SELECT IFNULL(MAX(outing_id), 0) + 1 FROM check_in")
    suspend fun nextOutingId(): Long

    // --- Writes of remote rows ------------------------------------------------------------------

    @Update
    suspend fun updateCompletion(completion: ReminderCompletionEntity)

    @Insert
    suspend fun insertCompletion(completion: ReminderCompletionEntity): Long

    @Query("DELETE FROM person WHERE remote_id = :remoteId")
    suspend fun deletePerson(remoteId: String)

    @Query("DELETE FROM check_in WHERE remote_id = :remoteId")
    suspend fun deleteCheckIn(remoteId: String)

    @Query("DELETE FROM event WHERE remote_id = :remoteId")
    suspend fun deleteEvent(remoteId: String)

    @Query("DELETE FROM reminder WHERE remote_id = :remoteId")
    suspend fun deleteReminder(remoteId: String)

    @Query("DELETE FROM reminder_completion WHERE remote_id = :remoteId")
    suspend fun deleteCompletion(remoteId: String)

    /**
     * Re-derives the denormalised last check-in of [personIds] from their check-ins (`NULL` when they
     * have none left), after cloud changes added, moved or removed some.
     */
    @Query(
        "UPDATE person SET last_check_in_epoch_millis = " +
            "(SELECT MAX(c.timestamp_epoch_millis) FROM check_in c WHERE c.person_id = person.id) " +
            "WHERE id IN (:personIds)",
    )
    suspend fun recomputeLastCheckIn(personIds: List<Long>)

    /** Wipes every synced table before a full download replaces the local data with the cloud's. */
    @Transaction
    suspend fun wipeAll() {
        deleteAllCompletionsRaw()
        deleteAllRemindersRaw()
        deleteAllEventsRaw()
        deleteAllCheckInsRaw()
        deleteAllFtsRaw()
        deleteAllPeopleRaw()
        clearOutbox()
    }

    @Query("DELETE FROM reminder_completion")
    suspend fun deleteAllCompletionsRaw()

    @Query("DELETE FROM reminder")
    suspend fun deleteAllRemindersRaw()

    @Query("DELETE FROM event")
    suspend fun deleteAllEventsRaw()

    @Query("DELETE FROM check_in")
    suspend fun deleteAllCheckInsRaw()

    @Query("DELETE FROM person_fts")
    suspend fun deleteAllFtsRaw()

    @Query("DELETE FROM person")
    suspend fun deleteAllPeopleRaw()
}

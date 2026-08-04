package com.peoplehub.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.peoplehub.core.database.entity.ReminderCompletionEntity
import com.peoplehub.core.database.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

/**
 * A reminder that has come due, joined with the owning person's name and notification opt-in so the
 * worker can filter and notify in a single query.
 */
data class DueReminderRow(
    val id: Long,
    @androidx.room.ColumnInfo(name = "person_id") val personId: Long,
    val title: String,
    val note: String?,
    @androidx.room.ColumnInfo(name = "first_name") val firstName: String,
    @androidx.room.ColumnInfo(name = "last_name") val lastName: String,
)

/**
 * How often a reminder has been ticked off as done, and when it last was — the whole completion log of
 * one reminder folded into the two values the cards display.
 */
data class ReminderCompletionRow(
    @androidx.room.ColumnInfo(name = "reminder_id") val reminderId: Long,
    @androidx.room.ColumnInfo(name = "times_done") val timesDone: Int,
    @androidx.room.ColumnInfo(name = "last_done_epoch_day") val lastDoneEpochDay: Long,
)

/** Data-access object for per-person relationship reminders. */
@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("SELECT * FROM reminder WHERE person_id = :personId ORDER BY next_fire_epoch_millis ASC")
    fun observeForPerson(personId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder ORDER BY next_fire_epoch_millis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminder")
    suspend fun getAll(): List<ReminderEntity>

    /**
     * The enabled reminders whose next fire is at or before [now], joined with the person's name for
     * the notification text. The sweep passes the end of the current day, so day-granular cadences are
     * not delivered a day late.
     *
     * Deliberately **not** gated on the person's `notifications_enabled` opt-in: that toggle governs
     * check-in reminders, defaults to off, and gating on it here meant a reminder the user created
     * explicitly never notified at all.
     */
    @Query(
        "SELECT r.id AS id, r.person_id AS person_id, r.title AS title, r.note AS note, " +
            "p.first_name AS first_name, p.last_name AS last_name " +
            "FROM reminder r JOIN person p ON p.id = r.person_id " +
            "WHERE r.enabled = 1 AND r.next_fire_epoch_millis <= :now " +
            "ORDER BY r.next_fire_epoch_millis ASC",
    )
    suspend fun getDue(now: Long): List<DueReminderRow>

    @Query("UPDATE reminder SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query(
        "UPDATE reminder SET last_fired_epoch_millis = :firedAt, next_fire_epoch_millis = :nextFire WHERE id = :id",
    )
    suspend fun markFired(id: Long, firedAt: Long, nextFire: Long)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminder")
    suspend fun deleteAll()

    /**
     * Logs that a reminder was done on a day. Ticking the same day twice is ignored rather than
     * rejected, so a card the user can tap again stays idempotent.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: ReminderCompletionEntity)

    /** The completion log of every reminder, folded into a count and the most recent day. */
    @Query(
        "SELECT reminder_id AS reminder_id, COUNT(*) AS times_done, " +
            "MAX(done_epoch_day) AS last_done_epoch_day " +
            "FROM reminder_completion GROUP BY reminder_id",
    )
    fun observeCompletions(): Flow<List<ReminderCompletionRow>>
}

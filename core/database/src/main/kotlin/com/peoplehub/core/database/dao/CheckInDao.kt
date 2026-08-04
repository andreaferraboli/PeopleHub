package com.peoplehub.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.peoplehub.core.database.entity.CheckInEntity
import kotlinx.coroutines.flow.Flow

/**
 * One attendee's row inside an outing, joined with their display data so a whole outing (card avatars
 * and names included) is read in a single query.
 */
data class OutingRow(
    val id: Long,
    @ColumnInfo(name = "outing_id") val outingId: Long,
    @ColumnInfo(name = "timestamp_epoch_millis") val timestampEpochMillis: Long,
    val note: String?,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "photo_path") val photoPath: String?,
)

/** Data-access object for check-in history. */
@Dao
interface CheckInDao {
    @Insert
    suspend fun insert(checkIn: CheckInEntity): Long

    @Update
    suspend fun update(checkIn: CheckInEntity)

    @Query("DELETE FROM check_in WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT MAX(timestamp_epoch_millis) FROM check_in WHERE person_id = :personId")
    suspend fun latestTimestamp(personId: Long): Long?

    @Query("SELECT * FROM check_in WHERE person_id = :personId ORDER BY timestamp_epoch_millis DESC")
    fun observeForPerson(personId: Long): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_in ORDER BY timestamp_epoch_millis DESC")
    suspend fun getAll(): List<CheckInEntity>

    /** The next unused outing id. Ids are handed out monotonically and never reused. */
    @Query("SELECT IFNULL(MAX(outing_id), 0) + 1 FROM check_in")
    suspend fun nextOutingId(): Long

    /** Every check-in joined with its person, most recent first, for the outings history. */
    @Query(
        "SELECT c.id AS id, c.outing_id AS outing_id, " +
            "c.timestamp_epoch_millis AS timestamp_epoch_millis, c.note AS note, " +
            "p.id AS person_id, p.first_name AS first_name, p.last_name AS last_name, " +
            "p.photo_path AS photo_path " +
            "FROM check_in c JOIN person p ON p.id = c.person_id " +
            "ORDER BY c.timestamp_epoch_millis DESC, c.outing_id DESC, p.first_name ASC",
    )
    fun observeOutingRows(): Flow<List<OutingRow>>

    /** The rows of a single outing, joined with their people. */
    @Query(
        "SELECT c.id AS id, c.outing_id AS outing_id, " +
            "c.timestamp_epoch_millis AS timestamp_epoch_millis, c.note AS note, " +
            "p.id AS person_id, p.first_name AS first_name, p.last_name AS last_name, " +
            "p.photo_path AS photo_path " +
            "FROM check_in c JOIN person p ON p.id = c.person_id " +
            "WHERE c.outing_id = :outingId ORDER BY p.first_name ASC",
    )
    suspend fun getOutingRows(outingId: Long): List<OutingRow>

    @Insert
    suspend fun insertAll(checkIns: List<CheckInEntity>)

    @Query("DELETE FROM check_in")
    suspend fun deleteAll()
}

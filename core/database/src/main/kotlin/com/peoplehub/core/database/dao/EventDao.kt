package com.peoplehub.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.peoplehub.core.database.entity.EventEntity
import kotlinx.coroutines.flow.Flow

/** Data-access object for personal events. */
@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM event ORDER BY date_time_epoch_millis ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE id = :id")
    fun observeById(id: Long): Flow<EventEntity?>

    @Query("SELECT * FROM event WHERE pinned_to_widget = 1 LIMIT 1")
    fun observePinned(): Flow<EventEntity?>

    @Query("SELECT * FROM event WHERE pinned_to_widget = 1 LIMIT 1")
    suspend fun getPinned(): EventEntity?

    @Query("SELECT DISTINCT category FROM event WHERE category IS NOT NULL ORDER BY category COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT background_image_path FROM event " +
            "WHERE background_image_path IS NOT NULL ORDER BY background_image_path ASC",
    )
    fun observeBackgroundImages(): Flow<List<String>>

    @Query("SELECT * FROM event")
    suspend fun getAll(): List<EventEntity>

    @Query("DELETE FROM event WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE event SET pinned_to_widget = 0")
    suspend fun clearAllPinned()

    @Query("UPDATE event SET pinned_to_widget = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM event")
    suspend fun deleteAll()
}

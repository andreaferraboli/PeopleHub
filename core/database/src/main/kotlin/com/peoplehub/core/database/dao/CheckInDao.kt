package com.peoplehub.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.peoplehub.core.database.entity.CheckInEntity
import kotlinx.coroutines.flow.Flow

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

    @Insert
    suspend fun insertAll(checkIns: List<CheckInEntity>)

    @Query("DELETE FROM check_in")
    suspend fun deleteAll()
}

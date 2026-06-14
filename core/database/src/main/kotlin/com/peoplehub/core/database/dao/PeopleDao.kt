package com.peoplehub.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.peoplehub.core.database.entity.InterestEntity
import com.peoplehub.core.database.entity.PersonEntity
import com.peoplehub.core.database.entity.PersonFtsEntity
import com.peoplehub.core.database.entity.PersonTagEntity
import com.peoplehub.core.database.entity.PersonWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for people and their child rows (tags, interests) plus the FTS index. The
 * repository orchestrates multi-table writes inside a single transaction; this DAO exposes the
 * primitive operations.
 */
@Dao
interface PeopleDao {
    @Transaction
    @Query("SELECT * FROM person")
    fun observeAll(): Flow<List<PersonWithDetails>>

    @Transaction
    @Query(
        "SELECT person.* FROM person " +
            "JOIN person_fts ON person_fts.rowid = person.id " +
            "WHERE person_fts MATCH :ftsQuery",
    )
    fun search(ftsQuery: String): Flow<List<PersonWithDetails>>

    @Transaction
    @Query("SELECT * FROM person WHERE id = :id")
    fun observeById(id: Long): Flow<PersonWithDetails?>

    @Transaction
    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun getById(id: Long): PersonWithDetails?

    @Transaction
    @Query("SELECT * FROM person")
    suspend fun getAll(): List<PersonWithDetails>

    @Query("SELECT DISTINCT tag FROM person_tag ORDER BY tag COLLATE NOCASE ASC")
    fun observeAllTags(): Flow<List<String>>

    @Insert
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("UPDATE person SET last_check_in_epoch_millis = :epochMillis WHERE id = :personId")
    suspend fun updateLastCheckIn(personId: Long, epochMillis: Long)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Query("DELETE FROM person")
    suspend fun deleteAllPeople()

    @Insert
    suspend fun insertTags(tags: List<PersonTagEntity>)

    @Query("DELETE FROM person_tag WHERE person_id = :personId")
    suspend fun deleteTagsFor(personId: Long)

    @Insert
    suspend fun insertInterests(interests: List<InterestEntity>)

    @Query("DELETE FROM interest WHERE person_id = :personId")
    suspend fun deleteInterestsFor(personId: Long)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertFts(fts: PersonFtsEntity)

    @Query("DELETE FROM person_fts WHERE rowid = :rowId")
    suspend fun deleteFts(rowId: Long)

    @Query("DELETE FROM person_fts")
    suspend fun deleteAllFts()
}

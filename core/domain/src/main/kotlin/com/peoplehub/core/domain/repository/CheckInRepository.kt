package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.CheckIn
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Read/write access to check-in records (the frequentation tracker history).
 */
interface CheckInRepository {
    /** Records a new check-in and returns its id. */
    suspend fun recordCheckIn(checkIn: CheckIn): Long

    /** Updates an existing check-in (its timestamp and/or note). */
    suspend fun updateCheckIn(checkIn: CheckIn)

    /** Deletes the check-ins with the given [ids] in a single statement. */
    suspend fun deleteCheckIns(ids: List<Long>)

    /** The most recent check-in instant for [personId], or `null` if they have none left. */
    suspend fun latestTimestamp(personId: Long): Instant?

    /** Observes the reverse-chronological check-in history for a person. */
    fun observeHistory(personId: Long): Flow<List<CheckIn>>

    /** One-shot read of every check-in, used by backup/export. */
    suspend fun getAllCheckIns(): List<CheckIn>

    /** Deletes every check-in — used by the "replace all" import strategy. */
    suspend fun deleteAll()
}

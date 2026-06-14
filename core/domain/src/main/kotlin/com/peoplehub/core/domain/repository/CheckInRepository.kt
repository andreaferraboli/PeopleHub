package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.CheckIn
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to check-in records (the frequentation tracker history).
 */
interface CheckInRepository {
    /** Records a new check-in and returns its id. */
    suspend fun recordCheckIn(checkIn: CheckIn): Long

    /** Observes the reverse-chronological check-in history for a person. */
    fun observeHistory(personId: Long): Flow<List<CheckIn>>

    /** One-shot read of every check-in, used by backup/export. */
    suspend fun getAllCheckIns(): List<CheckIn>

    /** Deletes every check-in — used by the "replace all" import strategy. */
    suspend fun deleteAll()
}

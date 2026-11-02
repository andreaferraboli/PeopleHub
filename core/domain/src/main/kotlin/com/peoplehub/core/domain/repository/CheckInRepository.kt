package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Outing
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Read/write access to check-in records (the frequentation tracker history) and to the [Outing]
 * grouping built on top of them.
 */
interface CheckInRepository {
    /** Records a new check-in and returns its id. */
    suspend fun recordCheckIn(checkIn: CheckIn): String

    /** Records several check-ins at once — used for multi-day and multi-person meetups. */
    suspend fun recordCheckIns(checkIns: List<CheckIn>)

    /** Updates an existing check-in (its timestamp and/or note). */
    suspend fun updateCheckIn(checkIn: CheckIn)

    /** Deletes the check-ins with the given [ids] (tombstones, in batches). */
    suspend fun deleteCheckIns(ids: List<String>)

    /** The most recent check-in instant for [personId], or `null` if they have none left. */
    suspend fun latestTimestamp(personId: String): Instant?

    /** Observes the reverse-chronological check-in history for a person. */
    fun observeHistory(personId: String): Flow<List<CheckIn>>

    /**
     * Draws a fresh [CheckIn.outingId] (a UUID) so several check-ins can be written as one shared
     * outing (or a single one detached into an outing of its own).
     */
    fun newOutingId(): String

    /** Observes every recorded outing, most recent first. */
    fun observeOutings(): Flow<List<Outing>>

    /** One-shot read of a single outing, or `null` if it no longer exists. */
    suspend fun getOuting(outingId: String): Outing?

    /** One-shot read of every check-in, used by backup/export. */
    suspend fun getAllCheckIns(): List<CheckIn>

    /** Deletes every check-in — used by the "replace all" import strategy. */
    suspend fun deleteAll()
}

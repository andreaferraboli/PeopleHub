package com.peoplehub.core.data.repository

import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.CloudWrite
import com.peoplehub.core.data.mapping.DocumentMapper
import com.peoplehub.core.data.query.Outings
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.domain.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Firestore-backed [CheckInRepository]. An outing is the set of check-ins sharing an `outingId` UUID
 * ([Outings]); deleting a check-in writes a tombstone.
 */
internal class CheckInRepositoryImpl
    @Inject
    constructor(
        private val store: CloudStore,
        private val reads: CloudReads,
        private val clock: Clock,
    ) : CheckInRepository {
        override suspend fun recordCheckIn(checkIn: CheckIn): String {
            val stored = checkIn.withIds()
            store.commit(listOf(put(stored)))
            return stored.id
        }

        override suspend fun recordCheckIns(checkIns: List<CheckIn>) = store.commit(checkIns.map { put(it.withIds()) })

        override suspend fun updateCheckIn(checkIn: CheckIn) {
            require(checkIn.id.isNotEmpty()) { "A check-in to update needs its id" }
            store.commit(listOf(put(checkIn.withIds())))
        }

        override suspend fun deleteCheckIns(ids: List<String>) =
            store.commit(ids.distinct().map { CloudWrite.Tombstone(Cloud.CHECK_INS, it) })

        override suspend fun latestTimestamp(personId: String): Instant? =
            reads.checkIns
                .first()
                .filter { it.personId == personId }
                .maxOfOrNull { it.timestamp }

        override fun observeHistory(personId: String): Flow<List<CheckIn>> =
            reads.checkIns.map { all -> all.filter { it.personId == personId }.sortedByDescending { it.timestamp } }

        override fun newOutingId(): String = newDocumentId()

        override fun observeOutings(): Flow<List<Outing>> =
            combine(reads.checkIns, reads.people) { checkIns, people ->
                Outings.group(checkIns, people.associateBy { it.id }, clock.zone)
            }

        override suspend fun getOuting(outingId: String): Outing? = observeOutings().first().firstOrNull { it.id == outingId }

        override suspend fun getAllCheckIns(): List<CheckIn> {
            val people =
                reads.people
                    .first()
                    .map { it.id }
                    .toSet()
            return reads.checkIns.first().filter { it.personId in people }
        }

        override suspend fun deleteAll() =
            store.commit(store.observe(Cloud.CHECK_INS).first().map { CloudWrite.Tombstone(Cloud.CHECK_INS, it.id) })

        private fun CheckIn.withIds(): CheckIn =
            copy(
                id = id.ifEmpty { newDocumentId() },
                outingId = outingId.ifEmpty { newDocumentId() },
            )

        private fun put(checkIn: CheckIn) = CloudWrite.Put(Cloud.CHECK_INS, checkIn.id, DocumentMapper.checkInToDoc(checkIn))
    }

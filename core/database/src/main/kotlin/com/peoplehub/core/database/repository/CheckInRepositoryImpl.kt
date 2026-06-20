package com.peoplehub.core.database.repository

import com.peoplehub.core.database.dao.CheckInDao
import com.peoplehub.core.database.mapper.toDomain
import com.peoplehub.core.database.mapper.toEntity
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** Room-backed [CheckInRepository]. */
internal class CheckInRepositoryImpl
    @Inject
    constructor(
        private val dao: CheckInDao,
    ) : CheckInRepository {
        override suspend fun recordCheckIn(checkIn: CheckIn): Long = dao.insert(checkIn.toEntity())

        override suspend fun updateCheckIn(checkIn: CheckIn) = dao.update(checkIn.toEntity())

        override suspend fun deleteCheckIns(ids: List<Long>) = dao.deleteByIds(ids)

        override suspend fun latestTimestamp(personId: Long): Instant? =
            dao.latestTimestamp(personId)?.let(Instant::ofEpochMilli)

        override fun observeHistory(personId: Long): Flow<List<CheckIn>> =
            dao.observeForPerson(personId).map { list -> list.map { it.toDomain() } }

        override suspend fun getAllCheckIns(): List<CheckIn> = dao.getAll().map { it.toDomain() }

        override suspend fun deleteAll() = dao.deleteAll()
    }

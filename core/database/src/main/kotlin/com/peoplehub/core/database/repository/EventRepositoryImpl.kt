package com.peoplehub.core.database.repository

import androidx.room.withTransaction
import com.peoplehub.core.database.PeopleHubDatabase
import com.peoplehub.core.database.dao.EventDao
import com.peoplehub.core.database.mapper.toDomain
import com.peoplehub.core.database.mapper.toEntity
import com.peoplehub.core.domain.model.EventFilter
import com.peoplehub.core.domain.model.EventTimeFilter
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Room-backed [EventRepository]. Time partitioning and the "future ascending, then past descending"
 * ordering are applied in memory against the injected [clock].
 */
internal class EventRepositoryImpl @Inject constructor(
    private val database: PeopleHubDatabase,
    private val dao: EventDao,
    private val clock: Clock,
) : EventRepository {

    override fun observeEvents(filter: EventFilter): Flow<List<PersonEvent>> =
        dao.observeAll().map { rows ->
            val now = LocalDateTime.now(clock)
            val events = rows.asSequence()
                .map { it.toDomain() }
                .filter { filter.category == null || it.category == filter.category }
                .filter { filter.personId == null || it.personId == filter.personId }
                .toList()

            val (future, past) = events.partition { !it.dateTime.isBefore(now) }
            val orderedFuture = future.sortedBy { it.dateTime }
            val orderedPast = past.sortedByDescending { it.dateTime }

            when (filter.timeFilter) {
                EventTimeFilter.ALL -> orderedFuture + orderedPast
                EventTimeFilter.UPCOMING -> orderedFuture
                EventTimeFilter.PAST -> orderedPast
            }
        }

    override fun observeEvent(id: Long): Flow<PersonEvent?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observePinnedEvent(): Flow<PersonEvent?> =
        dao.observePinned().map { it?.toDomain() }

    override fun observeCategories(): Flow<List<String>> = dao.observeCategories()

    override suspend fun getAllEvents(): List<PersonEvent> = dao.getAll().map { it.toDomain() }

    override suspend fun upsertEvent(event: PersonEvent): Long {
        val entity = event.toEntity()
        return if (event.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            event.id
        }
    }

    override suspend fun deleteEvent(id: Long) = dao.deleteById(id)

    override suspend fun setPinned(eventId: Long, pinned: Boolean) = database.withTransaction {
        if (pinned) {
            dao.clearAllPinned()
        }
        dao.setPinned(eventId, pinned)
    }

    override suspend fun deleteAll() = dao.deleteAll()
}

package com.peoplehub.core.database.repository

import androidx.room.withTransaction
import com.peoplehub.core.database.PeopleHubDatabase
import com.peoplehub.core.database.dao.PeopleDao
import com.peoplehub.core.database.entity.PersonFtsEntity
import com.peoplehub.core.database.mapper.toDomain
import com.peoplehub.core.database.mapper.toEntity
import com.peoplehub.core.database.mapper.toInterestEntities
import com.peoplehub.core.database.mapper.toTagEntities
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.PeopleSort
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.PeopleRepository
import com.peoplehub.core.domain.util.DateCalculations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Room-backed [PeopleRepository]. Full-text search is delegated to the FTS table; tag filtering and
 * birthday/check-in ordering (which need the current date) are applied in memory, which is well
 * within budget for the personal-scale datasets this app handles.
 */
internal class PeopleRepositoryImpl @Inject constructor(
    private val database: PeopleHubDatabase,
    private val dao: PeopleDao,
    private val clock: Clock,
) : PeopleRepository {

    override fun observePeople(filter: PeopleFilter): Flow<List<Person>> {
        val tokens = ftsTokens(filter.query)
        val source = if (tokens.isEmpty()) {
            dao.observeAll()
        } else {
            dao.search(tokens.joinToString(separator = " ") { "$it*" })
        }
        return source.map { rows ->
            val today = LocalDate.now(clock)
            rows.asSequence()
                .map { it.toDomain() }
                .filter { person -> filter.tags.isEmpty() || person.tags.any { it in filter.tags } }
                .sortedWith(comparatorFor(filter.sort, today))
                .toList()
        }
    }

    override fun observePerson(id: Long): Flow<Person?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observeAllTags(): Flow<List<String>> = dao.observeAllTags()

    override suspend fun getPerson(id: Long): Person? = dao.getById(id)?.toDomain()

    override suspend fun getAllPeople(): List<Person> = dao.getAll().map { it.toDomain() }

    override suspend fun upsertPerson(person: Person): Long = database.withTransaction {
        val entity = person.toEntity()
        val id = if (person.id == 0L) {
            dao.insertPerson(entity)
        } else {
            dao.updatePerson(entity)
            person.id
        }
        dao.deleteTagsFor(id)
        dao.insertTags(person.toTagEntities(id))
        dao.deleteInterestsFor(id)
        dao.insertInterests(person.toInterestEntities(id))
        dao.upsertFts(PersonFtsEntity(rowId = id, firstName = person.firstName, lastName = person.lastName, notes = person.notes))
        id
    }

    override suspend fun updateLastCheckIn(personId: Long, lastCheckInEpochMillis: Long) =
        dao.updateLastCheckIn(personId, lastCheckInEpochMillis)

    override suspend fun deletePerson(id: Long) = database.withTransaction {
        dao.deleteFts(id)
        dao.deletePerson(id)
    }

    override suspend fun deleteAll() = database.withTransaction {
        dao.deleteAllFts()
        dao.deleteAllPeople()
    }

    private fun comparatorFor(sort: PeopleSort, today: LocalDate): Comparator<Person> = when (sort) {
        PeopleSort.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.fullName }
        PeopleSort.LAST_CHECK_IN -> compareBy(nullsFirst<Instant>()) { it.lastCheckInAt }
        PeopleSort.UPCOMING_BIRTHDAY -> compareBy { person ->
            person.birthday?.let { DateCalculations.daysUntilBirthday(it, today) } ?: Int.MAX_VALUE
        }
    }

    private fun ftsTokens(query: String): List<String> =
        query.lowercase()
            .split(NON_ALPHANUMERIC)
            .filter { it.isNotBlank() }

    private companion object {
        val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    }
}

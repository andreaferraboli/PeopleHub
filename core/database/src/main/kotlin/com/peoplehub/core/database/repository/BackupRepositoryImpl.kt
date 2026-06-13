package com.peoplehub.core.database.repository

import androidx.room.withTransaction
import com.peoplehub.core.database.PeopleHubDatabase
import com.peoplehub.core.database.dao.CheckInDao
import com.peoplehub.core.database.dao.EventDao
import com.peoplehub.core.database.dao.PeopleDao
import com.peoplehub.core.database.entity.PersonFtsEntity
import com.peoplehub.core.database.mapper.toDomain
import com.peoplehub.core.database.mapper.toEntity
import com.peoplehub.core.database.mapper.toInterestEntities
import com.peoplehub.core.database.mapper.toTagEntities
import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.MergeReport
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.BackupRepository
import javax.inject.Inject

/**
 * Room-backed [BackupRepository]. Imports remap incoming primary keys to freshly generated ids so
 * they never collide with existing rows; child records (check-ins, events) are re-linked through an
 * old-id → new-id map.
 */
internal class BackupRepositoryImpl @Inject constructor(
    private val database: PeopleHubDatabase,
    private val peopleDao: PeopleDao,
    private val checkInDao: CheckInDao,
    private val eventDao: EventDao,
) : BackupRepository {

    override suspend fun exportAll(): BackupData = BackupData(
        schemaVersion = BackupData.CURRENT_SCHEMA_VERSION,
        people = peopleDao.getAll().map { it.toDomain() },
        checkIns = checkInDao.getAll().map { it.toDomain() },
        events = eventDao.getAll().map { it.toDomain() },
    )

    override suspend fun importReplace(data: BackupData) = database.withTransaction {
        eventDao.deleteAll()
        checkInDao.deleteAll()
        peopleDao.deleteAllFts()
        peopleDao.deleteAllPeople()
        val idMap = insertPeople(data.people)
        insertCheckIns(data, idMap)
        insertEvents(data, idMap)
        Unit
    }

    override suspend fun importMerge(data: BackupData): MergeReport = database.withTransaction {
        val existingKeys = peopleDao.getAll()
            .map { dedupKey(it.toDomain()) }
            .toMutableSet()

        val idMap = HashMap<Long, Long>()
        var peopleAdded = 0
        var peopleSkipped = 0
        for (person in data.people) {
            val key = dedupKey(person)
            if (key in existingKeys) {
                peopleSkipped++
                continue
            }
            existingKeys += key
            idMap[person.id] = insertPersonGraph(person)
            peopleAdded++
        }
        val checkInsAdded = insertCheckIns(data, idMap)
        val eventsAdded = insertEvents(data, idMap)
        MergeReport(peopleAdded, peopleSkipped, eventsAdded, checkInsAdded)
    }

    private suspend fun insertPeople(people: List<Person>): Map<Long, Long> {
        val idMap = HashMap<Long, Long>()
        for (person in people) {
            idMap[person.id] = insertPersonGraph(person)
        }
        return idMap
    }

    private suspend fun insertCheckIns(data: BackupData, idMap: Map<Long, Long>): Int {
        var added = 0
        for (checkIn in data.checkIns) {
            val personId = idMap[checkIn.personId] ?: continue
            checkInDao.insert(checkIn.copy(id = 0L, personId = personId).toEntity())
            added++
        }
        return added
    }

    private suspend fun insertEvents(data: BackupData, idMap: Map<Long, Long>): Int {
        var added = 0
        for (event in data.events) {
            val mappedPerson = event.personId?.let { idMap[it] }
            // Skip events whose linked person was not imported (a duplicate that was merged away).
            if (event.personId != null && mappedPerson == null) continue
            eventDao.insert(event.copy(id = 0L, personId = mappedPerson, pinnedToWidget = false).toEntity())
            added++
        }
        return added
    }

    private suspend fun insertPersonGraph(person: Person): Long {
        val id = peopleDao.insertPerson(person.copy(id = 0L).toEntity())
        peopleDao.insertTags(person.toTagEntities(id))
        peopleDao.insertInterests(person.toInterestEntities(id))
        peopleDao.upsertFts(PersonFtsEntity(rowId = id, firstName = person.firstName, lastName = person.lastName, notes = person.notes))
        return id
    }

    private fun dedupKey(person: Person): String =
        "${person.firstName.trim().lowercase()}|${person.lastName.trim().lowercase()}|${person.birthday ?: ""}"
}

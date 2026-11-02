package com.peoplehub.core.data.repository

import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.CloudWrite
import com.peoplehub.core.data.local.LocalMedia
import com.peoplehub.core.data.mapping.DocumentMapper
import com.peoplehub.core.data.query.PeopleQuery
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.PeopleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Firestore-backed [PeopleRepository]. Search, tag filter and sort run in memory ([PeopleQuery]);
 * deletions cascade to check-ins, reminders and their history like the MCP server does
 * ([personCascade]). Bulk changes touch only people that still exist, so they never resurrect a person
 * deleted elsewhere.
 */
internal class PeopleRepositoryImpl
    @Inject
    constructor(
        private val store: CloudStore,
        private val reads: CloudReads,
        private val media: LocalMedia,
        private val clock: Clock,
    ) : PeopleRepository {
        override fun observePeople(filter: PeopleFilter): Flow<List<Person>> =
            reads.people.map { people -> PeopleQuery.apply(people, filter, LocalDate.now(clock)) }

        override fun observePerson(id: String): Flow<Person?> =
            reads.people.map { people -> people.firstOrNull { it.id == id } }.distinctUntilChanged()

        override fun observeAllTags(): Flow<List<String>> =
            reads.people
                .map { people -> people.flatMap { it.tags }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER) }
                .distinctUntilChanged()

        override suspend fun getPerson(id: String): Person? = reads.people.first().firstOrNull { it.id == id }

        override suspend fun getAllPeople(): List<Person> = reads.people.first()

        override suspend fun upsertPerson(person: Person): String {
            val id = person.id.ifEmpty { newDocumentId() }
            val createdAt = if (person.createdAt == Instant.EPOCH) clock.instant() else person.createdAt
            // The photo map first, so the document never shows up without its photo.
            media.setPersonPhoto(id, person.photoPath)
            store.commit(listOf(CloudWrite.Put(Cloud.PEOPLE, id, DocumentMapper.personToDoc(person.copy(createdAt = createdAt)))))
            return id
        }

        override suspend fun updateLastCheckIn(personId: String, lastCheckInEpochMillis: Long?) {
            updateLive(listOf(personId), mapOf("lastCheckInAt" to lastCheckInEpochMillis))
        }

        override suspend fun bulkSetNotificationsEnabled(personIds: List<String>, enabled: Boolean) =
            updateLive(personIds, mapOf("notificationsEnabled" to enabled))

        override suspend fun bulkSetBirthdayOnly(personIds: List<String>, birthdayOnly: Boolean) =
            updateLive(personIds, mapOf("birthdayOnly" to birthdayOnly))

        override suspend fun bulkSetCheckInThreshold(personIds: List<String>, threshold: CheckInThreshold?) =
            updateLive(
                personIds,
                mapOf(
                    "warningDays" to threshold?.warningDays,
                    "criticalDays" to threshold?.criticalDays,
                    "checkInDisabled" to false,
                ),
            )

        override suspend fun bulkSetCheckInDisabled(personIds: List<String>, disabled: Boolean) =
            updateLive(personIds, mapOf("checkInDisabled" to disabled))

        override suspend fun deletePerson(id: String) = deletePeople(setOf(id))

        override suspend fun deleteAll() =
            deletePeople(
                store
                    .observe(Cloud.PEOPLE)
                    .first()
                    .map { it.id }
                    .toSet(),
            )

        private suspend fun deletePeople(ids: Set<String>) {
            if (ids.isEmpty()) return
            store.commit(
                personCascade(
                    personIds = ids,
                    checkIns = store.observe(Cloud.CHECK_INS).first(),
                    reminders = store.observe(Cloud.REMINDERS).first(),
                    completions = store.observe(Cloud.REMINDER_COMPLETIONS).first(),
                    events = store.observe(Cloud.EVENTS).first(),
                ),
            )
            media.forget(ids, emptyList())
        }

        /** Merges [fields] into every person of [personIds] that still exists. */
        private suspend fun updateLive(personIds: List<String>, fields: Map<String, Any?>) {
            if (personIds.isEmpty()) return
            val live =
                store
                    .observe(Cloud.PEOPLE)
                    .first()
                    .map { it.id }
                    .toSet()
            store.commit(personIds.distinct().filter { it in live }.map { CloudWrite.Put(Cloud.PEOPLE, it, fields) })
        }
    }

package com.peoplehub.core.data.repository

import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.CloudWrite
import com.peoplehub.core.data.local.EventMedia
import com.peoplehub.core.data.local.LocalMedia
import com.peoplehub.core.data.mapping.DocumentMapper
import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.MergeReport
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.BackupRepository
import com.peoplehub.core.domain.repository.EventRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Firestore-backed [BackupRepository]. Every imported document gets a fresh id, so an import never
 * collides with (or overwrites) existing data; check-ins and events are re-linked through an
 * old-id → new-id map. A replace tombstones the current data first — people, check-ins, events,
 * reminders and their history, as deleting everyone used to — then writes the file's content.
 * Writes go out in batches of at most 400.
 */
internal class BackupRepositoryImpl
    @Inject
    constructor(
        private val store: CloudStore,
        private val reads: CloudReads,
        private val events: EventRepository,
        private val media: LocalMedia,
        private val clock: Clock,
    ) : BackupRepository {
        override suspend fun exportAll(): BackupData {
            val people = reads.people.first()
            val ids = people.map { it.id }.toSet()
            return BackupData(
                schemaVersion = BackupData.CURRENT_SCHEMA_VERSION,
                people = people,
                checkIns = reads.checkIns.first().filter { it.personId in ids },
                events = events.getAllEvents(),
            )
        }

        override suspend fun importReplace(data: BackupData) {
            val wipe =
                listOf(Cloud.REMINDER_COMPLETIONS, Cloud.REMINDERS, Cloud.CHECK_INS, Cloud.EVENTS, Cloud.PEOPLE)
                    .flatMap { collection -> store.observe(collection).first().map { CloudWrite.Tombstone(collection, it.id) } }
            val oldPeople = wipe.filter { it.collection == Cloud.PEOPLE }.map { it.id }
            val oldEvents = wipe.filter { it.collection == Cloud.EVENTS }.map { it.id }
            val plan = ImportPlan()
            data.people.forEach { plan.addPerson(it) }
            plan.addCheckIns(data)
            plan.addEvents(data)
            plan.saveMedia()
            store.commit(wipe + plan.writes)
            media.forget(oldPeople, oldEvents)
        }

        override suspend fun importMerge(data: BackupData): MergeReport {
            val existingKeys =
                reads.people
                    .first()
                    .map(::dedupKey)
                    .toMutableSet()
            val plan = ImportPlan()
            var peopleAdded = 0
            var peopleSkipped = 0
            for (person in data.people) {
                if (!existingKeys.add(dedupKey(person))) {
                    peopleSkipped++
                    continue
                }
                plan.addPerson(person)
                peopleAdded++
            }
            val checkInsAdded = plan.addCheckIns(data)
            val eventsAdded = plan.addEvents(data)
            plan.saveMedia()
            store.commit(plan.writes)
            return MergeReport(peopleAdded, peopleSkipped, eventsAdded, checkInsAdded)
        }

        private fun dedupKey(person: Person): String =
            "${person.firstName.trim().lowercase()}|${person.lastName.trim().lowercase()}|${person.birthday ?: ""}"

        /** The documents an import writes, with the id map that re-links children to their new parents. */
        private inner class ImportPlan {
            val writes = mutableListOf<CloudWrite>()
            private val idMap = HashMap<String, String>()
            private val photos = HashMap<String, String>()
            private val eventMedia = HashMap<String, EventMedia>()

            fun addPerson(person: Person) {
                val id = newDocumentId()
                idMap[person.id] = id
                val createdAt = if (person.createdAt == Instant.EPOCH) clock.instant() else person.createdAt
                writes += CloudWrite.Put(Cloud.PEOPLE, id, DocumentMapper.personToDoc(person.copy(id = id, createdAt = createdAt)))
                person.photoPath?.takeIf { it.isNotBlank() }?.let { photos[id] = it }
            }

            /**
             * Every incoming outing gets a fresh id, so an import cannot fuse with an unrelated outing.
             * Rows from files written before outings existed (no id, or `0`) are regrouped by day and
             * description, the rule the app always used.
             */
            fun addCheckIns(data: BackupData): Int {
                val outingIds = HashMap<String, String>()
                var added = 0
                for (checkIn in data.checkIns) {
                    val personId = idMap[checkIn.personId] ?: continue
                    val groupKey =
                        if (checkIn.outingId.isNotEmpty() && checkIn.outingId != LEGACY_NO_OUTING) {
                            "id:${checkIn.outingId}"
                        } else {
                            "legacy:${checkIn.timestamp.atZone(clock.zone).toLocalDate()}|${checkIn.note.orEmpty()}"
                        }
                    val stored =
                        checkIn.copy(
                            id = newDocumentId(),
                            personId = personId,
                            outingId = outingIds.getOrPut(groupKey) { newDocumentId() },
                        )
                    writes += CloudWrite.Put(Cloud.CHECK_INS, stored.id, DocumentMapper.checkInToDoc(stored))
                    added++
                }
                return added
            }

            fun addEvents(data: BackupData): Int {
                var added = 0
                for (event in data.events) {
                    val mappedPerson = event.personId?.let { idMap[it] }
                    // Skip events whose linked person was not imported (a duplicate that was merged away).
                    if (event.personId != null && mappedPerson == null) continue
                    val stored = event.copy(id = newDocumentId(), personId = mappedPerson, pinnedToWidget = false)
                    writes += CloudWrite.Put(Cloud.EVENTS, stored.id, DocumentMapper.eventToDoc(stored, clock.zone))
                    if (!stored.backgroundImagePath.isNullOrBlank()) {
                        eventMedia[stored.id] = EventMedia(stored.backgroundImagePath, stored.backgroundSourcePath, stored.backgroundCrop)
                    }
                    added++
                }
                return added
            }

            suspend fun saveMedia() = media.putAll(photos, eventMedia)
        }

        private companion object {
            /** The outing id older files carry for "none". */
            const val LEGACY_NO_OUTING = "0"
        }
    }

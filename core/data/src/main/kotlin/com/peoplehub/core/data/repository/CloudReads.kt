package com.peoplehub.core.data.repository

import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudDoc
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.CloudWrite
import com.peoplehub.core.data.local.LocalMedia
import com.peoplehub.core.data.mapping.DocumentMapper
import com.peoplehub.core.data.mapping.string
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * The reads several repositories share: check-ins, and people with their local photo and a last-seen
 * date computed from their check-ins (the `lastCheckInAt` stored on the document is only the fallback
 * for a person without any).
 */
internal class CloudReads
    @Inject
    constructor(
        private val store: CloudStore,
        media: LocalMedia,
    ) {
        val checkIns: Flow<List<CheckIn>> =
            store.observe(Cloud.CHECK_INS).map { docs -> docs.mapNotNull(DocumentMapper::checkInFromDoc) }

        val people: Flow<List<Person>> =
            combine(store.observe(Cloud.PEOPLE), checkIns, media.personPhotos) { docs, checkIns, photos ->
                val latest = checkIns.groupBy { it.personId }.mapValues { (_, rows) -> rows.maxOf { it.timestamp } }
                docs.map { DocumentMapper.personFromDoc(it, photos[it.id], latest[it.id]) }
            }
    }

/** A fresh document id: a lowercase UUID, the shape every client uses. */
internal fun newDocumentId(): String = UUID.randomUUID().toString()

/**
 * What deleting [personIds] writes, the same cascade the MCP server applies: tombstones for the people,
 * their check-ins, their reminders and those reminders' completions, and the events linked to them
 * unlinked (kept, `personId: null`). Children come first, so a failure part way never leaves live
 * documents pointing at a deleted person.
 */
internal fun personCascade(
    personIds: Set<String>,
    checkIns: List<CloudDoc>,
    reminders: List<CloudDoc>,
    completions: List<CloudDoc>,
    events: List<CloudDoc>,
): List<CloudWrite> {
    if (personIds.isEmpty()) return emptyList()
    val theirReminders = reminders.filter { it.data.string("personId") in personIds }
    val reminderIds = theirReminders.map { it.id }.toSet()
    return buildList {
        completions
            .filter { it.data.string("reminderId") in reminderIds }
            .forEach { add(CloudWrite.Tombstone(Cloud.REMINDER_COMPLETIONS, it.id)) }
        theirReminders.forEach { add(CloudWrite.Tombstone(Cloud.REMINDERS, it.id)) }
        checkIns
            .filter { it.data.string("personId") in personIds }
            .forEach { add(CloudWrite.Tombstone(Cloud.CHECK_INS, it.id)) }
        events
            .filter { it.data.string("personId") in personIds }
            .forEach { add(CloudWrite.Put(Cloud.EVENTS, it.id, mapOf("personId" to null))) }
        personIds.forEach { add(CloudWrite.Tombstone(Cloud.PEOPLE, it)) }
    }
}

package com.peoplehub.core.sync.mapping

import com.peoplehub.core.database.entity.CheckInEntity
import com.peoplehub.core.database.entity.EventEntity
import com.peoplehub.core.database.entity.InterestEntity
import com.peoplehub.core.database.entity.PersonEntity
import com.peoplehub.core.database.entity.PersonTagEntity
import com.peoplehub.core.database.entity.PersonWithDetails
import com.peoplehub.core.database.entity.ReminderCompletionEntity
import com.peoplehub.core.database.entity.ReminderEntity

/** A person document split back into its Room rows (child rows get their person id on write). */
internal data class PersonRows(
    val person: PersonEntity,
    val tags: List<String>,
    val interests: List<Pair<String, String>>,
) {
    fun tagEntities(personId: Long): List<PersonTagEntity> = tags.map { PersonTagEntity(personId = personId, tag = it) }

    fun interestEntities(personId: Long): List<InterestEntity> =
        interests.map { (key, value) -> InterestEntity(personId = personId, key = key, value = value) }
}

/**
 * Hand-written mapping between Room rows and Firestore field maps, field for field as in
 * `docs/firestore-schema.md`. Plain maps instead of `toObject()` keep R8 out of the picture and make
 * the contract visible in one file.
 *
 * Reading a document (`*FromDoc`) takes the `existing` local row when there is one: fields that live
 * only on the phone (photo, event background, widget pin, local ids) are carried over from it, and a
 * field missing from the document falls back to it (then to a neutral default), because the other
 * writer may send partial documents. References between documents travel as remote ids; the caller
 * resolves them to local ids.
 */
@Suppress("TooManyFunctions") // One pair of functions per collection.
internal object DocumentMapper {
    // --- people -----------------------------------------------------------------------------------

    fun personToDoc(row: PersonWithDetails): Map<String, Any?> {
        val p = row.person
        return mapOf(
            "firstName" to p.firstName,
            "lastName" to p.lastName,
            "birthday" to p.birthday,
            "notes" to p.notes,
            "tags" to row.tags.map { it.tag }.distinct(),
            "interests" to row.interests.map { mapOf("key" to it.key, "value" to it.value) },
            "warningDays" to p.warningDays,
            "criticalDays" to p.criticalDays,
            "notificationsEnabled" to p.notificationsEnabled,
            "birthdayOnly" to p.birthdayOnly,
            "checkInDisabled" to p.checkInDisabled,
            "isFamily" to p.isFamily,
            "createdAt" to p.createdAtEpochMillis,
            "lastCheckInAt" to p.lastCheckInEpochMillis,
            "hasPhoto" to (p.photoPath != null),
        )
    }

    fun personFromDoc(
        remoteId: String,
        doc: Map<String, Any?>,
        existing: PersonEntity?,
    ): PersonRows {
        val person =
            PersonEntity(
                id = existing?.id ?: 0L,
                firstName = doc.string("firstName") ?: existing?.firstName.orEmpty(),
                lastName = doc.string("lastName") ?: existing?.lastName.orEmpty(),
                photoPath = existing?.photoPath,
                birthday = doc.nullableOr("birthday", existing?.birthday) { string(it) },
                notes = doc.string("notes") ?: existing?.notes.orEmpty(),
                lastCheckInEpochMillis = existing?.lastCheckInEpochMillis ?: doc.long("lastCheckInAt"),
                warningDays = doc.nullableOr("warningDays", existing?.warningDays) { int(it) },
                criticalDays = doc.nullableOr("criticalDays", existing?.criticalDays) { int(it) },
                createdAtEpochMillis = doc.long("createdAt") ?: existing?.createdAtEpochMillis ?: doc.long("updatedAt") ?: 0L,
                notificationsEnabled = doc.bool("notificationsEnabled") ?: existing?.notificationsEnabled ?: false,
                birthdayOnly = doc.bool("birthdayOnly") ?: existing?.birthdayOnly ?: false,
                checkInDisabled = doc.bool("checkInDisabled") ?: existing?.checkInDisabled ?: false,
                isFamily = doc.bool("isFamily") ?: existing?.isFamily ?: false,
                remoteId = remoteId,
            )
        val tags =
            doc
                .list("tags")
                .filterIsInstance<String>()
                .filter { it.isNotBlank() }
                .distinct()
        return PersonRows(person, tags, interestsOf(doc))
    }

    private fun interestsOf(doc: Map<String, Any?>): List<Pair<String, String>> =
        doc.list("interests").mapNotNull { item ->
            val entry = item as? Map<*, *> ?: return@mapNotNull null
            val key = entry["key"] as? String ?: return@mapNotNull null
            key to (entry["value"] as? String).orEmpty()
        }

    // --- check-ins --------------------------------------------------------------------------------

    fun checkInToDoc(row: CheckInEntity, personRemoteId: String): Map<String, Any?> =
        mapOf(
            "personId" to personRemoteId,
            "at" to row.timestampEpochMillis,
            "note" to row.note,
            "outingId" to (row.outingRemoteId ?: row.remoteId),
        )

    fun checkInFromDoc(
        remoteId: String,
        doc: Map<String, Any?>,
        existing: CheckInEntity?,
        personId: Long,
        outingId: Long,
        outingRemoteId: String,
    ): CheckInEntity =
        CheckInEntity(
            id = existing?.id ?: 0L,
            personId = personId,
            timestampEpochMillis = doc.long("at") ?: existing?.timestampEpochMillis ?: doc.long("updatedAt") ?: 0L,
            note = doc.nullableOr("note", existing?.note) { string(it) },
            outingId = outingId,
            remoteId = remoteId,
            outingRemoteId = outingRemoteId,
        )

    // --- events -----------------------------------------------------------------------------------

    fun eventToDoc(row: EventEntity, personRemoteId: String?): Map<String, Any?> =
        mapOf(
            "title" to row.title,
            "at" to row.dateTimeEpochMillis,
            "description" to row.description,
            "category" to row.category,
            "personId" to personRemoteId,
        )

    fun eventFromDoc(
        remoteId: String,
        doc: Map<String, Any?>,
        existing: EventEntity?,
        personId: Long?,
    ): EventEntity =
        EventEntity(
            id = existing?.id ?: 0L,
            title = doc.string("title") ?: existing?.title.orEmpty(),
            dateTimeEpochMillis = doc.long("at") ?: existing?.dateTimeEpochMillis ?: 0L,
            description = doc.nullableOr("description", existing?.description) { string(it) },
            category = doc.nullableOr("category", existing?.category) { string(it) },
            backgroundImagePath = existing?.backgroundImagePath,
            backgroundSourcePath = existing?.backgroundSourcePath,
            backgroundZoom = existing?.backgroundZoom ?: 1f,
            backgroundPanX = existing?.backgroundPanX ?: 0f,
            backgroundPanY = existing?.backgroundPanY ?: 0f,
            personId = personId,
            pinnedToWidget = existing?.pinnedToWidget ?: false,
            remoteId = remoteId,
        )

    // --- reminders --------------------------------------------------------------------------------

    fun reminderToDoc(row: ReminderEntity, personRemoteId: String): Map<String, Any?> =
        mapOf(
            "personId" to personRemoteId,
            "title" to row.title,
            "note" to row.note,
            "category" to row.category,
            "targetIntervalDays" to row.targetIntervalDays,
            "jitterPercent" to row.jitterPercent,
            "enabled" to row.enabled,
            "lastFiredAt" to row.lastFiredEpochMillis,
            "nextFireAt" to row.nextFireEpochMillis,
            "createdAt" to row.createdEpochMillis,
            "presetKey" to row.presetKey,
        )

    fun reminderFromDoc(
        remoteId: String,
        doc: Map<String, Any?>,
        existing: ReminderEntity?,
        personId: Long,
    ): ReminderEntity {
        val interval = doc.int("targetIntervalDays") ?: existing?.targetIntervalDays ?: DEFAULT_INTERVAL_DAYS
        val createdAt = doc.long("createdAt") ?: existing?.createdEpochMillis ?: doc.long("updatedAt") ?: 0L
        return ReminderEntity(
            id = existing?.id ?: 0L,
            personId = personId,
            title = doc.string("title") ?: existing?.title.orEmpty(),
            note = doc.nullableOr("note", existing?.note) { string(it) },
            category = doc.string("category") ?: existing?.category ?: DEFAULT_CATEGORY,
            targetIntervalDays = interval,
            jitterPercent = doc.int("jitterPercent") ?: existing?.jitterPercent ?: 0,
            enabled = doc.bool("enabled") ?: existing?.enabled ?: true,
            lastFiredEpochMillis = doc.nullableOr("lastFiredAt", existing?.lastFiredEpochMillis) { long(it) },
            nextFireEpochMillis =
                doc.long("nextFireAt") ?: existing?.nextFireEpochMillis ?: (createdAt + interval * MILLIS_PER_DAY),
            createdEpochMillis = createdAt,
            presetKey = doc.nullableOr("presetKey", existing?.presetKey) { string(it) },
            remoteId = remoteId,
        )
    }

    // --- reminder completions ---------------------------------------------------------------------

    fun completionToDoc(row: ReminderCompletionEntity, reminderRemoteId: String): Map<String, Any?> =
        mapOf(
            "reminderId" to reminderRemoteId,
            "day" to row.doneEpochDay,
            "at" to row.doneEpochMillis,
        )

    fun completionFromDoc(
        remoteId: String,
        doc: Map<String, Any?>,
        existing: ReminderCompletionEntity?,
        reminderId: Long,
    ): ReminderCompletionEntity {
        val at = doc.nullableOr("at", existing?.doneEpochMillis) { long(it) }
        return ReminderCompletionEntity(
            id = existing?.id ?: 0L,
            reminderId = reminderId,
            doneEpochDay = doc.long("day") ?: existing?.doneEpochDay ?: (at ?: 0L) / MILLIS_PER_DAY,
            doneEpochMillis = at,
            remoteId = remoteId,
        )
    }

    private const val DEFAULT_INTERVAL_DAYS = 30
    private const val DEFAULT_CATEGORY = "CUSTOM"
    private const val MILLIS_PER_DAY = 86_400_000L
}

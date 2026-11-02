package com.peoplehub.core.data.mapping

import com.peoplehub.core.data.cloud.CloudDoc
import com.peoplehub.core.data.local.EventMedia
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.CropTransform
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.model.ReminderDoneEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One entry of a reminder's completion log, with the reminder it belongs to. */
internal data class CompletionDoc(
    val reminderId: String,
    val entry: ReminderDoneEntry,
)

/**
 * Hand-written mapping between Firestore field maps and the domain, field for field as in
 * `docs/firestore-schema.md`. Plain maps instead of `toObject()` keep R8 out of the picture and make
 * the contract visible in one file.
 *
 * Reads are lenient: the MCP server may write partial documents, so a missing field falls back to a
 * neutral default, and a document missing the reference it cannot live without (a check-in's person)
 * reads as `null` and is skipped. What lives only on the phone (photos, event backgrounds, the widget
 * pin) is passed in by the caller.
 */
@Suppress("TooManyFunctions") // One pair of functions per collection.
internal object DocumentMapper {
    // --- people -----------------------------------------------------------------------------------

    fun personFromDoc(
        doc: CloudDoc,
        photoPath: String?,
        lastCheckInAt: Instant?,
    ): Person {
        val d = doc.data
        val warning = d.int("warningDays")
        val critical = d.int("criticalDays")
        return Person(
            id = doc.id,
            firstName = d.string("firstName").orEmpty(),
            lastName = d.string("lastName").orEmpty(),
            photoPath = photoPath,
            birthday = d.string("birthday")?.let(::parseDate),
            tags =
                d
                    .list("tags")
                    .filterIsInstance<String>()
                    .filter { it.isNotBlank() }
                    .distinct(),
            interests =
                d.list("interests").mapNotNull { item ->
                    val entry = item as? Map<*, *> ?: return@mapNotNull null
                    val key = entry["key"] as? String ?: return@mapNotNull null
                    Interest(key = key, value = (entry["value"] as? String).orEmpty())
                },
            notes = d.string("notes").orEmpty(),
            lastCheckInAt = lastCheckInAt ?: d.long("lastCheckInAt")?.let(Instant::ofEpochMilli),
            checkInThreshold = if (warning != null && critical != null) CheckInThreshold(warning, critical) else null,
            createdAt = Instant.ofEpochMilli(d.long("createdAt") ?: d.long("updatedAt") ?: 0L),
            notificationsEnabled = d.bool("notificationsEnabled") ?: false,
            birthdayOnly = d.bool("birthdayOnly") ?: false,
            checkInDisabled = d.bool("checkInDisabled") ?: false,
            isFamily = d.bool("isFamily") ?: false,
        )
    }

    fun personToDoc(person: Person): Map<String, Any?> =
        mapOf(
            "firstName" to person.firstName,
            "lastName" to person.lastName,
            "birthday" to person.birthday?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            "notes" to person.notes,
            "tags" to person.tags.distinct(),
            "interests" to person.interests.map { mapOf("key" to it.key, "value" to it.value) },
            "warningDays" to person.checkInThreshold?.warningDays,
            "criticalDays" to person.checkInThreshold?.criticalDays,
            "notificationsEnabled" to person.notificationsEnabled,
            "birthdayOnly" to person.birthdayOnly,
            "checkInDisabled" to person.checkInDisabled,
            "isFamily" to person.isFamily,
            "createdAt" to person.createdAt.toEpochMilli(),
            "lastCheckInAt" to person.lastCheckInAt?.toEpochMilli(),
            "hasPhoto" to !person.photoPath.isNullOrBlank(),
        )

    // --- check-ins --------------------------------------------------------------------------------

    /** `null` when the document has no person: such a check-in belongs to nobody's history. */
    fun checkInFromDoc(doc: CloudDoc): CheckIn? {
        val d = doc.data
        val personId = d.string("personId")?.takeIf { it.isNotEmpty() } ?: return null
        return CheckIn(
            id = doc.id,
            personId = personId,
            timestamp = Instant.ofEpochMilli(d.long("at") ?: d.long("updatedAt") ?: 0L),
            note = d.string("note"),
            // A check-in without an outing is an outing of its own.
            outingId = d.string("outingId")?.takeIf { it.isNotEmpty() } ?: doc.id,
        )
    }

    fun checkInToDoc(checkIn: CheckIn): Map<String, Any?> =
        mapOf(
            "personId" to checkIn.personId,
            "at" to checkIn.timestamp.toEpochMilli(),
            "note" to checkIn.note,
            "outingId" to checkIn.outingId,
        )

    // --- events -----------------------------------------------------------------------------------

    fun eventFromDoc(
        doc: CloudDoc,
        media: EventMedia?,
        pinned: Boolean,
        zone: ZoneId,
    ): PersonEvent {
        val d = doc.data
        return PersonEvent(
            id = doc.id,
            title = d.string("title").orEmpty(),
            dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(d.long("at") ?: 0L), zone),
            description = d.string("description"),
            category = d.string("category"),
            backgroundImagePath = media?.imagePath,
            backgroundSourcePath = media?.sourcePath,
            backgroundCrop = media?.crop ?: CropTransform.Default,
            personId = d.string("personId")?.takeIf { it.isNotEmpty() },
            pinnedToWidget = pinned,
        )
    }

    fun eventToDoc(event: PersonEvent, zone: ZoneId): Map<String, Any?> =
        mapOf(
            "title" to event.title,
            "at" to
                event.dateTime
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
            "description" to event.description,
            "category" to event.category,
            "personId" to event.personId,
        )

    // --- reminders --------------------------------------------------------------------------------

    /** `null` when the document has no person: reminders are always per person. */
    fun reminderFromDoc(doc: CloudDoc): Reminder? {
        val d = doc.data
        val personId = d.string("personId")?.takeIf { it.isNotEmpty() } ?: return null
        val interval = d.int("targetIntervalDays") ?: DEFAULT_INTERVAL_DAYS
        val createdAt = d.long("createdAt") ?: d.long("updatedAt") ?: 0L
        return Reminder(
            id = doc.id,
            personId = personId,
            title = d.string("title").orEmpty(),
            note = d.string("note"),
            category =
                d.string("category")?.let { name -> ReminderCategory.entries.firstOrNull { it.name == name } }
                    ?: ReminderCategory.CUSTOM,
            targetIntervalDays = interval,
            jitterPercent = d.int("jitterPercent") ?: 0,
            enabled = d.bool("enabled") ?: true,
            lastFiredAt = d.long("lastFiredAt")?.let(Instant::ofEpochMilli),
            nextFireAt = Instant.ofEpochMilli(d.long("nextFireAt") ?: (createdAt + interval * MILLIS_PER_DAY)),
            createdAt = Instant.ofEpochMilli(createdAt),
            presetKey = d.string("presetKey"),
        )
    }

    fun reminderToDoc(reminder: Reminder): Map<String, Any?> =
        mapOf(
            "personId" to reminder.personId,
            "title" to reminder.title,
            "note" to reminder.note,
            "category" to reminder.category.name,
            "targetIntervalDays" to reminder.targetIntervalDays,
            "jitterPercent" to reminder.jitterPercent,
            "enabled" to reminder.enabled,
            "lastFiredAt" to reminder.lastFiredAt?.toEpochMilli(),
            "nextFireAt" to reminder.nextFireAt.toEpochMilli(),
            "createdAt" to reminder.createdAt.toEpochMilli(),
            "presetKey" to reminder.presetKey,
        )

    // --- reminder completions ---------------------------------------------------------------------

    fun completionFromDoc(doc: CloudDoc): CompletionDoc? {
        val d = doc.data
        val reminderId = d.string("reminderId")?.takeIf { it.isNotEmpty() } ?: return null
        val at = d.long("at")
        val day = d.long("day") ?: at?.let { it / MILLIS_PER_DAY } ?: return null
        return CompletionDoc(reminderId, ReminderDoneEntry(day = LocalDate.ofEpochDay(day), at = at?.let(Instant::ofEpochMilli)))
    }

    fun completionToDoc(
        reminderId: String,
        at: Instant,
        day: LocalDate,
    ): Map<String, Any?> =
        mapOf(
            "reminderId" to reminderId,
            "day" to day.toEpochDay(),
            "at" to at.toEpochMilli(),
        )

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()

    private const val DEFAULT_INTERVAL_DAYS = 30
    private const val MILLIS_PER_DAY = 86_400_000L
}

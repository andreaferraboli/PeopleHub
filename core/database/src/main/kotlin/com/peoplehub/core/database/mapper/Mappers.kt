package com.peoplehub.core.database.mapper

import com.peoplehub.core.database.dao.DueReminderRow
import com.peoplehub.core.database.entity.CheckInEntity
import com.peoplehub.core.database.entity.EventEntity
import com.peoplehub.core.database.entity.InterestEntity
import com.peoplehub.core.database.entity.PersonEntity
import com.peoplehub.core.database.entity.PersonTagEntity
import com.peoplehub.core.database.entity.PersonWithDetails
import com.peoplehub.core.database.entity.ReminderEntity
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.CropTransform
import com.peoplehub.core.domain.model.DueReminder
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCategory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

private fun LocalDateTime.toEpochMillis(): Long =
    atZone(zone).toInstant().toEpochMilli()

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()

/** Builds a domain [CheckInThreshold] from the two nullable columns, or `null` if either is unset. */
private fun thresholdOf(warningDays: Int?, criticalDays: Int?): CheckInThreshold? =
    if (warningDays != null && criticalDays != null) CheckInThreshold(warningDays, criticalDays) else null

/** Maps a [PersonWithDetails] read model to a domain [Person]. */
fun PersonWithDetails.toDomain(): Person =
    Person(
        id = person.id,
        firstName = person.firstName,
        lastName = person.lastName,
        photoPath = person.photoPath,
        birthday = person.birthday?.toLocalDateOrNull(),
        tags = tags.map { it.tag },
        interests = interests.map { Interest(key = it.key, value = it.value, id = it.id) },
        notes = person.notes,
        lastCheckInAt = person.lastCheckInEpochMillis?.let(Instant::ofEpochMilli),
        checkInThreshold = thresholdOf(person.warningDays, person.criticalDays),
        createdAt = Instant.ofEpochMilli(person.createdAtEpochMillis),
        notificationsEnabled = person.notificationsEnabled,
        birthdayOnly = person.birthdayOnly,
        checkInDisabled = person.checkInDisabled,
        isFamily = person.isFamily,
    )

/** Maps a domain [Person] to its [PersonEntity] row (child rows are handled separately). */
fun Person.toEntity(): PersonEntity =
    PersonEntity(
        id = id,
        firstName = firstName,
        lastName = lastName,
        photoPath = photoPath,
        birthday = birthday?.format(DateTimeFormatter.ISO_LOCAL_DATE),
        notes = notes,
        lastCheckInEpochMillis = lastCheckInAt?.toEpochMilli(),
        warningDays = checkInThreshold?.warningDays,
        criticalDays = checkInThreshold?.criticalDays,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        notificationsEnabled = notificationsEnabled,
        birthdayOnly = birthdayOnly,
        checkInDisabled = checkInDisabled,
        isFamily = isFamily,
    )

/** Maps the person's tags to child rows keyed by [personId]. */
fun Person.toTagEntities(personId: Long): List<PersonTagEntity> =
    tags.distinct().map { PersonTagEntity(personId = personId, tag = it) }

/** Maps the person's interests to child rows keyed by [personId]. */
fun Person.toInterestEntities(personId: Long): List<InterestEntity> =
    interests.map { InterestEntity(id = 0L, personId = personId, key = it.key, value = it.value) }

/** Maps a [CheckInEntity] to its domain model. */
fun CheckInEntity.toDomain(): CheckIn =
    CheckIn(
        id = id,
        personId = personId,
        timestamp = Instant.ofEpochMilli(timestampEpochMillis),
        note = note,
    )

/** Maps a domain [CheckIn] to its entity row. */
fun CheckIn.toEntity(): CheckInEntity =
    CheckInEntity(
        id = id,
        personId = personId,
        timestampEpochMillis = timestamp.toEpochMilli(),
        note = note,
    )

/** Maps an [EventEntity] to its domain model. */
fun EventEntity.toDomain(): PersonEvent =
    PersonEvent(
        id = id,
        title = title,
        dateTime = dateTimeEpochMillis.toLocalDateTime(),
        description = description,
        category = category,
        backgroundImagePath = backgroundImagePath,
        backgroundSourcePath = backgroundSourcePath,
        backgroundCrop = CropTransform(zoom = backgroundZoom, panX = backgroundPanX, panY = backgroundPanY),
        personId = personId,
        pinnedToWidget = pinnedToWidget,
    )

/** Maps a domain [PersonEvent] to its entity row. */
fun PersonEvent.toEntity(): EventEntity =
    EventEntity(
        id = id,
        title = title,
        dateTimeEpochMillis = dateTime.toEpochMillis(),
        description = description,
        category = category,
        backgroundImagePath = backgroundImagePath,
        backgroundSourcePath = backgroundSourcePath,
        backgroundZoom = backgroundCrop.zoom,
        backgroundPanX = backgroundCrop.panX,
        backgroundPanY = backgroundCrop.panY,
        personId = personId,
        pinnedToWidget = pinnedToWidget,
    )

/** Parses a stored category name, falling back to [ReminderCategory.CUSTOM] for unknown values. */
private fun String.toReminderCategory(): ReminderCategory =
    runCatching { ReminderCategory.valueOf(this) }.getOrDefault(ReminderCategory.CUSTOM)

/** Maps a [ReminderEntity] to its domain model. */
fun ReminderEntity.toDomain(): Reminder =
    Reminder(
        id = id,
        personId = personId,
        title = title,
        note = note,
        category = category.toReminderCategory(),
        targetIntervalDays = targetIntervalDays,
        jitterPercent = jitterPercent,
        enabled = enabled,
        lastFiredAt = lastFiredEpochMillis?.let(Instant::ofEpochMilli),
        nextFireAt = Instant.ofEpochMilli(nextFireEpochMillis),
        createdAt = Instant.ofEpochMilli(createdEpochMillis),
        presetKey = presetKey,
    )

/** Maps a domain [Reminder] to its entity row. */
fun Reminder.toEntity(): ReminderEntity =
    ReminderEntity(
        id = id,
        personId = personId,
        title = title,
        note = note,
        category = category.name,
        targetIntervalDays = targetIntervalDays,
        jitterPercent = jitterPercent,
        enabled = enabled,
        lastFiredEpochMillis = lastFiredAt?.toEpochMilli(),
        nextFireEpochMillis = nextFireAt.toEpochMilli(),
        createdEpochMillis = createdAt.toEpochMilli(),
        presetKey = presetKey,
    )

/** Maps a joined due-reminder row to its domain model, building the person's display name. */
fun DueReminderRow.toDomain(): DueReminder =
    DueReminder(
        reminderId = id,
        personId = personId,
        personName = "$firstName $lastName".trim(),
        title = title,
        note = note,
    )

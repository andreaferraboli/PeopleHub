package com.peoplehub.core.dataio.mapper

import com.peoplehub.core.dataio.dto.BackupDto
import com.peoplehub.core.dataio.dto.CheckInDto
import com.peoplehub.core.dataio.dto.EventDto
import com.peoplehub.core.dataio.dto.InterestDto
import com.peoplehub.core.dataio.dto.PersonDto
import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

/**
 * Converts a [PersonDto] into the domain [Person].
 *
 * A malformed [PersonDto.birthday] string causes a `DateTimeParseException`, which the calling
 * serializer wraps into a failed [Result]. The check-in threshold is rebuilt only when both
 * `warningDays` and `criticalDays` are present; otherwise it resolves to `null`.
 */
fun PersonDto.toDomain(): Person =
    Person(
        id = id,
        firstName = firstName,
        lastName = lastName,
        photoPath = photoPath,
        birthday = birthday?.let { LocalDate.parse(it, DATE_FORMATTER) },
        tags = tags,
        interests = interests.map(InterestDto::toDomain),
        notes = notes,
        lastCheckInAt = lastCheckInEpochMillis?.let(Instant::ofEpochMilli),
        checkInThreshold = thresholdFrom(warningDays, criticalDays),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        notificationsEnabled = notificationsEnabled,
        birthdayOnly = birthdayOnly,
        checkInDisabled = checkInDisabled,
        isFamily = isFamily,
    )

/** Converts a domain [Person] into its serializable [PersonDto]. */
fun Person.toDto(): PersonDto =
    PersonDto(
        id = id,
        firstName = firstName,
        lastName = lastName,
        photoPath = photoPath,
        birthday = birthday?.format(DATE_FORMATTER),
        tags = tags,
        interests = interests.map(Interest::toDto),
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

/** Converts an [InterestDto] into the domain [Interest]. */
fun InterestDto.toDomain(): Interest = Interest(key = key, value = value, id = id)

/** Converts a domain [Interest] into its serializable [InterestDto]. */
fun Interest.toDto(): InterestDto = InterestDto(key = key, value = value, id = id)

/** Converts a [CheckInDto] into the domain [CheckIn]. */
fun CheckInDto.toDomain(): CheckIn =
    CheckIn(
        id = id,
        personId = personId,
        timestamp = Instant.ofEpochMilli(timestampEpochMillis),
        note = note,
    )

/** Converts a domain [CheckIn] into its serializable [CheckInDto]. */
fun CheckIn.toDto(): CheckInDto =
    CheckInDto(
        id = id,
        personId = personId,
        timestampEpochMillis = timestamp.toEpochMilli(),
        note = note,
    )

/**
 * Converts an [EventDto] into the domain [PersonEvent].
 *
 * A malformed [EventDto.dateTime] string causes a `DateTimeParseException`, which the calling
 * serializer wraps into a failed [Result].
 */
fun EventDto.toDomain(): PersonEvent =
    PersonEvent(
        id = id,
        title = title,
        dateTime = LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER),
        description = description,
        category = category,
        backgroundImagePath = backgroundImagePath,
        personId = personId,
        pinnedToWidget = pinnedToWidget,
    )

/** Converts a domain [PersonEvent] into its serializable [EventDto]. */
fun PersonEvent.toDto(): EventDto =
    EventDto(
        id = id,
        title = title,
        dateTime = dateTime.format(DATE_TIME_FORMATTER),
        description = description,
        category = category,
        backgroundImagePath = backgroundImagePath,
        personId = personId,
        pinnedToWidget = pinnedToWidget,
    )

/** Converts a [BackupDto] into the domain [BackupData]. */
fun BackupDto.toDomain(): BackupData =
    BackupData(
        schemaVersion = schemaVersion,
        people = people.map(PersonDto::toDomain),
        checkIns = checkIns.map(CheckInDto::toDomain),
        events = events.map(EventDto::toDomain),
    )

/** Converts a domain [BackupData] snapshot into its serializable [BackupDto]. */
fun BackupData.toDto(): BackupDto =
    BackupDto(
        schemaVersion = schemaVersion,
        people = people.map(Person::toDto),
        checkIns = checkIns.map(CheckIn::toDto),
        events = events.map(PersonEvent::toDto),
    )

/**
 * Rebuilds a [CheckInThreshold] from the flattened [warningDays]/[criticalDays] pair.
 *
 * Returns `null` unless both values are present, mirroring the optional per-person override.
 */
private fun thresholdFrom(warningDays: Int?, criticalDays: Int?): CheckInThreshold? =
    if (warningDays != null && criticalDays != null) {
        CheckInThreshold(warningDays = warningDays, criticalDays = criticalDays)
    } else {
        null
    }

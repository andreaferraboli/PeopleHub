package com.peoplehub.core.dataio.dto

import kotlinx.serialization.Serializable

/**
 * Serialization-friendly mirror of the domain `Person`.
 *
 * Uses only primitive/JSON-native types, apart from the ids ([FlexibleIdSerializer]: strings now,
 * numbers in files written before the data moved to the cloud): the [birthday] is an ISO-8601 local
 * date string, instants are epoch-millis longs and the per-person check-in threshold is flattened into
 * [warningDays]/[criticalDays].
 *
 * Every field carries a default so that older or partial JSON decodes gracefully.
 */
@Serializable
data class PersonDto(
    @Serializable(with = FlexibleIdSerializer::class)
    val id: String = "",
    val firstName: String,
    val lastName: String,
    val photoPath: String? = null,
    val birthday: String? = null,
    val tags: List<String> = emptyList(),
    val interests: List<InterestDto> = emptyList(),
    val notes: String = "",
    val lastCheckInEpochMillis: Long? = null,
    val warningDays: Int? = null,
    val criticalDays: Int? = null,
    val createdAtEpochMillis: Long = 0L,
    val notificationsEnabled: Boolean = false,
    val birthdayOnly: Boolean = false,
    val checkInDisabled: Boolean = false,
    val isFamily: Boolean = false,
    /**
     * The person's full meetup history, so a single-profile export/import round-trips every recorded
     * date and not just the denormalised [lastCheckInEpochMillis]. Empty in older files.
     */
    val checkIns: List<CheckInDto> = emptyList(),
)

/** Serialization-friendly mirror of the domain `Interest` (an editable key/value pair). */
@Serializable
data class InterestDto(
    val key: String,
    val value: String,
)

/**
 * Serialization-friendly mirror of the domain `CheckIn`; the timestamp is epoch-millis.
 *
 * [outingId] groups the check-ins recorded for one shared outing. It is missing (or `0`) in files
 * written before outings existed; the import then rebuilds the groups from the day and the note.
 * [endEpochMillis] is when the outing ended, `null` when it has no end (and in older files).
 */
@Serializable
data class CheckInDto(
    @Serializable(with = FlexibleIdSerializer::class)
    val id: String = "",
    @Serializable(with = FlexibleIdSerializer::class)
    val personId: String,
    val timestampEpochMillis: Long,
    val note: String? = null,
    @Serializable(with = FlexibleIdSerializer::class)
    val outingId: String = "",
    val endEpochMillis: Long? = null,
)

/** Serialization-friendly mirror of the domain `PersonEvent`; [dateTime] is an ISO local date-time string. */
@Serializable
data class EventDto(
    @Serializable(with = FlexibleIdSerializer::class)
    val id: String = "",
    val title: String,
    val dateTime: String,
    val description: String? = null,
    val category: String? = null,
    val backgroundImagePath: String? = null,
    val backgroundSourcePath: String? = null,
    val backgroundZoom: Float = 1f,
    val backgroundPanX: Float = 0f,
    val backgroundPanY: Float = 0f,
    @Serializable(with = NullableFlexibleIdSerializer::class)
    val personId: String? = null,
    val pinnedToWidget: Boolean = false,
)

/** Top-level serialization-friendly mirror of the domain `BackupData` snapshot. */
@Serializable
data class BackupDto(
    val schemaVersion: Int,
    val people: List<PersonDto> = emptyList(),
    val checkIns: List<CheckInDto> = emptyList(),
    val events: List<EventDto> = emptyList(),
)

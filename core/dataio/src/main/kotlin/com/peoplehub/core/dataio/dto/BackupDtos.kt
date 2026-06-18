package com.peoplehub.core.dataio.dto

import kotlinx.serialization.Serializable

/**
 * Serialization-friendly mirror of the domain `Person`.
 *
 * Uses only primitive/JSON-native types so that no custom serializers are required:
 * the [birthday] is an ISO-8601 local date string, instants are epoch-millis longs and the
 * per-person check-in threshold is flattened into [warningDays]/[criticalDays].
 *
 * Every field carries a default so that older or partial JSON decodes gracefully.
 */
@Serializable
data class PersonDto(
    val id: Long = 0L,
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
)

/** Serialization-friendly mirror of the domain `Interest` (an editable key/value pair). */
@Serializable
data class InterestDto(
    val key: String,
    val value: String,
    val id: Long = 0L,
)

/** Serialization-friendly mirror of the domain `CheckIn`; the timestamp is epoch-millis. */
@Serializable
data class CheckInDto(
    val id: Long = 0L,
    val personId: Long,
    val timestampEpochMillis: Long,
    val note: String? = null,
)

/** Serialization-friendly mirror of the domain `PersonEvent`; [dateTime] is an ISO local date-time string. */
@Serializable
data class EventDto(
    val id: Long = 0L,
    val title: String,
    val dateTime: String,
    val description: String? = null,
    val category: String? = null,
    val backgroundImagePath: String? = null,
    val personId: Long? = null,
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

package com.peoplehub.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A person tracked inside the relationship hub.
 *
 * This is the central aggregate of the domain: it carries identity, optional biographical data
 * (birthday, photo), free-form personalisation ([tags], [interests], [notes]) and the denormalised
 * [lastCheckInAt] used to drive the "seen X days ago" frequency tracker.
 *
 * @property id stable database identifier; `0` denotes a not-yet-persisted person.
 * @property checkInThreshold per-person override of the global check-in thresholds, or `null` to
 * fall back to [AppSettings.defaultCheckInThreshold].
 */
data class Person(
    val id: Long = 0L,
    val firstName: String,
    val lastName: String,
    val photoPath: String? = null,
    val birthday: LocalDate? = null,
    val tags: List<String> = emptyList(),
    val interests: List<Interest> = emptyList(),
    val notes: String = "",
    val lastCheckInAt: Instant? = null,
    val checkInThreshold: CheckInThreshold? = null,
    val createdAt: Instant = Instant.EPOCH,
) {
    /** Display name combining first and last name, trimmed of stray whitespace. */
    val fullName: String
        get() = "$firstName $lastName".trim()

    /** Up to two uppercase initials used by the avatar fallback when no [photoPath] is set. */
    val initials: String
        get() = buildString {
            firstName.trim().firstOrNull()?.let(::append)
            lastName.trim().firstOrNull()?.let(::append)
        }.uppercase().ifEmpty { "?" }
}

/**
 * A free-form "interests & tastes" entry, modelled as an editable key/value pair
 * (e.g. `"Favourite food" -> "Sushi"`).
 */
data class Interest(
    val key: String,
    val value: String,
    val id: Long = 0L,
)

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
 * @property notificationsEnabled whether this person may trigger check-in and birthday
 * notifications. Defaults to `false`, so a new (or migrated) profile is silent until the user opts
 * in explicitly.
 * @property birthdayOnly whether this entry is a bare birthday rather than a tracked relationship.
 * Birthday-only entries are hidden from the directory ("The Circle") and the urgent check-ins, but
 * still appear in the Milestones / birthday calendar.
 * @property checkInDisabled when `true` the person is excluded from check-in tracking entirely: they
 * never surface in the urgent check-ins, the widget, or the reminder worker, regardless of how long
 * it has been since the last interaction. Set in bulk via the directory's "never" action.
 * @property isFamily when `true` the person is a family member: the "seen X days ago" frequency
 * tracker and its reminders do not apply to them (you see them all the time), so they never surface
 * in the urgent check-ins, the widget, or the reminder worker. Instead of a check-in badge the UI
 * simply shows a "Family" label.
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
    val notificationsEnabled: Boolean = false,
    val birthdayOnly: Boolean = false,
    val checkInDisabled: Boolean = false,
    val isFamily: Boolean = false,
) {
    /** Display name combining first and last name, trimmed of stray whitespace. */
    val fullName: String
        get() = "$firstName $lastName".trim()

    /** Up to two uppercase initials used by the avatar fallback when no [photoPath] is set. */
    val initials: String
        get() =
            buildString {
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

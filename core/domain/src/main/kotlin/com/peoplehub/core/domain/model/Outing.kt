package com.peoplehub.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * One recorded outing — a day, an optional shared description, and everyone who was there.
 *
 * An outing is a *view* over the [CheckIn] rows that share a [CheckIn.outingId]: the storage stays
 * per-person (so every attendee keeps the meeting in their own history and their own cadence tracker),
 * while the outing is what the user actually recorded and what the "outings history" edits as a unit.
 * A quick one-person check-in is simply an outing with a single attendee.
 *
 * @property id the shared [CheckIn.outingId] of every attendee's row.
 * @property date the calendar day of the outing, resolved in the device's zone.
 * @property timestamp the instant the underlying check-ins carry (noon of [date] for recorded outings).
 * @property note the shared free-text description, or `null` when none was written.
 * @property attendees everyone who took part, one entry per person, ordered by display name.
 * @property checkInIds every underlying check-in row, so the whole outing can be deleted or moved.
 */
data class Outing(
    val id: Long,
    val date: LocalDate,
    val timestamp: Instant,
    val note: String?,
    val attendees: List<OutingAttendee>,
    val checkInIds: List<Long>,
)

/**
 * One participant of an [Outing], denormalised with the display data the outing cards need so the UI
 * never has to look people up row by row.
 *
 * @property checkInId the attendee's own check-in row inside the outing.
 */
data class OutingAttendee(
    val checkInId: Long,
    val personId: Long,
    val fullName: String,
    val initials: String,
    val photoPath: String?,
)

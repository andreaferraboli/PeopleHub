package com.peoplehub.core.data.query

import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.domain.model.OutingAttendee
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.validEndAfter
import java.time.ZoneId

/**
 * Folds check-ins into [Outing]s: rows sharing an `outingId` are one outing, most recent first.
 *
 * Check-ins whose person no longer exists are left out (the same rows a join with the people would
 * drop). The day is resolved in [zone]; duplicate rows for the same person collapse into a single
 * attendee while every underlying row id is kept, so deletes and moves stay complete. The outing's end
 * is the one its rows carry (the same on each), when it falls after the start.
 */
internal object Outings {
    fun group(
        checkIns: List<CheckIn>,
        people: Map<String, Person>,
        zone: ZoneId,
    ): List<Outing> =
        checkIns
            .filter { it.personId in people }
            .groupBy { it.outingId }
            .map { (outingId, rows) -> toOuting(outingId, rows, people, zone) }
            .sortedWith(compareByDescending<Outing> { it.timestamp }.thenByDescending { it.id })

    private fun toOuting(
        outingId: String,
        rows: List<CheckIn>,
        people: Map<String, Person>,
        zone: ZoneId,
    ): Outing {
        val timestamp = rows.minOf { it.timestamp }
        // Every row carries the same end by construction; the latest one wins should they ever differ.
        val endAt = rows.mapNotNull { it.endAt }.maxOrNull().validEndAfter(timestamp)
        return Outing(
            id = outingId,
            date = timestamp.atZone(zone).toLocalDate(),
            timestamp = timestamp,
            note = rows.firstNotNullOfOrNull { row -> row.note?.takeIf(String::isNotBlank) },
            attendees =
                rows
                    .distinctBy { it.personId }
                    .map { row ->
                        val person = people.getValue(row.personId)
                        OutingAttendee(
                            checkInId = row.id,
                            personId = row.personId,
                            fullName = person.fullName,
                            initials = person.initials,
                            photoPath = person.photoPath,
                        )
                    }.sortedBy { it.fullName.lowercase() },
            checkInIds = rows.map { it.id },
            endAt = endAt,
        )
    }
}

package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.CheckInUrgency
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.repository.PeopleRepository
import com.peoplehub.core.domain.repository.SettingsRepository
import com.peoplehub.core.domain.util.DateCalculations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Records that a person was seen, creating a [CheckIn] and updating the person's denormalised
 * last-seen timestamp in a single logical action. The check-in defaults to "now" but a specific
 * [at] instant may be supplied to back-date a meeting that happened on an earlier day. A back-dated
 * check-in never regresses a more recent last-seen value.
 */
class CheckInPersonUseCase
    @Inject
    constructor(
        private val checkInRepository: CheckInRepository,
        private val peopleRepository: PeopleRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(personId: Long, note: String? = null, at: Instant? = null): Long {
            val timestamp = at ?: clock.instant()
            val id =
                checkInRepository.recordCheckIn(
                    CheckIn(
                        personId = personId,
                        timestamp = timestamp,
                        note = note?.takeIf(String::isNotBlank),
                        outingId = checkInRepository.newOutingId(),
                    ),
                )
            val previous = peopleRepository.getPerson(personId)?.lastCheckInAt
            if (previous == null || timestamp.isAfter(previous)) {
                peopleRepository.updateLastCheckIn(personId, timestamp.toEpochMilli())
            }
            return id
        }
    }

/**
 * Records a single meetup that may span several days and/or involve several people at once — the
 * "record an outing" flow. Every combination of [personIds] and [days] becomes one [CheckIn] (a
 * weekend away with someone yields three rows; an outing with four friends on one day yields four),
 * so each person independently keeps the full history and the denormalised last-seen timestamp is
 * re-derived per attendee. Each day is anchored at noon in the clock's zone so the calendar-day math
 * is stable regardless of the exact hour.
 *
 * Every day gets its own [CheckIn.outingId], shared by all its attendees: that is what lets the
 * outings history show the meetup as one card and edit its date, description and attendees as a unit.
 * A multi-day meetup is therefore one outing per day, each editable on its own.
 */
class RecordMeetupUseCase
    @Inject
    constructor(
        private val checkInRepository: CheckInRepository,
        private val peopleRepository: PeopleRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(personIds: List<Long>, days: List<LocalDate>, note: String? = null) {
            if (personIds.isEmpty() || days.isEmpty()) return
            val cleanNote = note?.takeIf(String::isNotBlank)
            val people = personIds.distinct()
            // One id per day, all reserved up front: nothing is written yet, so asking for the "next"
            // id repeatedly would hand out the same number for every day.
            val firstOutingId = checkInRepository.newOutingId()
            val checkIns =
                days.distinct().sorted().flatMapIndexed { dayIndex, day ->
                    people.map { personId ->
                        CheckIn(
                            personId = personId,
                            timestamp = day.atTime(NOON_HOUR, 0).atZone(clock.zone).toInstant(),
                            note = cleanNote,
                            outingId = firstOutingId + dayIndex,
                        )
                    }
                }
            checkInRepository.recordCheckIns(checkIns)
            people.forEach { personId ->
                refreshLastCheckIn(checkInRepository, peopleRepository, personId)
            }
        }

        private companion object {
            const val NOON_HOUR = 12
        }
    }

/**
 * Edits **one person's** check-in (its day and/or note) and re-derives their denormalised last-seen
 * timestamp from the remaining history, so moving the latest check-in keeps the cadence tracker in
 * sync.
 *
 * When the edited row belongs to an outing shared with other people, it is detached into an outing of
 * its own: the edit was made from a single person's history, so it must not silently rewrite the day
 * or description everyone else sees. Editing the shared outing as a whole is what
 * [UpdateOutingUseCase] is for.
 */
class UpdateCheckInUseCase
    @Inject
    constructor(
        private val checkInRepository: CheckInRepository,
        private val peopleRepository: PeopleRepository,
    ) {
        suspend operator fun invoke(checkIn: CheckIn) {
            val outing = checkIn.outingId.takeIf { it > 0L }?.let { checkInRepository.getOuting(it) }
            val isShared = outing != null && outing.attendees.any { it.personId != checkIn.personId }
            val outingId =
                if (isShared || checkIn.outingId <= 0L) checkInRepository.newOutingId() else checkIn.outingId
            checkInRepository.updateCheckIn(
                checkIn.copy(note = checkIn.note?.takeIf(String::isNotBlank), outingId = outingId),
            )
            refreshLastCheckIn(checkInRepository, peopleRepository, checkIn.personId)
        }
    }

/**
 * Deletes one or more of a person's check-ins and re-derives their denormalised last-seen timestamp
 * from whatever history remains (clearing it when none is left).
 */
class DeleteCheckInsUseCase
    @Inject
    constructor(
        private val checkInRepository: CheckInRepository,
        private val peopleRepository: PeopleRepository,
    ) {
        suspend operator fun invoke(personId: Long, ids: List<Long>) {
            if (ids.isEmpty()) return
            checkInRepository.deleteCheckIns(ids)
            refreshLastCheckIn(checkInRepository, peopleRepository, personId)
        }
    }

/** Re-derives a person's denormalised last-seen timestamp from their most recent surviving check-in. */
internal suspend fun refreshLastCheckIn(
    checkInRepository: CheckInRepository,
    peopleRepository: PeopleRepository,
    personId: Long,
) {
    val latest = checkInRepository.latestTimestamp(personId)
    peopleRepository.updateLastCheckIn(personId, latest?.toEpochMilli())
}

/** Observes the reverse-chronological check-in history for a person. */
class ObserveCheckInHistoryUseCase
    @Inject
    constructor(
        private val repository: CheckInRepository,
    ) {
        operator fun invoke(personId: Long): Flow<List<CheckIn>> = repository.observeHistory(personId)
    }

/**
 * Observes people whose check-in recency has reached at least the warning threshold, ordered by
 * urgency (never-seen and most-overdue first). Drives the home "urgent check-ins" section and the
 * check-in widget.
 *
 * People who are not part of the cadence tracker never appear: birthday-only entries (bare
 * birthdays, filtered out at the query), family members (seen all the time) and anyone with
 * check-ins explicitly disabled.
 */
class GetUrgentCheckInsUseCase
    @Inject
    constructor(
        private val peopleRepository: PeopleRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(): Flow<List<CheckInUrgency>> =
            combine(
                peopleRepository.observePeople(PeopleFilter(includeBirthdayOnly = false)),
                settingsRepository.settings,
            ) { people, settings ->
                val now = clock.instant()
                people
                    .filter { !it.checkInDisabled && !it.isFamily && !it.birthdayOnly }
                    .map { person ->
                        val threshold = person.checkInThreshold ?: settings.defaultCheckInThreshold
                        val daysSince = person.lastCheckInAt?.let { DateCalculations.daysSince(it, now) }
                        CheckInUrgency(person, daysSince, CheckInStatus.of(daysSince, threshold))
                    }.filter { it.status != CheckInStatus.FRESH }
                    .sortedWith(
                        compareByDescending<CheckInUrgency> { it.status.urgencyRank() }
                            .thenByDescending { it.daysSince ?: Long.MAX_VALUE },
                    )
            }

        private fun CheckInStatus.urgencyRank(): Int =
            when (this) {
                CheckInStatus.NEVER -> 3
                CheckInStatus.OVERDUE -> 2
                CheckInStatus.DUE -> 1
                CheckInStatus.FRESH -> 0
            }
    }

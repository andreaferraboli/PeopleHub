package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.repository.PeopleRepository
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Observes the whole outings history, most recent first. */
class ObserveOutingsUseCase
    @Inject
    constructor(
        private val repository: CheckInRepository,
    ) {
        operator fun invoke(): Flow<List<Outing>> = repository.observeOutings()
    }

/** One-shot read of a single outing, used to seed the edit form. */
class GetOutingUseCase
    @Inject
    constructor(
        private val repository: CheckInRepository,
    ) {
        suspend operator fun invoke(outingId: Long): Outing? = repository.getOuting(outingId)
    }

/**
 * Rewrites an existing outing as a whole: its day, its description **and** who took part.
 *
 * Because an outing is stored as one check-in per attendee, the edit is applied row by row — the rows
 * of people who are still there are moved to the new day and description, the rows of people who were
 * removed are deleted, and freshly added people get a new row under the same outing id. Everyone
 * touched on either side (before or after) then has their denormalised last-seen timestamp
 * re-derived, so removing the person you saw most recently rolls the cadence tracker back correctly.
 *
 * Fails when [personIds] is empty (an outing without attendees is not an outing — delete it instead
 * via [DeleteOutingUseCase]) or when the outing no longer exists.
 */
class UpdateOutingUseCase
    @Inject
    constructor(
        private val checkInRepository: CheckInRepository,
        private val peopleRepository: PeopleRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(
            outingId: Long,
            day: LocalDate,
            personIds: List<Long>,
            note: String? = null,
        ): Result<Unit> =
            runCatching {
                val wanted = personIds.distinct()
                require(wanted.isNotEmpty()) { "An outing needs at least one person" }
                val outing = checkInRepository.getOuting(outingId)
                requireNotNull(outing) { "Outing $outingId no longer exists" }

                val timestamp = day.atTime(NOON_HOUR, 0).atZone(clock.zone).toInstant()
                val cleanNote = note?.trim()?.takeIf(String::isNotBlank)
                val existingByPerson = outing.attendees.associateBy { it.personId }

                val keptIds = wanted.mapNotNull { existingByPerson[it]?.checkInId }.toSet()
                val removedCheckInIds = outing.checkInIds.filterNot { it in keptIds }
                if (removedCheckInIds.isNotEmpty()) checkInRepository.deleteCheckIns(removedCheckInIds)

                wanted.forEach { personId ->
                    val attendee = existingByPerson[personId]
                    if (attendee == null) {
                        checkInRepository.recordCheckIn(
                            CheckIn(
                                personId = personId,
                                timestamp = timestamp,
                                note = cleanNote,
                                outingId = outingId,
                            ),
                        )
                    } else {
                        checkInRepository.updateCheckIn(
                            CheckIn(
                                id = attendee.checkInId,
                                personId = personId,
                                timestamp = timestamp,
                                note = cleanNote,
                                outingId = outingId,
                            ),
                        )
                    }
                }

                (outing.attendees.map { it.personId } + wanted).distinct().forEach { personId ->
                    refreshLastCheckIn(checkInRepository, peopleRepository, personId)
                }
            }

        private companion object {
            const val NOON_HOUR = 12
        }
    }

/**
 * Deletes an entire outing — every attendee's check-in row — and re-derives each of their last-seen
 * timestamps from whatever history remains.
 */
class DeleteOutingUseCase
    @Inject
    constructor(
        private val checkInRepository: CheckInRepository,
        private val peopleRepository: PeopleRepository,
    ) {
        suspend operator fun invoke(outingId: Long) {
            val outing = checkInRepository.getOuting(outingId) ?: return
            checkInRepository.deleteCheckIns(outing.checkInIds)
            outing.attendees.forEach { attendee ->
                refreshLastCheckIn(checkInRepository, peopleRepository, attendee.personId)
            }
        }
    }

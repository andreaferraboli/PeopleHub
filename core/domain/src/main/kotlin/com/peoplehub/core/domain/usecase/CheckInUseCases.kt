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
import javax.inject.Inject

/**
 * Records that a person was seen "today", creating a [CheckIn] and updating the person's
 * denormalised last-seen timestamp in a single logical action.
 */
class CheckInPersonUseCase
    @Inject
    constructor(
        private val checkInRepository: CheckInRepository,
        private val peopleRepository: PeopleRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(personId: Long, note: String? = null): Long {
            val now = clock.instant()
            val id =
                checkInRepository.recordCheckIn(
                    CheckIn(personId = personId, timestamp = now, note = note?.takeIf(String::isNotBlank)),
                )
            peopleRepository.updateLastCheckIn(personId, now.toEpochMilli())
            return id
        }
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
                peopleRepository.observePeople(PeopleFilter()),
                settingsRepository.settings,
            ) { people, settings ->
                val now = clock.instant()
                people
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

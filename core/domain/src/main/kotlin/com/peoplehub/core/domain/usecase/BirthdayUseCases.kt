package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.domain.repository.PeopleRepository
import com.peoplehub.core.domain.util.DateCalculations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Observes every person who has a birthday, projected onto its next occurrence and sorted by how
 * soon it arrives. The calendar (year/month/week) views all derive from this single stream.
 */
class GetAllBirthdaysUseCase @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<List<UpcomingBirthday>> =
        peopleRepository.observePeople(PeopleFilter()).map { people ->
            val today = LocalDate.now(clock)
            people
                .filter { it.birthday != null }
                .map { person ->
                    val birthday = requireNotNull(person.birthday)
                    UpcomingBirthday(
                        personId = person.id,
                        fullName = person.fullName,
                        photoPath = person.photoPath,
                        birthday = birthday,
                        nextOccurrence = DateCalculations.nextBirthdayOccurrence(birthday, today),
                        daysUntil = DateCalculations.daysUntilBirthday(birthday, today),
                        turningAge = DateCalculations.ageOnNextBirthday(birthday, today),
                        notificationsEnabled = person.notificationsEnabled,
                    )
                }
                .sortedBy { it.daysUntil }
        }
}

/**
 * Observes upcoming birthdays falling within [withinDays] from today (default 30), used by the
 * "next 30 days" list and the birthday widget.
 */
class GetUpcomingBirthdaysUseCase @Inject constructor(
    private val getAllBirthdays: GetAllBirthdaysUseCase,
) {
    operator fun invoke(withinDays: Int = DEFAULT_WINDOW_DAYS): Flow<List<UpcomingBirthday>> =
        getAllBirthdays().map { all -> all.filter { it.daysUntil <= withinDays } }

    companion object {
        const val DEFAULT_WINDOW_DAYS: Int = 30
    }
}

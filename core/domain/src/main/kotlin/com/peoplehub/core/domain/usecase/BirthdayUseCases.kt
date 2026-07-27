package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.domain.repository.PeopleRepository
import com.peoplehub.core.domain.repository.SettingsRepository
import com.peoplehub.core.domain.util.DateCalculations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Observes every person who has a birthday, projected onto its next occurrence and sorted by how
 * soon it arrives. The calendar (year/month/week) views all derive from this single stream.
 */
class GetAllBirthdaysUseCase
    @Inject
    constructor(
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
                        )
                    }.sortedBy { it.daysUntil }
            }
    }

/**
 * Observes upcoming birthdays falling within [withinDays] from today (default 30), used by the
 * "next 30 days" list and the birthday widget.
 */
class GetUpcomingBirthdaysUseCase
    @Inject
    constructor(
        private val getAllBirthdays: GetAllBirthdaysUseCase,
    ) {
        operator fun invoke(withinDays: Int = DEFAULT_WINDOW_DAYS): Flow<List<UpcomingBirthday>> =
            getAllBirthdays().map { all -> all.filter { it.daysUntil <= withinDays } }

        companion object {
            const val DEFAULT_WINDOW_DAYS: Int = 30
        }
    }

/**
 * A birthday whose distance from today matches one of the globally enabled reminder offsets, i.e.
 * one that should be notified in today's sweep.
 *
 * @property isToday whether this is the birthday itself (the "happy birthday" greeting) rather than
 * an advance reminder.
 */
data class DueBirthdayReminder(
    val personId: Long,
    val fullName: String,
    val daysUntil: Int,
) {
    val isToday: Boolean get() = daysUntil == ReminderOffset.SAME_DAY.daysBefore
}

/**
 * Resolves which birthdays today's reminder sweep should notify: every birthday whose remaining day
 * count matches an enabled [ReminderOffset].
 *
 * Deliberately independent of the per-person `notificationsEnabled` opt-in — that toggle governs
 * check-in reminders only. Gating birthdays on it silently muted every profile added or imported
 * without flipping the switch, which defaults to off.
 */
class GetDueBirthdayRemindersUseCase
    @Inject
    constructor(
        private val getAllBirthdays: GetAllBirthdaysUseCase,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(): List<DueBirthdayReminder> {
            val enabledOffsets =
                settingsRepository.settings
                    .first()
                    .birthdayReminderOffsets
                    .map { it.daysBefore }
                    .toSet()
            return getAllBirthdays()
                .first()
                .filter { it.daysUntil in enabledOffsets }
                .map { DueBirthdayReminder(it.personId, it.fullName, it.daysUntil) }
        }
    }

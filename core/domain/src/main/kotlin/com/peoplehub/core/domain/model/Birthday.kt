package com.peoplehub.core.domain.model

import java.time.LocalDate

/**
 * A person's birthday projected onto its next occurrence, with the remaining day count and the age
 * they will turn.
 *
 * @property nextOccurrence the next calendar date the birthday falls on (today counts as 0 days).
 * @property turningAge the age reached on [nextOccurrence], or `null` if the birth year is unknown.
 */
data class UpcomingBirthday(
    val personId: Long,
    val fullName: String,
    val photoPath: String?,
    val birthday: LocalDate,
    val nextOccurrence: LocalDate,
    val daysUntil: Int,
    val turningAge: Int?,
)

/**
 * How far ahead of a birthday a reminder fires. Multiple offsets can be active independently.
 *
 * @property daysBefore number of days before the birthday the reminder should trigger.
 */
enum class ReminderOffset(val daysBefore: Int) {
    ONE_DAY(1),
    THREE_DAYS(3),
    ONE_WEEK(7),
    ONE_MONTH(30),

    ;

    companion object {
        /** Reminder offsets that should use exact alarms because they fire within 24 hours. */
        val ExactWindowDays: Int = 1
    }
}

package com.peoplehub.core.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure, side-effect-free date arithmetic shared across the domain. Every function takes its
 * reference point ("today"/"now") as a parameter so the logic stays deterministic and testable.
 */
object DateCalculations {
    /**
     * Whole days elapsed between [past] and [now]. Returns 0 for future instants.
     */
    fun daysSince(past: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Long {
        val pastDate = past.atZone(zone).toLocalDate()
        val nowDate = now.atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(pastDate, nowDate).coerceAtLeast(0)
    }

    /**
     * The next calendar date a [birthday] occurs on or after [today].
     *
     * Handles Feb 29 birthdays in non-leap years by falling back to Feb 28.
     */
    fun nextBirthdayOccurrence(birthday: LocalDate, today: LocalDate): LocalDate {
        val candidate = safeWithYear(birthday, today.year)
        return if (candidate.isBefore(today)) safeWithYear(birthday, today.year + 1) else candidate
    }

    /** Whole days from [today] until the next occurrence of [birthday] (0 when it is today). */
    fun daysUntilBirthday(birthday: LocalDate, today: LocalDate): Int {
        val next = nextBirthdayOccurrence(birthday, today)
        return ChronoUnit.DAYS.between(today, next).toInt()
    }

    /**
     * The age the person turns on the next occurrence of [birthday], or `null` when the birth year
     * carries no meaningful age (year 0 / unset).
     */
    fun ageOnNextBirthday(birthday: LocalDate, today: LocalDate): Int? {
        if (birthday.year <= MIN_MEANINGFUL_YEAR) return null
        val next = nextBirthdayOccurrence(birthday, today)
        return next.year - birthday.year
    }

    /** The person's current age in whole years, or `null` when the birth year is unknown. */
    fun currentAge(birthday: LocalDate, today: LocalDate): Int? {
        if (birthday.year <= MIN_MEANINGFUL_YEAR) return null
        return Period.between(birthday, today).years
    }

    /** Whole days between [date] and [today]: negative for the past, positive for the future. */
    fun signedDaysFromToday(date: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, date)

    private fun safeWithYear(birthday: LocalDate, year: Int): LocalDate =
        if (birthday.monthValue == FEBRUARY && birthday.dayOfMonth == LEAP_DAY && !LocalDate.ofYearDay(year, 1).isLeapYear) {
            LocalDate.of(year, FEBRUARY, LEAP_DAY - 1)
        } else {
            birthday.withYear(year)
        }

    private const val FEBRUARY = 2
    private const val LEAP_DAY = 29
    private const val MIN_MEANINGFUL_YEAR = 1
}

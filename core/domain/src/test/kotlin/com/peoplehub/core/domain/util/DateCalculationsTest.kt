package com.peoplehub.core.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DateCalculationsTest {

    @Test
    fun `next birthday occurrence is later this year when it has not passed`() {
        val today = LocalDate.of(2026, 6, 10)
        val birthday = LocalDate.of(1990, 10, 12)

        val next = DateCalculations.nextBirthdayOccurrence(birthday, today)

        assertEquals(LocalDate.of(2026, 10, 12), next)
    }

    @Test
    fun `next birthday occurrence rolls to next year when it already passed`() {
        val today = LocalDate.of(2026, 11, 1)
        val birthday = LocalDate.of(1990, 10, 12)

        val next = DateCalculations.nextBirthdayOccurrence(birthday, today)

        assertEquals(LocalDate.of(2027, 10, 12), next)
    }

    @Test
    fun `birthday today counts as zero days away`() {
        val today = LocalDate.of(2026, 10, 12)
        val birthday = LocalDate.of(1990, 10, 12)

        assertEquals(0, DateCalculations.daysUntilBirthday(birthday, today))
    }

    @Test
    fun `feb 29 birthday falls back to feb 28 in a non-leap year`() {
        val today = LocalDate.of(2026, 1, 1) // 2026 is not a leap year
        val birthday = LocalDate.of(2000, 2, 29)

        val next = DateCalculations.nextBirthdayOccurrence(birthday, today)

        assertEquals(LocalDate.of(2026, 2, 28), next)
    }

    @Test
    fun `days since counts whole days and never goes negative`() {
        val zone = ZoneOffset.UTC
        val past = Instant.parse("2026-06-01T08:00:00Z")
        val now = Instant.parse("2026-06-10T09:00:00Z")

        assertEquals(9, DateCalculations.daysSince(past, now, zone))
        assertEquals(0, DateCalculations.daysSince(now, past, zone))
    }

    @Test
    fun `age on next birthday is computed from the birth year`() {
        val today = LocalDate.of(2026, 6, 10)
        val birthday = LocalDate.of(1990, 10, 12)

        assertEquals(36, DateCalculations.ageOnNextBirthday(birthday, today))
    }

    @Test
    fun `age is null when the birth year is unset`() {
        val today = LocalDate.of(2026, 6, 10)
        val birthday = LocalDate.of(1, 10, 12)

        assertNull(DateCalculations.ageOnNextBirthday(birthday, today))
        assertNull(DateCalculations.currentAge(birthday, today))
    }

    @Test
    fun `signed days from today is negative for the past and positive for the future`() {
        val today = LocalDate.of(2026, 6, 10)

        assertEquals(-5, DateCalculations.signedDaysFromToday(LocalDate.of(2026, 6, 5), today))
        assertEquals(4, DateCalculations.signedDaysFromToday(LocalDate.of(2026, 6, 14), today))
    }
}

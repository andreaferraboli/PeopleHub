package com.peoplehub.core.domain.util

import com.peoplehub.core.domain.model.ReminderDoneEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class ReminderSchedulingTest {
    @Test
    fun `window is centred on the target and sized by the jitter`() {
        // 14 days, 20% jitter -> +-3 days -> 11..17.
        assertEquals(11..17, ReminderScheduling.window(targetIntervalDays = 14, jitterPercent = 20))
    }

    @Test
    fun `zero jitter yields a fixed one-day window at the target`() {
        assertEquals(30..30, ReminderScheduling.window(targetIntervalDays = 30, jitterPercent = 0))
    }

    @Test
    fun `window never dips below one day`() {
        val window = ReminderScheduling.window(targetIntervalDays = 1, jitterPercent = 90)
        assertTrue(window.first >= ReminderScheduling.MIN_INTERVAL_DAYS)
        assertTrue(window.last >= window.first)
    }

    @Test
    fun `out-of-range inputs are clamped`() {
        // A negative target is clamped to the minimum; a huge jitter is clamped to the max.
        val window = ReminderScheduling.window(targetIntervalDays = -5, jitterPercent = 1_000)
        assertEquals(ReminderScheduling.MIN_INTERVAL_DAYS, window.first)
    }

    @Test
    fun `drawn intervals always fall inside the window and actually vary`() {
        val random = Random(seed = 42)
        val window = ReminderScheduling.window(targetIntervalDays = 14, jitterPercent = 20)
        val draws = (1..200).map { ReminderScheduling.nextIntervalDays(14, 20, random) }

        assertTrue(draws.all { it in window }, "every draw must sit inside $window")
        assertTrue(draws.distinct().size > 1, "the cadence must not be constant")
    }

    @Test
    fun `next fire adds the drawn whole-day interval to the base instant`() {
        val base = Instant.parse("2026-07-20T09:00:00Z")
        val random = Random(seed = 7)
        val expectedDays = ReminderScheduling.nextIntervalDays(14, 20, Random(seed = 7))

        val next = ReminderScheduling.nextFireAt(base, 14, 20, random)

        assertEquals(base.plus(Duration.ofDays(expectedDays.toLong())), next)
        assertTrue(next.isAfter(base))
    }

    // --- rescheduling after the completion history is edited -------------------------------------

    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-07-20T08:00:00Z")
    private val june1 = ReminderDoneEntry(LocalDate.of(2026, 6, 1), Instant.parse("2026-06-01T18:00:00Z"), id = "a")
    private val june10 = ReminderDoneEntry(LocalDate.of(2026, 6, 10), Instant.parse("2026-06-10T09:30:00Z"), id = "b")

    private fun reschedule(
        before: List<ReminderDoneEntry>,
        after: List<ReminderDoneEntry>,
    ): Instant? = ReminderScheduling.nextFireAfterHistoryChange(before, after, now, 7, 0, zone, Random(1))

    @Test
    fun `a day-only completion counts from local noon of its day`() {
        val dayOnly = ReminderDoneEntry(LocalDate.of(2026, 6, 5), at = null)

        assertEquals(Instant.parse("2026-06-05T12:00:00Z"), ReminderScheduling.anchorOf(dayOnly, zone))
        assertEquals(
            Instant.parse("2026-06-05T10:00:00Z"),
            ReminderScheduling.anchorOf(dayOnly, ZoneId.of("Europe/Rome")),
        )
        assertEquals(june10.at, ReminderScheduling.latestAnchor(listOf(june1, dayOnly, june10), zone))
        assertNull(ReminderScheduling.latestAnchor(emptyList(), zone))
    }

    @Test
    fun `adding a completion newer than the latest restarts the cadence from it`() {
        val next = reschedule(before = listOf(june1), after = listOf(june1, june10))

        assertEquals(june10.at!!.plus(Duration.ofDays(7)), next)
    }

    @Test
    fun `adding or deleting an entry older than the latest leaves the schedule alone`() {
        assertNull(reschedule(before = listOf(june10), after = listOf(june10, june1)))
        assertNull(reschedule(before = listOf(june1, june10), after = listOf(june10)))
    }

    @Test
    fun `deleting the latest completion falls back to the previous one`() {
        val next = reschedule(before = listOf(june1, june10), after = listOf(june1))

        assertEquals(june1.at!!.plus(Duration.ofDays(7)), next)
    }

    @Test
    fun `deleting the only completion draws the next occurrence from now`() {
        val next = reschedule(before = listOf(june10), after = emptyList())

        assertEquals(now.plus(Duration.ofDays(7)), next)
    }

    @Test
    fun `moving the latest completion earlier reschedules from its new moment`() {
        val moved = june10.copy(day = LocalDate.of(2026, 6, 8), at = Instant.parse("2026-06-08T07:00:00Z"))

        val next = reschedule(before = listOf(june1, june10), after = listOf(june1, moved))

        assertEquals(moved.at!!.plus(Duration.ofDays(7)), next)
    }

    @Test
    fun `an edit that leaves the latest moment unchanged does not redraw`() {
        assertNull(reschedule(before = listOf(june1, june10), after = listOf(june1.copy(day = LocalDate.of(2026, 5, 2)), june10)))
    }
}

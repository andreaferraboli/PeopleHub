package com.peoplehub.core.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
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
}

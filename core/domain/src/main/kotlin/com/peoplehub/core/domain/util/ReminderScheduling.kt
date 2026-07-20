package com.peoplehub.core.domain.util

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pure, deterministic maths for the deliberately-irregular reminder cadence.
 *
 * A reminder has a target interval (e.g. 14 days) and a jitter percentage. Each fire draws a fresh
 * whole-day interval uniformly from a window centred on the target and sized by the jitter, so the
 * gesture never arrives on a predictable rhythm. Randomness is passed in (never sourced internally)
 * so the logic stays testable — mirroring how the rest of the domain injects "now" as a
 * [java.time.Clock].
 */
object ReminderScheduling {
    /** The shortest cadence a reminder may have. */
    const val MIN_INTERVAL_DAYS: Int = 1

    /** The largest jitter half-width, as a percentage of the target interval. */
    const val MAX_JITTER_PERCENT: Int = 90

    /** A sensible default jitter half-width for new/preset reminders. */
    const val DEFAULT_JITTER_PERCENT: Int = 20

    /**
     * The inclusive `low..high` day window a [targetIntervalDays]/[jitterPercent] pair resolves to.
     * The bounds are clamped so the window never dips below [MIN_INTERVAL_DAYS] and `high >= low`.
     */
    fun window(targetIntervalDays: Int, jitterPercent: Int): IntRange {
        val target = targetIntervalDays.coerceAtLeast(MIN_INTERVAL_DAYS)
        val jitter = jitterPercent.coerceIn(0, MAX_JITTER_PERCENT)
        val delta = (target * jitter / PERCENT).roundToInt()
        val low = (target - delta).coerceAtLeast(MIN_INTERVAL_DAYS)
        val high = (target + delta).coerceAtLeast(low)
        return low..high
    }

    /** A random whole-day interval drawn uniformly from [window]. */
    fun nextIntervalDays(targetIntervalDays: Int, jitterPercent: Int, random: Random): Int {
        val range = window(targetIntervalDays, jitterPercent)
        return random.nextInt(range.first, range.last + 1)
    }

    /**
     * The next fire instant: [from] plus a freshly drawn jittered interval. [from] should be the
     * moment the reminder actually fired (or was created), not the previously scheduled time, so the
     * schedule never drifts and a device that was off does not produce a catch-up burst.
     */
    fun nextFireAt(from: Instant, targetIntervalDays: Int, jitterPercent: Int, random: Random): Instant =
        from.plus(Duration.ofDays(nextIntervalDays(targetIntervalDays, jitterPercent, random).toLong()))

    private const val PERCENT = 100.0
}

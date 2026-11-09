package com.peoplehub.core.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The time range an outing was given in the app's forms: when it started and when it ended, as times
 * of day on the outing's own day.
 *
 * An [end] at or before [start] means the outing ran past midnight and ended the next morning
 * (18:00 -> 07:30 is a night out, not a negative duration). Outings recorded without a range keep the
 * app's implicit noon and no end at all.
 */
data class OutingTimes(
    val start: LocalTime,
    val end: LocalTime,
) {
    /** Whether the range ends on the day after it starts. */
    val endsNextDay: Boolean get() = !end.isAfter(start)

    /** The start instant on [day] in [zone]. */
    fun startOn(day: LocalDate, zone: ZoneId): Instant = day.atTime(start).atZone(zone).toInstant()

    /** The end instant for an outing on [day] in [zone]: the next day when [endsNextDay]. */
    fun endOn(day: LocalDate, zone: ZoneId): Instant {
        val endDay = if (endsNextDay) day.plusDays(1) else day
        return endDay.atTime(end).atZone(zone).toInstant()
    }

    companion object {
        /** How long a freshly enabled range lasts before the user picks an end. */
        private val DEFAULT_LENGTH: Duration = Duration.ofHours(2)

        /** A new range starting at [start] with the default length. */
        fun startingAt(start: LocalTime): OutingTimes =
            OutingTimes(start.truncatedTo(ChronoUnit.MINUTES), start.plus(DEFAULT_LENGTH).truncatedTo(ChronoUnit.MINUTES))

        /**
         * The range stored on an outing or check-in that starts at [timestamp] and ends at [endAt], read
         * in [zone] to the minute; `null` when it has no end.
         */
        fun of(timestamp: Instant, endAt: Instant?, zone: ZoneId): OutingTimes? =
            endAt?.takeIf { it.isAfter(timestamp) }?.let {
                OutingTimes(
                    start = timestamp.atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                    end = it.atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                )
            }
    }
}

/** An end instant is only meaningful strictly after its start: anything else reads as "no end". */
fun Instant?.validEndAfter(start: Instant): Instant? = this?.takeIf { it.isAfter(start) }

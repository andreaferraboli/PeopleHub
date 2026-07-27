package com.peoplehub.core.domain.repository

import java.time.LocalDate

/**
 * Bookkeeping for the notification engine — not user-facing preferences.
 *
 * The daily birthday sweep is driven by three redundant triggers (the exact alarm, a periodic
 * WorkManager backstop, and an app-launch catch-up) so a single missed alarm cannot silently swallow
 * a birthday. [lastBirthdaySweepDate] is what keeps that redundancy from notifying twice: whichever
 * trigger runs first each day claims the sweep, the others no-op.
 *
 * [scheduledReminderHour] records the hour the recurring work was last enqueued for, so changing the
 * hour in Settings can re-enqueue instead of being swallowed by `ExistingPeriodicWorkPolicy.KEEP`.
 */
interface ReminderStateRepository {
    /** The day the birthday sweep last completed, or `null` if it has never run. */
    suspend fun lastBirthdaySweepDate(): LocalDate?

    /** Records [date] as the day the birthday sweep completed. */
    suspend fun setLastBirthdaySweepDate(date: LocalDate)

    /** The hour the recurring reminder work was last scheduled for, or `null` if never scheduled. */
    suspend fun scheduledReminderHour(): Int?

    /** Records [hour] as the hour the recurring reminder work is now scheduled for. */
    suspend fun setScheduledReminderHour(hour: Int)
}

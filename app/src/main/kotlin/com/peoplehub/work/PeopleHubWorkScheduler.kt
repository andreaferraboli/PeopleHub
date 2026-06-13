package com.peoplehub.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralises scheduling of all recurring background work:
 * - a daily check-in reminder sweep (around 09:00),
 * - a periodic widget refresh (every 6 hours),
 * - the daily exact birthday alarm (delegated to [BirthdayAlarmScheduler]).
 */
@Singleton
class PeopleHubWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val birthdayAlarmScheduler: BirthdayAlarmScheduler,
    private val clock: Clock,
) {

    fun scheduleRecurringWork() {
        val workManager = WorkManager.getInstance(context)

        val checkInRequest = PeriodicWorkRequestBuilder<CheckInReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayToHourMillis(DAILY_HOUR), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .build()
        workManager.enqueueUniquePeriodicWork(CHECK_IN_WORK, ExistingPeriodicWorkPolicy.KEEP, checkInRequest)

        val widgetRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(WIDGET_INTERVAL_HOURS, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(WIDGET_WORK, ExistingPeriodicWorkPolicy.KEEP, widgetRequest)

        birthdayAlarmScheduler.scheduleDailyCheck()
    }

    /** Enqueues a one-off widget refresh after a significant data change. */
    fun updateWidgetsNow() {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build())
    }

    private fun initialDelayToHourMillis(hour: Int): Long {
        val now = ZonedDateTime.now(clock)
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    private companion object {
        const val CHECK_IN_WORK = "check_in_daily"
        const val WIDGET_WORK = "widget_update_periodic"
        const val DAILY_HOUR = 9
        const val WIDGET_INTERVAL_HOURS = 6L
    }
}

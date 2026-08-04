package com.peoplehub.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.peoplehub.core.domain.repository.ReminderStateRepository
import com.peoplehub.core.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralises scheduling of all recurring background work at the user's configured reminder hour:
 * - a daily check-in reminder sweep,
 * - a daily relationship-reminder sweep,
 * - a daily birthday sweep (a WorkManager backstop for the exact alarm below),
 * - a periodic widget refresh (every 6 hours),
 * - the daily birthday alarm (delegated to [BirthdayAlarmScheduler]).
 *
 * Both notification sweeps (birthdays and per-person relationship reminders) have three triggers on
 * purpose. The alarm is punctual but fragile: each firing re-arms only the next one, so a single drop
 * (force-stop, an OEM battery manager, or the inexact fallback used when the Android 14+ exact-alarm
 * permission is denied) breaks the chain for good. The WorkManager job survives reboots and process
 * death and retries on its own, and [runCatchUpIfMissed] closes the last gap by sweeping on launch
 * when the day's run never happened. Each worker claims its day in [ReminderStateRepository] so the
 * three triggers can't double-notify.
 */
@Singleton
class PeopleHubWorkScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val birthdayAlarmScheduler: BirthdayAlarmScheduler,
        private val getSettings: GetSettingsUseCase,
        private val reminderState: ReminderStateRepository,
        private val clock: Clock,
    ) {
        /**
         * (Re)schedules every recurring job for the currently configured reminder hour and runs the
         * birthday catch-up. Safe to call on every launch: the periodic work is only re-enqueued when
         * the hour actually changed, so the daily jobs are not pushed out by frequent app starts.
         */
        suspend fun scheduleRecurringWork() {
            val settings = getSettings().first()
            val hour = settings.dailyReminderHour.coerceIn(0, MAX_HOUR)
            val workManager = WorkManager.getInstance(context)
            val hourChanged = reminderState.scheduledReminderHour() != hour
            val policy =
                if (hourChanged) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP

            workManager.enqueueUniquePeriodicWork(CHECK_IN_WORK, policy, dailyRequestAt<CheckInReminderWorker>(hour))
            workManager.enqueueUniquePeriodicWork(REMINDER_WORK, policy, dailyRequestAt<RelationshipReminderWorker>(hour))
            workManager.enqueueUniquePeriodicWork(BIRTHDAY_WORK, policy, dailyRequestAt<BirthdayReminderWorker>(hour))

            val widgetRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(WIDGET_INTERVAL_HOURS, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(WIDGET_WORK, ExistingPeriodicWorkPolicy.KEEP, widgetRequest)

            reminderState.setScheduledReminderHour(hour)
            birthdayAlarmScheduler.scheduleDailyCheck(hour, preferExact = settings.useExactAlarms)
            runCatchUpIfMissed(hour)
        }

        /**
         * Enqueues a one-off birthday sweep as unique work, so the alarm, the catch-up and a manual
         * run collapse into a single execution instead of racing each other's day-claim.
         */
        fun enqueueBirthdaySweep(force: Boolean = false) {
            val request =
                OneTimeWorkRequestBuilder<BirthdayReminderWorker>()
                    .setInputData(Data.Builder().putBoolean(BirthdayReminderWorker.INPUT_FORCE, force).build())
                    .build()
            enqueueSweep(BIRTHDAY_SWEEP_WORK, request, force)
        }

        /**
         * Enqueues a one-off relationship-reminder sweep as unique work, for the same reason as
         * [enqueueBirthdaySweep]: the alarm, the launch catch-up and the manual check must collapse
         * into a single execution.
         */
        fun enqueueRelationshipSweep(force: Boolean = false) {
            val request =
                OneTimeWorkRequestBuilder<RelationshipReminderWorker>()
                    .setInputData(Data.Builder().putBoolean(RelationshipReminderWorker.INPUT_FORCE, force).build())
                    .build()
            enqueueSweep(REMINDER_SWEEP_WORK, request, force)
        }

        /** Enqueues a one-off widget refresh after a significant data change. */
        fun updateWidgetsNow() {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build())
        }

        /**
         * Sweeps immediately when today's reminder hour has already passed but a sweep has not run —
         * i.e. the alarm and the backstop both missed it. Each worker also no-ops if its day was
         * already claimed, so this costs nothing on a normal launch.
         */
        private suspend fun runCatchUpIfMissed(hour: Int) {
            val now = ZonedDateTime.now(clock)
            if (now.hour < hour) return
            val today = LocalDate.now(clock)
            if (reminderState.lastBirthdaySweepDate() != today) enqueueBirthdaySweep()
            if (reminderState.lastRelationshipSweepDate() != today) enqueueRelationshipSweep()
        }

        private fun enqueueSweep(uniqueName: String, request: OneTimeWorkRequest, force: Boolean) {
            val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, policy, request)
        }

        private inline fun <reified W : ListenableWorker> dailyRequestAt(hour: Int): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<W>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayToHourMillis(hour), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
                .build()

        private fun initialDelayToHourMillis(hour: Int): Long {
            val now = ZonedDateTime.now(clock)
            var next =
                now
                    .withHour(hour)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMillis()
        }

        private companion object {
            const val CHECK_IN_WORK = "check_in_daily"
            const val REMINDER_WORK = "relationship_reminders_daily"
            const val BIRTHDAY_WORK = "birthday_reminders_daily"
            const val BIRTHDAY_SWEEP_WORK = "birthday_sweep_once"
            const val REMINDER_SWEEP_WORK = "relationship_sweep_once"
            const val WIDGET_WORK = "widget_update_periodic"
            const val WIDGET_INTERVAL_HOURS = 6L
            const val MAX_HOUR = 23
        }
    }

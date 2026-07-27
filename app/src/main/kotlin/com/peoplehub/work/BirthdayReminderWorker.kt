package com.peoplehub.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.domain.repository.ReminderStateRepository
import com.peoplehub.core.domain.usecase.GetDueBirthdayRemindersUseCase
import com.peoplehub.core.notifications.PeopleHubNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate

/**
 * Evaluates today's birthday reminders: a "happy birthday" notification for birthdays that land
 * today, and an advance reminder for any birthday whose distance matches an enabled reminder offset.
 * The same-day greeting only fires when [ReminderOffset.SAME_DAY] is enabled globally.
 *
 * Birthday notifications are deliberately **not** gated on the per-person notification opt-in — that
 * toggle governs check-in reminders only. Gating birthdays on it meant every profile added or
 * imported without deliberately flipping the switch (it defaults to off) silently produced no
 * birthday notification at all.
 *
 * Three redundant triggers enqueue this worker — the daily exact alarm, a daily WorkManager
 * backstop, and an app-launch catch-up — so one missed alarm cannot swallow a birthday. The sweep
 * therefore claims the day in [ReminderStateRepository] and later triggers no-op, unless the request
 * carries [INPUT_FORCE] (the manual "check now" in Settings).
 */
@HiltWorker
class BirthdayReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val getDueBirthdayReminders: GetDueBirthdayRemindersUseCase,
        private val reminderState: ReminderStateRepository,
        private val notifier: PeopleHubNotifier,
        private val clock: Clock,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val today = LocalDate.now(clock)
            val forced = inputData.getBoolean(INPUT_FORCE, false)
            if (!forced && reminderState.lastBirthdaySweepDate() == today) return Result.success()

            getDueBirthdayReminders().forEach { due ->
                if (due.isToday) {
                    notifier.showBirthdayToday(due.personId, due.fullName)
                } else {
                    notifier.showBirthdayUpcoming(due.personId, due.fullName, due.daysUntil)
                }
            }
            reminderState.setLastBirthdaySweepDate(today)
            return Result.success()
        }

        companion object {
            /** Input flag that bypasses the once-a-day guard, used by the manual check in Settings. */
            const val INPUT_FORCE: String = "force"
        }
    }

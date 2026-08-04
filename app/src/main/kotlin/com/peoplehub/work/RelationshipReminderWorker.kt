package com.peoplehub.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peoplehub.core.domain.repository.ReminderStateRepository
import com.peoplehub.core.domain.usecase.GetDueRemindersUseCase
import com.peoplehub.core.domain.usecase.MarkReminderFiredUseCase
import com.peoplehub.core.notifications.PeopleHubNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate

/**
 * Daily sweep that fires the per-person relationship reminders that have come due. Scheduled by
 * [PeopleHubWorkScheduler] at the user's configured reminder hour.
 *
 * Each due reminder is notified once and then immediately rescheduled from now with fresh jitter, so
 * a device that was off for several days fires each overdue reminder a single time (no catch-up
 * burst) and no reminder repeats the next morning.
 *
 * Like the birthday sweep, three redundant triggers enqueue this worker — the daily alarm, a daily
 * WorkManager backstop, and an app-launch catch-up — because a lone periodic job is silently dropped
 * by Doze and OEM battery managers. The sweep therefore claims the day in [ReminderStateRepository]
 * and later triggers no-op, unless the request carries [INPUT_FORCE] (the manual check in Settings).
 */
@HiltWorker
class RelationshipReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val getDueReminders: GetDueRemindersUseCase,
        private val markReminderFired: MarkReminderFiredUseCase,
        private val reminderState: ReminderStateRepository,
        private val notifier: PeopleHubNotifier,
        private val clock: Clock,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val today = LocalDate.now(clock)
            val forced = inputData.getBoolean(INPUT_FORCE, false)
            if (!forced && reminderState.lastRelationshipSweepDate() == today) return Result.success()

            getDueReminders().forEach { due ->
                notifier.showRelationshipReminder(
                    reminderId = due.reminderId,
                    personId = due.personId,
                    name = due.personName,
                    title = due.title,
                    note = due.note,
                )
                markReminderFired(due.reminderId)
            }
            reminderState.setLastRelationshipSweepDate(today)
            return Result.success()
        }

        companion object {
            /** Input flag that bypasses the once-a-day guard, used by the manual check in Settings. */
            const val INPUT_FORCE: String = "force"
        }
    }

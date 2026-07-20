package com.peoplehub.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peoplehub.core.domain.usecase.GetDueRemindersUseCase
import com.peoplehub.core.domain.usecase.MarkReminderFiredUseCase
import com.peoplehub.core.notifications.PeopleHubNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily sweep that fires the per-person relationship reminders that have come due. Scheduled by
 * [PeopleHubWorkScheduler] to run around 09:00.
 *
 * Each due reminder is notified once and then immediately rescheduled from now with fresh jitter, so
 * a device that was off for several days fires each overdue reminder a single time (no catch-up
 * burst) and no reminder repeats the next morning.
 */
@HiltWorker
class RelationshipReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val getDueReminders: GetDueRemindersUseCase,
        private val markReminderFired: MarkReminderFiredUseCase,
        private val notifier: PeopleHubNotifier,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
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
            return Result.success()
        }
    }

package com.peoplehub.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.usecase.GetUrgentCheckInsUseCase
import com.peoplehub.core.notifications.PeopleHubNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Daily sweep that notifies the user about people they have not seen beyond their critical
 * threshold. Scheduled by [PeopleHubWorkScheduler] to run around 09:00.
 */
@HiltWorker
class CheckInReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val getUrgentCheckIns: GetUrgentCheckInsUseCase,
        private val notifier: PeopleHubNotifier,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            getUrgentCheckIns()
                .first()
                .filter { it.person.notificationsEnabled }
                .filter { it.status == CheckInStatus.OVERDUE || it.status == CheckInStatus.NEVER }
                .forEach { urgency ->
                    notifier.showCheckInReminder(
                        personId = urgency.person.id,
                        name = urgency.person.firstName,
                        days = urgency.daysSince?.toInt(),
                        lastSeenText = urgency.person.lastCheckInAt?.let(::formatLastSeen),
                    )
                }
            return Result.success()
        }

        private fun formatLastSeen(instant: Instant): String =
            instant.atZone(ZoneId.systemDefault()).format(LastSeenFormatter)

        private companion object {
            val LastSeenFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        }
    }

package com.peoplehub.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.repository.AuthRepository
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
 * threshold. Scheduled by [PeopleHubWorkScheduler] at the configured reminder hour. Reads the cloud:
 * signed out it does nothing, and without an answer from the server in time it retries.
 */
@HiltWorker
class CheckInReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val getUrgentCheckIns: GetUrgentCheckInsUseCase,
        private val notifier: PeopleHubNotifier,
        private val auth: AuthRepository,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            if (auth.currentAccount() == null) return Result.success()
            val urgent = readCloudOrNull { getUrgentCheckIns().first() } ?: return Result.retry()
            urgent
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

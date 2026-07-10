package com.peoplehub.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.domain.usecase.GetAllBirthdaysUseCase
import com.peoplehub.core.domain.usecase.GetSettingsUseCase
import com.peoplehub.core.notifications.PeopleHubNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Evaluates today's birthday reminders: a "happy birthday" notification for birthdays that land
 * today, and an advance reminder for any birthday whose distance matches an enabled reminder offset.
 * Both are opt-in — the same-day greeting only fires when [ReminderOffset.SAME_DAY] is enabled.
 * Triggered by the daily birthday alarm.
 */
@HiltWorker
class BirthdayReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val getAllBirthdays: GetAllBirthdaysUseCase,
        private val getSettings: GetSettingsUseCase,
        private val notifier: PeopleHubNotifier,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val enabledOffsets =
                getSettings()
                    .first()
                    .birthdayReminderOffsets
                    .map { it.daysBefore }
                    .toSet()
            getAllBirthdays()
                .first()
                .filter { it.notificationsEnabled && it.daysUntil in enabledOffsets }
                .forEach { birthday ->
                    if (birthday.daysUntil == ReminderOffset.SAME_DAY.daysBefore) {
                        notifier.showBirthdayToday(birthday.personId, birthday.fullName)
                    } else {
                        notifier.showBirthdayUpcoming(birthday.personId, birthday.fullName, birthday.daysUntil)
                    }
                }
            return Result.success()
        }
    }

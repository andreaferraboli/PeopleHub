package com.peoplehub.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.peoplehub.receiver.BirthdayAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily birthday check using an exact alarm so reminders fire reliably even in Doze.
 *
 * On Android 12+ it honours both the user's "use exact alarms" preference and the system
 * exact-alarm permission: when either says no it falls back to an inexact `setAndAllowWhileIdle`
 * alarm. Since Android 14 that permission is **not** granted by default to an app targeting SDK 33+,
 * so the fallback is the common case and an inexact alarm can slip by hours — which is why
 * [PeopleHubWorkScheduler] also runs a WorkManager backstop and an app-launch catch-up.
 *
 * The alarm is re-armed for the next day by [BirthdayAlarmReceiver] and after device boot.
 */
@Singleton
class BirthdayAlarmScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val clock: Clock,
    ) {
        /** Arms the next daily check at [hour], exact when [preferExact] and the system allows it. */
        fun scheduleDailyCheck(hour: Int, preferExact: Boolean) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAtMillis = nextTriggerMillis(hour)
            val pendingIntent = buildPendingIntent()

            val exact = preferExact && canScheduleExactAlarms()
            val scheduled =
                exact &&
                    runCatching {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }.isSuccess
            // The permission can be revoked between the check and the call, so fall back on failure.
            if (!scheduled) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        /** Whether the system currently lets this app post exact alarms. Always true below API 31. */
        fun canScheduleExactAlarms(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
            return alarmManager.canScheduleExactAlarms()
        }

        private fun buildPendingIntent(): PendingIntent {
            val intent =
                Intent(context, BirthdayAlarmReceiver::class.java).apply {
                    action = BirthdayAlarmReceiver.ACTION_BIRTHDAY_CHECK
                }
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, FLAGS)
        }

        private fun nextTriggerMillis(hour: Int): Long {
            val now = ZonedDateTime.now(clock)
            var next =
                now
                    .withHour(hour.coerceIn(0, MAX_HOUR))
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return next.toInstant().toEpochMilli()
        }

        private companion object {
            const val REQUEST_CODE = 7001
            const val MAX_HOUR = 23
            val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

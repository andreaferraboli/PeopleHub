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
 * On Android 12+ it honours the user's exact-alarm permission: when [AlarmManager.canScheduleExactAlarms]
 * is denied it gracefully falls back to an inexact `setAndAllowWhileIdle` alarm. The alarm is
 * re-armed for the next day by [BirthdayAlarmReceiver] and after device boot.
 */
@Singleton
class BirthdayAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {

    fun scheduleDailyCheck() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = nextTriggerMillis(DAILY_HOUR)
        val pendingIntent = buildPendingIntent()

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, BirthdayAlarmReceiver::class.java).apply {
            action = BirthdayAlarmReceiver.ACTION_BIRTHDAY_CHECK
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, FLAGS)
    }

    private fun nextTriggerMillis(hour: Int): Long {
        val now = ZonedDateTime.now(clock)
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    private companion object {
        const val REQUEST_CODE = 7001
        const val DAILY_HOUR = 9
        val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

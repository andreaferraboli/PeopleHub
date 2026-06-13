package com.peoplehub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.peoplehub.di.ReceiverEntryPoint
import com.peoplehub.work.BirthdayReminderWorker
import dagger.hilt.android.EntryPointAccessors

/**
 * Fired by the daily exact alarm: enqueues a one-off [BirthdayReminderWorker] to evaluate today's
 * reminders, then re-arms the alarm for the following day.
 */
class BirthdayAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BIRTHDAY_CHECK) return
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BirthdayReminderWorker>().build())
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
        entryPoint.birthdayAlarmScheduler().scheduleDailyCheck()
    }

    companion object {
        const val ACTION_BIRTHDAY_CHECK = "com.peoplehub.action.BIRTHDAY_CHECK"
    }
}

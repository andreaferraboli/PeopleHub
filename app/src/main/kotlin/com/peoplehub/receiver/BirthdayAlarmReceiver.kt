package com.peoplehub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.peoplehub.di.ReceiverEntryPoint
import com.peoplehub.work.BirthdayReminderWorker
import com.peoplehub.work.RelationshipReminderWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by the daily alarm at the configured reminder hour: enqueues a [BirthdayReminderWorker] and a
 * [RelationshipReminderWorker] to evaluate today's notifications, then re-arms every recurring trigger
 * for the following day. Re-scheduling reads the configured reminder hour from DataStore, so the
 * broadcast is held open with `goAsync()`.
 */
class BirthdayAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BIRTHDAY_CHECK) return
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
        val scheduler = entryPoint.workScheduler()
        scheduler.enqueueBirthdaySweep()
        scheduler.enqueueRelationshipSweep()
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                scheduler.scheduleRecurringWork()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_BIRTHDAY_CHECK = "com.peoplehub.action.BIRTHDAY_CHECK"
    }
}

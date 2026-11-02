package com.peoplehub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.peoplehub.core.notifications.NotificationActions
import com.peoplehub.di.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles the "Done" action on a relationship reminder: logs the tap in the reminder's completion history,
 * restarts its cadence from now with a freshly drawn interval, and dismisses the notification, without
 * opening the app. Same effect as the "done" button on the reminder cards, deliberately routed through
 * the same use case so the history is complete however the user ticked it off.
 */
class ReminderDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationActions.ACTION_REMINDER_DONE) return

        val reminderId = intent.getStringExtra(NotificationActions.EXTRA_REMINDER_ID)
        val notificationId = intent.getIntExtra(NotificationActions.EXTRA_NOTIFICATION_ID, -1)
        if (reminderId.isNullOrEmpty()) return

        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val done = withTimeoutOrNull(CLOUD_TIMEOUT_MILLIS) { entryPoint.markReminderDone().invoke(reminderId) }
                if (done?.isSuccess != true) return@launch
                if (notificationId > 0) entryPoint.notifier().cancel(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        /** A broadcast receiver has about ten seconds; the cloud write must fit in it. */
        const val CLOUD_TIMEOUT_MILLIS = 9_000L
    }
}

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

/**
 * Handles the "Mark as seen" notification action: records a check-in for the person without opening
 * the app, dismisses the notification, and refreshes the widgets.
 */
class MarkSeenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationActions.ACTION_MARK_SEEN) return

        val personId = intent.getLongExtra(NotificationActions.EXTRA_PERSON_ID, INVALID_ID)
        val notificationId = intent.getIntExtra(NotificationActions.EXTRA_NOTIFICATION_ID, -1)
        if (personId <= 0L) return

        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                entryPoint.checkInPerson().invoke(personId)
                if (notificationId > 0) entryPoint.notifier().cancel(notificationId)
                entryPoint.workScheduler().updateWidgetsNow()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val INVALID_ID = -1L
    }
}

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
 * Handles the "Mark as seen" notification action: records a check-in for the person without opening
 * the app, dismisses the notification, and refreshes the widgets.
 */
class MarkSeenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationActions.ACTION_MARK_SEEN) return

        val personId = intent.getStringExtra(NotificationActions.EXTRA_PERSON_ID)
        val notificationId = intent.getIntExtra(NotificationActions.EXTRA_NOTIFICATION_ID, -1)
        if (personId.isNullOrEmpty()) return

        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Signed out or offline: the check-in is not recorded and the notification stays.
                val recorded =
                    runCatching {
                        withTimeoutOrNull(CLOUD_TIMEOUT_MILLIS) { entryPoint.checkInPerson().invoke(personId) }
                    }.getOrNull()
                if (recorded == null) return@launch
                if (notificationId > 0) entryPoint.notifier().cancel(notificationId)
                entryPoint.workScheduler().updateWidgetsNow()
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

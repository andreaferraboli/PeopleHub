package com.peoplehub.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.peoplehub.core.domain.navigation.DeepLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts the app's notifications through [NotificationCompat], on dedicated channels.
 *
 * Content intents use package-scoped `ACTION_VIEW` deep links so this module never has to reference
 * the app's `Activity`, and the "mark as seen" action fires a package-scoped broadcast handled by
 * the app's receiver.
 */
@Singleton
class PeopleHubNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates the notification channels. Safe to call repeatedly (idempotent). */
    fun ensureChannels() {
        val checkIn = NotificationChannel(
            NotificationChannels.CHECK_IN,
            context.getString(R.string.channel_checkin_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_checkin_desc) }

        val birthday = NotificationChannel(
            NotificationChannels.BIRTHDAY,
            context.getString(R.string.channel_birthday_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_birthday_desc) }

        manager.createNotificationChannels(listOf(checkIn, birthday))
    }

    /** "You haven't seen [name] in [days] days", deep-linking to their profile. */
    fun showCheckInReminder(personId: Long, name: String, days: Int, lastSeenText: String?) {
        val id = NotificationIds.checkIn(personId)
        val builder = baseBuilder(NotificationChannels.CHECK_IN)
            .setContentTitle(context.getString(R.string.notif_checkin_title, name, days))
            .setContentIntent(personPendingIntent(personId, id))
        if (lastSeenText != null) {
            builder.setContentText(context.getString(R.string.notif_checkin_body, lastSeenText))
        }
        post(id, builder)
    }

    /** "Today is [name]'s birthday! 🎂" with a "Mark as seen" action that checks them in directly. */
    fun showBirthdayToday(personId: Long, name: String) {
        val id = NotificationIds.birthday(personId)
        val builder = baseBuilder(NotificationChannels.BIRTHDAY)
            .setContentTitle(context.getString(R.string.notif_birthday_today_title, name))
            .setContentIntent(personPendingIntent(personId, id))
            .addAction(
                0,
                context.getString(R.string.notif_action_mark_seen),
                markSeenPendingIntent(personId, id),
            )
        post(id, builder)
    }

    /** "[name]'s birthday is in [daysUntil] days." */
    fun showBirthdayUpcoming(personId: Long, name: String, daysUntil: Int) {
        val id = NotificationIds.birthday(personId)
        val builder = baseBuilder(NotificationChannels.BIRTHDAY)
            .setContentTitle(context.getString(R.string.notif_birthday_upcoming_title, name, daysUntil))
            .setContentIntent(personPendingIntent(personId, id))
        post(id, builder)
    }

    /** Cancels a previously posted notification. */
    fun cancel(notificationId: Int) = manager.cancel(notificationId)

    private fun baseBuilder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_people)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    private fun personPendingIntent(personId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.person(personId))).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_FLAGS)
    }

    private fun markSeenPendingIntent(personId: Long, notificationId: Int): PendingIntent {
        val intent = Intent(NotificationActions.ACTION_MARK_SEEN).apply {
            `package` = context.packageName
            putExtra(NotificationActions.EXTRA_PERSON_ID, personId)
            putExtra(NotificationActions.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(context, notificationId + ACTION_REQUEST_OFFSET, intent, PENDING_FLAGS)
    }

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, builder.build()) }
    }

    private companion object {
        val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        const val ACTION_REQUEST_OFFSET = 500_000
    }
}

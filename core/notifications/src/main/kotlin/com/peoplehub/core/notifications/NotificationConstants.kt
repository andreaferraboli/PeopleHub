package com.peoplehub.core.notifications

/** Identifiers for the app's notification channels. */
object NotificationChannels {
    const val CHECK_IN: String = "check_in_reminders"
    const val BIRTHDAY: String = "birthday_reminders"
}

/**
 * Broadcast action + extras for the "mark as seen" notification action button, shared between the
 * notifier (which builds the [android.app.PendingIntent]) and the receiver (which handles it).
 */
object NotificationActions {
    const val ACTION_MARK_SEEN: String = "com.peoplehub.action.MARK_SEEN"
    const val EXTRA_PERSON_ID: String = "com.peoplehub.extra.PERSON_ID"
    const val EXTRA_NOTIFICATION_ID: String = "com.peoplehub.extra.NOTIFICATION_ID"
}

/** Stable notification-id ranges so reminders update in place instead of stacking endlessly. */
internal object NotificationIds {
    private const val CHECK_IN_BASE = 100_000
    private const val BIRTHDAY_BASE = 200_000
    private const val ID_MODULO = 90_000

    fun checkIn(personId: Long): Int = CHECK_IN_BASE + (personId % ID_MODULO).toInt()

    fun birthday(personId: Long): Int = BIRTHDAY_BASE + (personId % ID_MODULO).toInt()
}

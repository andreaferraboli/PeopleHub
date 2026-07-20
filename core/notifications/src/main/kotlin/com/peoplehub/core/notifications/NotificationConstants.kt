package com.peoplehub.core.notifications

/** Identifiers for the app's notification channels. */
object NotificationChannels {
    const val CHECK_IN: String = "check_in_reminders"
    const val BIRTHDAY: String = "birthday_reminders"
    const val RELATIONSHIP: String = "relationship_reminders"
}

/**
 * Broadcast action + extras for the "mark as seen" notification action button, shared between the
 * notifier (which builds the [android.app.PendingIntent]) and the receiver (which handles it).
 */
object NotificationActions {
    const val ACTION_MARK_SEEN: String = "com.peoplehub.action.MARK_SEEN"
    const val EXTRA_PERSON_ID: String = "com.peoplehub.extra.PERSON_ID"
    const val EXTRA_NOTIFICATION_ID: String = "com.peoplehub.extra.NOTIFICATION_ID"

    /** "Done" action on a relationship reminder: reschedules it from now and dismisses it. */
    const val ACTION_REMINDER_DONE: String = "com.peoplehub.action.REMINDER_DONE"
    const val EXTRA_REMINDER_ID: String = "com.peoplehub.extra.REMINDER_ID"
}

/** Stable notification-id ranges so reminders update in place instead of stacking endlessly. */
internal object NotificationIds {
    private const val CHECK_IN_BASE = 100_000
    private const val BIRTHDAY_BASE = 200_000
    private const val REMINDER_BASE = 300_000
    private const val ID_MODULO = 90_000

    fun checkIn(personId: Long): Int = CHECK_IN_BASE + (personId % ID_MODULO).toInt()

    fun birthday(personId: Long): Int = BIRTHDAY_BASE + (personId % ID_MODULO).toInt()

    fun reminder(reminderId: Long): Int = REMINDER_BASE + (reminderId % ID_MODULO).toInt()
}

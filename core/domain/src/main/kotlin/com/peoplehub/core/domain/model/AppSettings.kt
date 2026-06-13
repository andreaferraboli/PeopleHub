package com.peoplehub.core.domain.model

/**
 * User-configurable application settings, persisted via DataStore.
 *
 * @property defaultCheckInThreshold global fallback thresholds for people without an override.
 * @property birthdayReminderOffsets globally enabled birthday reminder offsets.
 * @property useExactAlarms whether to request exact alarms for imminent reminders (falls back to
 * inexact scheduling when the permission is denied).
 * @property dailyReminderHour hour of day (0–23) the daily check-in sweep runs.
 */
data class AppSettings(
    val defaultCheckInThreshold: CheckInThreshold = CheckInThreshold.Default,
    val birthdayReminderOffsets: Set<ReminderOffset> = setOf(ReminderOffset.ONE_DAY, ReminderOffset.ONE_WEEK),
    val useExactAlarms: Boolean = true,
    val dailyReminderHour: Int = DEFAULT_REMINDER_HOUR,
) {
    companion object {
        const val DEFAULT_REMINDER_HOUR: Int = 9
    }
}

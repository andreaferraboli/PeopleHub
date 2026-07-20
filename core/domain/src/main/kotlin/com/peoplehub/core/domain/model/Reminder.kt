package com.peoplehub.core.domain.model

import java.time.Instant

/**
 * A recurring "do something for this person" reminder that fires on a deliberately *irregular*
 * cadence rather than a fixed interval.
 *
 * The user picks a [targetIntervalDays] (e.g. 14 for "about every two weeks"); each time the
 * reminder fires, the next occurrence is drawn at random from a window around that target, sized by
 * [jitterPercent]. So "every two weeks" might actually arrive after 12, 15 or 17 days, never the
 * same rhythm twice. The rationale — a gesture that arrives like clockwork reads as automatic and
 * loses its affective signal — is explained on the in-app science screen.
 *
 * @property id stable database identifier; `0` denotes a not-yet-persisted reminder.
 * @property personId the person this reminder belongs to (reminders are always per-person).
 * @property targetIntervalDays the nominal cadence in days the jitter window is centred on.
 * @property jitterPercent half-width of the jitter window as a percentage of the target
 * (e.g. `20` with a 14-day target → a 11..17-day window). `0` means a fixed interval.
 * @property lastFiredAt when the reminder last notified, or `null` if it never has.
 * @property nextFireAt the next instant the reminder is due; recomputed (with fresh jitter) every
 * time it fires so the cadence never settles into a pattern.
 * @property presetKey the [com.peoplehub.core.domain.reminder.ReminderPreset] this was created from,
 * or `null` for a fully custom reminder.
 */
data class Reminder(
    val id: Long = 0L,
    val personId: Long,
    val title: String,
    val note: String? = null,
    val category: ReminderCategory = ReminderCategory.CUSTOM,
    val targetIntervalDays: Int,
    val jitterPercent: Int,
    val enabled: Boolean = true,
    val lastFiredAt: Instant? = null,
    val nextFireAt: Instant = Instant.EPOCH,
    val createdAt: Instant = Instant.EPOCH,
    val presetKey: String? = null,
)

/**
 * The kind of relationship a reminder serves, used to group the built-in presets and colour the
 * category chip. [CUSTOM] is the fallback for reminders the user writes from scratch.
 */
enum class ReminderCategory {
    LOVE,
    FRIENDSHIP,
    FAMILY,
    CUSTOM,
}

/**
 * A reminder that has come due, denormalised with the owning person's display name so the reminder
 * worker can post a notification without a second lookup.
 */
data class DueReminder(
    val reminderId: Long,
    val personId: Long,
    val personName: String,
    val title: String,
    val note: String?,
)

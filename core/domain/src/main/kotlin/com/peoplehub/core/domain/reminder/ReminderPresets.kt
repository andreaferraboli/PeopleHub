package com.peoplehub.core.domain.reminder

import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.util.ReminderScheduling

/**
 * A built-in reminder template the user can start from, grounded in the relationship-maintenance
 * literature (Gottman, Aron's self-expansion model, Gable's capitalization, Hall/Dunbar).
 *
 * Only the machine-readable knobs live here so the domain stays free of Android resources: the
 * localised name and description are resolved in the feature layer from [key]. Every preset carries
 * a periodic cadence, since a one-off or purely situational gesture makes no sense as a recurring
 * reminder.
 */
data class ReminderPreset(
    val key: String,
    val category: ReminderCategory,
    val targetIntervalDays: Int,
    val jitterPercent: Int = ReminderScheduling.DEFAULT_JITTER_PERCENT,
)

/** The curated set of periodic relationship-maintenance presets, grouped by relationship type. */
object ReminderPresets {
    val all: List<ReminderPreset> =
        listOf(
            // Love — Gottman's rituals + Aron's self-expansion.
            ReminderPreset("love_new_activity", ReminderCategory.LOVE, targetIntervalDays = 30, jitterPercent = 30),
            ReminderPreset("love_state_of_union", ReminderCategory.LOVE, targetIntervalDays = 7),
            ReminderPreset("love_love_maps", ReminderCategory.LOVE, targetIntervalDays = 7),
            // Friendship — Hall's hours-to-friendship + the Ben Franklin effect.
            ReminderPreset("friend_face_time", ReminderCategory.FRIENDSHIP, targetIntervalDays = 14),
            ReminderPreset("friend_ask_advice", ReminderCategory.FRIENDSHIP, targetIntervalDays = 30, jitterPercent = 30),
            // Family — Transactional Analysis parity + functional solidarity.
            ReminderPreset("family_parents_call", ReminderCategory.FAMILY, targetIntervalDays = 7),
            ReminderPreset("family_siblings", ReminderCategory.FAMILY, targetIntervalDays = 21),
            ReminderPreset("family_grandparents", ReminderCategory.FAMILY, targetIntervalDays = 30),
        )

    /** The preset with the given [key], or `null` if none matches. */
    fun byKey(key: String): ReminderPreset? = all.firstOrNull { it.key == key }
}

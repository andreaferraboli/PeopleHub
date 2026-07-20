package com.peoplehub.feature.reminders

import androidx.annotation.StringRes
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.reminder.ReminderPreset

/** The localised label for a relationship category chip. */
@StringRes
fun ReminderCategory.labelRes(): Int =
    when (this) {
        ReminderCategory.LOVE -> R.string.reminder_category_love
        ReminderCategory.FRIENDSHIP -> R.string.reminder_category_friendship
        ReminderCategory.FAMILY -> R.string.reminder_category_family
        ReminderCategory.CUSTOM -> R.string.reminder_category_custom
    }

/** The localised name of a built-in preset, resolved from its stable key. */
@StringRes
fun ReminderPreset.nameRes(): Int =
    when (key) {
        "love_new_activity" -> R.string.preset_love_new_activity_name
        "love_state_of_union" -> R.string.preset_love_state_of_union_name
        "love_love_maps" -> R.string.preset_love_love_maps_name
        "friend_face_time" -> R.string.preset_friend_face_time_name
        "friend_ask_advice" -> R.string.preset_friend_ask_advice_name
        "family_parents_call" -> R.string.preset_family_parents_call_name
        "family_siblings" -> R.string.preset_family_siblings_name
        "family_grandparents" -> R.string.preset_family_grandparents_name
        else -> R.string.reminder_category_custom
    }

/** The localised one-line description of a built-in preset, resolved from its stable key. */
@StringRes
fun ReminderPreset.descriptionRes(): Int =
    when (key) {
        "love_new_activity" -> R.string.preset_love_new_activity_desc
        "love_state_of_union" -> R.string.preset_love_state_of_union_desc
        "love_love_maps" -> R.string.preset_love_love_maps_desc
        "friend_face_time" -> R.string.preset_friend_face_time_desc
        "friend_ask_advice" -> R.string.preset_friend_ask_advice_desc
        "family_parents_call" -> R.string.preset_family_parents_call_desc
        "family_siblings" -> R.string.preset_family_siblings_desc
        "family_grandparents" -> R.string.preset_family_grandparents_desc
        else -> R.string.reminder_category_custom
    }

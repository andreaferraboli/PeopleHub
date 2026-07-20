package com.peoplehub.feature.reminders.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.peoplehub.feature.reminders.edit.AddEditReminderScreen
import com.peoplehub.feature.reminders.list.RemindersListScreen
import com.peoplehub.feature.reminders.science.ReminderScienceScreen
import kotlinx.serialization.Serializable

/** Type-safe navigation routes for the reminders feature. */
@Serializable
data object RemindersRoute

/**
 * Add/edit route. [reminderId] is [NEW_REMINDER] when creating; [personId] is [NO_PERSON] when the
 * person is not fixed (the global list, where it is picked in the form) and a real id when adding
 * from a person's detail screen.
 */
@Serializable
data class AddEditReminderRoute(
    val reminderId: Long = NEW_REMINDER,
    val personId: Long = NO_PERSON,
) {
    companion object {
        const val NEW_REMINDER: Long = -1L
        const val NO_PERSON: Long = -1L
    }
}

/** The "why irregular cadence" explainer screen. */
@Serializable
data object ReminderScienceRoute

/**
 * Registers the reminders screens into the host graph. Navigation out of these screens is delegated
 * to the supplied callbacks so the feature stays decoupled from the rest of the app.
 */
fun NavGraphBuilder.remindersSection(
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onOpenScience: () -> Unit,
    onPersonClick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    composable<RemindersRoute> {
        RemindersListScreen(
            onAddReminder = onAddReminder,
            onEditReminder = onEditReminder,
            onOpenScience = onOpenScience,
            onPersonClick = onPersonClick,
        )
    }
    composable<AddEditReminderRoute> {
        AddEditReminderScreen(onBack = onBack, onOpenScience = onOpenScience)
    }
    composable<ReminderScienceRoute> {
        ReminderScienceScreen(onBack = onBack)
    }
}

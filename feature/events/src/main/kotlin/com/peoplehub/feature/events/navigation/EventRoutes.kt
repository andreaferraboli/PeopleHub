package com.peoplehub.feature.events.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.peoplehub.core.domain.navigation.DeepLinks
import com.peoplehub.feature.events.detail.EventDetailScreen
import com.peoplehub.feature.events.edit.AddEditEventScreen
import com.peoplehub.feature.events.list.EventsListScreen
import kotlinx.serialization.Serializable

/** Type-safe navigation routes for the events feature. */
@Serializable
data object EventsRoute

/** Event detail route. [eventId] is read back via `SavedStateHandle.toRoute()` in the ViewModel. */
@Serializable
data class EventDetailRoute(val eventId: String)

/** Add/edit route; [eventId] is [NEW_EVENT] when creating a new event. */
@Serializable
data class AddEditEventRoute(val eventId: String = NEW_EVENT) {
    companion object {
        const val NEW_EVENT: String = "new"
    }
}

/**
 * Registers the events screens into the host graph. Navigation out of these screens is delegated to
 * the supplied callbacks so the feature stays decoupled from the rest of the app.
 */
fun NavGraphBuilder.eventsSection(
    onEventClick: (String) -> Unit,
    onAddEvent: () -> Unit,
    onEditEvent: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    composable<EventsRoute> {
        EventsListScreen(
            onEventClick = onEventClick,
            onAddEvent = onAddEvent,
        )
    }
    composable<EventDetailRoute>(
        deepLinks =
            listOf(
                navDeepLink { uriPattern = "${DeepLinks.SCHEME}://${DeepLinks.HOST_EVENT}/{eventId}" },
            ),
    ) {
        EventDetailScreen(
            onBack = onBack,
            onEdit = onEditEvent,
            onPersonClick = onPersonClick,
        )
    }
    composable<AddEditEventRoute> {
        AddEditEventScreen(onBack = onBack)
    }
}

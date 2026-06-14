package com.peoplehub.feature.people.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.peoplehub.core.domain.navigation.DeepLinks
import com.peoplehub.feature.people.birthdayonly.BirthdayOnlyScreen
import com.peoplehub.feature.people.detail.PersonDetailScreen
import com.peoplehub.feature.people.edit.AddEditPersonScreen
import com.peoplehub.feature.people.list.PeopleListScreen
import kotlinx.serialization.Serializable

/** Type-safe navigation routes for the people feature. */
@Serializable
data object PeopleListRoute

/** Person detail route. [personId] is read back via `SavedStateHandle.toRoute()` in the ViewModel. */
@Serializable
data class PersonDetailRoute(val personId: Long)

/** Add/edit route; [personId] is [NEW_PERSON] when creating a new person. */
@Serializable
data class AddEditPersonRoute(val personId: Long = NEW_PERSON) {
    companion object {
        const val NEW_PERSON: Long = -1L
    }
}

/** Birthday-only management route (reached from the Vault). */
@Serializable
data object BirthdayOnlyRoute

/**
 * Registers the people screens into the host graph. Navigation out of these screens is delegated to
 * the supplied callbacks so the feature stays decoupled from the rest of the app.
 */
fun NavGraphBuilder.peopleSection(
    onPersonClick: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onEditPerson: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    composable<PeopleListRoute> {
        PeopleListScreen(
            onPersonClick = onPersonClick,
            onAddPerson = onAddPerson,
        )
    }
    composable<PersonDetailRoute>(
        deepLinks =
            listOf(
                navDeepLink { uriPattern = "${DeepLinks.SCHEME}://${DeepLinks.HOST_PERSON}/{personId}" },
            ),
    ) {
        PersonDetailScreen(
            onBack = onBack,
            onEdit = onEditPerson,
            onEventClick = onEventClick,
        )
    }
    composable<AddEditPersonRoute> {
        AddEditPersonScreen(onBack = onBack)
    }
    composable<BirthdayOnlyRoute> {
        BirthdayOnlyScreen(onBack = onBack)
    }
}

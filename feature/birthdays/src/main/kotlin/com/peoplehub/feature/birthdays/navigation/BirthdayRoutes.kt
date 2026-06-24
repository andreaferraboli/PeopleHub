package com.peoplehub.feature.birthdays.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.peoplehub.core.domain.navigation.DeepLinks
import com.peoplehub.feature.birthdays.AddBirthdaysScreen
import com.peoplehub.feature.birthdays.BirthdaysScreen
import kotlinx.serialization.Serializable

/** Type-safe navigation route for the birthdays ("Milestones") screen. */
@Serializable
data object BirthdaysRoute

/** Type-safe route for the bulk "Add birthdays" screen reached from the Milestones screen. */
@Serializable
data object AddBirthdaysRoute

/**
 * Registers the birthdays screens into the host graph. The Milestones screen is reachable both
 * in-app and via the `peoplehub://birthdays` deep link (used by the birthday widget and
 * notifications); from it the user can open the bulk "Add birthdays" screen.
 *
 * Navigation out of the screens is delegated to the supplied callbacks so the feature stays
 * decoupled from the rest of the app.
 */
fun NavGraphBuilder.birthdaysSection(
    onPersonClick: (Long) -> Unit,
    onAddBirthdays: () -> Unit,
    onBack: () -> Unit,
) {
    composable<BirthdaysRoute>(
        deepLinks =
            listOf(
                navDeepLink { uriPattern = "${DeepLinks.SCHEME}://${DeepLinks.HOST_BIRTHDAYS}" },
            ),
    ) {
        BirthdaysScreen(
            onPersonClick = onPersonClick,
            onAddBirthdays = onAddBirthdays,
            onBack = onBack,
        )
    }
    composable<AddBirthdaysRoute> {
        AddBirthdaysScreen(onBack = onBack)
    }
}

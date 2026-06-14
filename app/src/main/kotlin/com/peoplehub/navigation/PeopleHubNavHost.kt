package com.peoplehub.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.peoplehub.dashboard.DashboardScreen
import com.peoplehub.feature.birthdays.navigation.BirthdaysRoute
import com.peoplehub.feature.birthdays.navigation.birthdaysSection
import com.peoplehub.feature.events.navigation.AddEditEventRoute
import com.peoplehub.feature.events.navigation.EventDetailRoute
import com.peoplehub.feature.events.navigation.eventsSection
import com.peoplehub.feature.people.navigation.AddEditPersonRoute
import com.peoplehub.feature.people.navigation.BirthdayOnlyRoute
import com.peoplehub.feature.people.navigation.PeopleListRoute
import com.peoplehub.feature.people.navigation.PersonDetailRoute
import com.peoplehub.feature.people.navigation.peopleSection
import com.peoplehub.importguide.ImportGuideScreen
import com.peoplehub.settings.LanguageScreen
import com.peoplehub.settings.SettingsScreen

/** The single navigation graph wiring the dashboard, settings and all feature sections together. */
@Composable
fun PeopleHubNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = DashboardRoute, modifier = modifier) {
        composable<DashboardRoute> {
            DashboardScreen(
                onPersonClick = { navController.navigate(PersonDetailRoute(it)) },
                onEventClick = { navController.navigate(EventDetailRoute(it)) },
                onSeeAllPeople = { navController.navigate(PeopleListRoute) },
                onSeeAllBirthdays = { navController.navigate(BirthdaysRoute) },
                onAddPerson = { navController.navigate(AddEditPersonRoute()) },
            )
        }

        peopleSection(
            onPersonClick = { navController.navigate(PersonDetailRoute(it)) },
            onAddPerson = { navController.navigate(AddEditPersonRoute()) },
            onEditPerson = { navController.navigate(AddEditPersonRoute(it)) },
            onEventClick = { navController.navigate(EventDetailRoute(it)) },
            onBack = { navController.popBackStack() },
        )

        eventsSection(
            onEventClick = { navController.navigate(EventDetailRoute(it)) },
            onAddEvent = { navController.navigate(AddEditEventRoute()) },
            onEditEvent = { navController.navigate(AddEditEventRoute(it)) },
            onPersonClick = { navController.navigate(PersonDetailRoute(it)) },
            onBack = { navController.popBackStack() },
        )

        birthdaysSection(
            onPersonClick = { navController.navigate(PersonDetailRoute(it)) },
            onBack = { navController.popBackStack() },
        )

        composable<SettingsRoute> {
            SettingsScreen(
                onOpenImportGuide = { navController.navigate(ImportGuideRoute) },
                onOpenBirthdayOnly = { navController.navigate(BirthdayOnlyRoute) },
                onOpenLanguage = { navController.navigate(LanguageRoute) },
            )
        }

        composable<ImportGuideRoute> {
            ImportGuideScreen(onBack = { navController.popBackStack() })
        }

        composable<LanguageRoute> {
            LanguageScreen(onBack = { navController.popBackStack() })
        }
    }
}

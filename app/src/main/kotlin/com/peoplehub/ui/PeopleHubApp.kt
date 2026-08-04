package com.peoplehub.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.peoplehub.navigation.PeopleHubNavHost
import com.peoplehub.navigation.TopLevelDestination
import com.peoplehub.update.UpdatePrompt

/**
 * The root app composable: a [Scaffold] hosting the bottom navigation bar and the navigation graph.
 * The bottom bar is shown only on the four top-level destinations and hidden on detail/edit screens.
 *
 * The app draws edge to edge, so the insets are handed down deliberately: this scaffold applies none
 * itself (`contentWindowInsets` is empty) and lets each screen's own scaffold pad for the status bar
 * and — on the screens where no navigation bar is drawn — for the phone's navigation buttons. Where
 * the bottom bar *is* drawn it already covers that area (Material's `NavigationBar` insets itself), so
 * the space it occupies is both padded **and** consumed here, otherwise every top-level screen would
 * reserve the navigation-bar height a second time and float above the tab bar.
 */
@Composable
fun PeopleHubApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar =
        TopLevelDestination.entries.any { destination ->
            currentDestination.isOn(destination)
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                PeopleHubBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { destination -> navController.navigateToTopLevel(destination) },
                )
            }
        },
    ) { padding ->
        val bottomBarSpace = PaddingValues(bottom = padding.calculateBottomPadding())
        PeopleHubNavHost(
            navController = navController,
            modifier = Modifier.padding(bottomBarSpace).consumeWindowInsets(bottomBarSpace),
        )
    }

    // Silent automatic update check on launch; shows a dialog only when a newer release exists.
    UpdatePrompt()
}

@Composable
private fun PeopleHubBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination.isOn(destination),
                onClick = { onNavigate(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes), style = MaterialTheme.typography.labelMedium) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

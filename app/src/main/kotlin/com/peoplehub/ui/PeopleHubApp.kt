package com.peoplehub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.peoplehub.core.ui.components.BottomFade
import com.peoplehub.core.ui.components.LocalNavBarClearance
import com.peoplehub.core.ui.components.LocalNavHazeState
import com.peoplehub.core.ui.components.NavBarItem
import com.peoplehub.core.ui.components.VellutoNavBar
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.navigation.PeopleHubNavHost
import com.peoplehub.navigation.TopLevelDestination
import com.peoplehub.update.UpdatePrompt
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * The root app composable: the navigation graph with the Velluto floating navigation bar over it.
 * The bar is shown only on the five top-level destinations and hidden on detail/edit screens.
 *
 * The app draws edge to edge. On top-level screens the content runs **under** the floating glass bar
 * (that is what the glass blurs), so this shell consumes the system navigation inset for the nav
 * host and publishes, through [LocalNavBarClearance], how much room the bar takes from the bottom
 * (system inset + bar + its margin). Each top-level screen adds that clearance below its scrolling
 * content and above its FAB. On every other screen the clearance is zero and the screen's own
 * scaffold insets itself as before.
 */
@Composable
fun PeopleHubApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedIndex = TopLevelDestination.entries.indexOfFirst { currentDestination.isOn(it) }
    val showBottomBar = selectedIndex >= 0
    val hazeState = remember { HazeState() }
    val spacing = PeopleHubTheme.spacing
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val clearance = if (showBottomBar) systemBottom + spacing.navBottom + spacing.navHeight else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalNavBarClearance provides clearance,
            LocalNavHazeState provides if (showBottomBar) hazeState else null,
        ) {
            PeopleHubNavHost(
                navController = navController,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .haze(hazeState)
                        .then(if (showBottomBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
            )
        }
        if (showBottomBar) {
            BottomFade(modifier = Modifier.align(Alignment.BottomCenter))
            val items =
                TopLevelDestination.entries.map { destination ->
                    NavBarItem(label = stringResource(destination.labelRes), icon = destination.icon)
                }
            VellutoNavBar(
                items = items,
                selectedIndex = selectedIndex,
                onSelect = { index -> navController.navigateToTopLevel(TopLevelDestination.entries[index]) },
                hazeState = hazeState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = spacing.gutter, end = spacing.gutter, bottom = spacing.navBottom),
            )
        }
    }

    // Silent automatic update check on launch; shows a dialog only when a newer release exists.
    UpdatePrompt()
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

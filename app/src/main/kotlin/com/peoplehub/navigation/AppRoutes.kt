package com.peoplehub.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.graphics.vector.ImageVector
import com.peoplehub.R
import com.peoplehub.feature.events.navigation.EventsRoute
import com.peoplehub.feature.people.navigation.PeopleListRoute
import kotlinx.serialization.Serializable

/** Top-level, app-owned routes (the dashboard and settings tabs). */
@Serializable
data object DashboardRoute

@Serializable
data object SettingsRoute

/** The import-format reference, reached from the Vault. */
@Serializable
data object ImportGuideRoute

/**
 * The four bottom-navigation destinations. Each maps to the start route of its section; detail and
 * edit screens are reached from within and are not top-level destinations.
 */
enum class TopLevelDestination(val route: Any, val labelRes: Int, val icon: ImageVector) {
    REFLECT(DashboardRoute, R.string.tab_reflect, Icons.Outlined.AutoAwesome),
    CIRCLE(PeopleListRoute, R.string.tab_circle, Icons.Outlined.Groups),
    EVENTS(EventsRoute, R.string.tab_events, Icons.AutoMirrored.Outlined.EventNote),
    VAULT(SettingsRoute, R.string.tab_vault, Icons.Outlined.Lock),
}

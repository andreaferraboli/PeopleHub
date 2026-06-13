package com.peoplehub.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The shared PeopleHub top app bar: an editorial serif title in champagne gold over a transparent
 * surface that tints as the content scrolls beneath it.
 *
 * @param centered renders a [CenterAlignedTopAppBar] (used for brand-forward screens) instead of a
 * leading-aligned [TopAppBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleHubTopBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    centered: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        titleContentColor = MaterialTheme.colorScheme.primary,
        navigationIconContentColor = MaterialTheme.colorScheme.primary,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val titleContent: @Composable () -> Unit = {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (centered) {
        CenterAlignedTopAppBar(
            title = titleContent,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = titleContent,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}

package com.peoplehub.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.R
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.glassSurface
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes

/** Centred loading state: a thin accent spinner on a soft accent glow. */
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    val c = PeopleHubTheme.colors
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AmbientGlow(size = 160.dp, strong = false)
        CircularProgressIndicator(color = c.accent, trackColor = c.track, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
    }
}

/**
 * A centred empty state with an icon, headline and supporting text, plus an optional [action] slot
 * (e.g. a primary call-to-action button) rendered beneath the text.
 */
@Composable
fun EmptyView(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = VellutoIcons.Inbox,
    action: (@Composable () -> Unit)? = null,
) {
    StateMessage(icon = icon, title = title, description = description, modifier = modifier, action = action)
}

/** A centred error state with an icon, headline and the failure message. */
@Composable
fun ErrorView(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ui_error_generic),
) {
    StateMessage(
        icon = VellutoIcons.CircleAlert,
        tint = PeopleHubTheme.colors.err,
        title = title,
        description = message,
        modifier = modifier,
    )
}

@Composable
private fun StateMessage(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = PeopleHubTheme.colors.accentOnSoft,
    action: (@Composable () -> Unit)? = null,
) {
    val c = PeopleHubTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AmbientGlow(size = 180.dp, strong = false)
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .glassSurface(c, VellutoShapes.CardSmall),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = 24.dp)) {
                action()
            }
        }
    }
}

/**
 * Renders a [UiState] exhaustively, dispatching to the appropriate slot. Every screen funnels its
 * state through this so the four cases are always handled consistently.
 */
@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    loadingContent: @Composable () -> Unit = { LoadingView() },
    emptyContent: @Composable () -> Unit = {
        EmptyView(
            title = stringResource(R.string.ui_empty_generic),
            description = stringResource(R.string.ui_empty_generic),
        )
    },
    errorContent: @Composable (String) -> Unit = { ErrorView(message = it) },
    successContent: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> loadingContent()
        UiState.Empty -> emptyContent()
        is UiState.Error -> errorContent(state.message)
        is UiState.Success -> successContent(state.data)
    }
}

package com.peoplehub.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R
import com.peoplehub.core.ui.components.SceneArt
import com.peoplehub.core.ui.components.ScreenHero
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.theme.PeopleHubTheme
import kotlinx.coroutines.launch

/**
 * The only screen shown without a signed-in account: every piece of data lives in the cloud under the
 * Google account, so there is nothing to show before signing in. Velluto hero (floating rings), one
 * line on where the data lives, and the single gold "Sign in with Google" action.
 */
@Composable
fun SignInScreen(viewModel: AccountViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
    val failedMessage = stringResource(R.string.sign_in_failed)

    LaunchedEffect(viewModel) {
        viewModel.signInFailures.collect { snackbarHostState.showSnackbar(failedMessage) }
    }

    SignInContent(
        signingIn = signingIn,
        snackbarHostState = snackbarHostState,
        onSignIn = {
            viewModel.onSignInStarted()
            scope.launch {
                GoogleSignIn
                    .requestIdToken(context)
                    .onSuccess(viewModel::onIdToken)
                    .onFailure { viewModel.onSignInFailed(it.message ?: it.javaClass.simpleName) }
            }
        },
    )
}

@Composable
private fun SignInContent(
    signingIn: Boolean,
    snackbarHostState: SnackbarHostState,
    onSignIn: () -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    val title = stringResource(R.string.sign_in_title)
    val split = title.trimEnd().lastIndexOf(' ')
    val accent = if (split > 0) title.substring(split + 1).trimEnd() else null
    val display = if (split > 0) title.substring(0, split) + "\n" + accent else title
    VellutoBackground(secondGlow = true) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.l),
            ) {
                ScreenHero(
                    title = display,
                    titleAccent = accent,
                    eyebrow = stringResource(R.string.sign_in_eyebrow),
                    subtitle = stringResource(R.string.sign_in_subtitle),
                    scene = SceneArt.Rings,
                    sceneOffsetX = 24.dp,
                    sceneOffsetY = 40.dp,
                )
                Spacer(Modifier.padding(top = spacing.s))
                VellutoButton(
                    text = stringResource(R.string.sign_in_button),
                    onClick = onSignIn,
                    icon = VellutoIcons.Cloud,
                    enabled = !signingIn,
                    breathing = !signingIn,
                    modifier =
                        Modifier
                            .padding(horizontal = spacing.gutter)
                            .fillMaxWidth()
                            .riseIn(1),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SignInPreview() {
    PeopleHubTheme {
        SignInContent(signingIn = false, snackbarHostState = remember { SnackbarHostState() }, onSignIn = {})
    }
}

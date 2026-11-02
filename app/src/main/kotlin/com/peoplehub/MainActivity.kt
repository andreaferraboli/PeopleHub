package com.peoplehub

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.account.AccountState
import com.peoplehub.account.AccountViewModel
import com.peoplehub.account.SignInScreen
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.locale.AppLocale
import com.peoplehub.ui.PeopleHubApp
import com.peoplehub.update.UpdatePrompt
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity hosting the entire Compose UI. Deep links declared on the navigation
 * destinations (e.g. `peoplehub://person/{id}`) are routed here via the manifest intent filter.
 *
 * Every piece of data lives in the cloud under the signed-in Google account, so the app shows the
 * sign-in screen and nothing else until someone signs in.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PeopleHubTheme {
                RequestNotificationPermission()
                SignedInGate()
            }
        }
    }
}

/**
 * Shows the app for the signed-in account, or the sign-in screen. The app is keyed on the account, so
 * signing in as someone else starts from a fresh navigation state.
 */
@Composable
private fun SignedInGate(viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        AccountState.SignedOut -> {
            SignInScreen(viewModel)
            UpdatePrompt()
        }
        is AccountState.SignedIn -> key(current.account.uid) { PeopleHubApp() }
    }
}

/**
 * Requests the Android 13+ notification permission on first launch. The system surfaces its own
 * prompt; if the user declines, the app keeps working and simply posts no notifications.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.locale.AppLocale
import com.peoplehub.ui.PeopleHubApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity hosting the entire Compose UI. Deep links declared on the navigation
 * destinations (e.g. `peoplehub://person/{id}`) are routed here via the manifest intent filter.
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
                PeopleHubApp()
            }
        }
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

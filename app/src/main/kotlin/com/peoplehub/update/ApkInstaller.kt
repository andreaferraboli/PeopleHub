package com.peoplehub.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an update APK into the app's private cache and hands it to the system package installer.
 * All Android/`Context` interaction is confined here so the ViewModel stays framework-free.
 */
@Singleton
class ApkInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Whether the user has granted "install unknown apps" for this app (required on Android 8+). */
        fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

        /** Opens the system screen where the user grants the install-unknown-apps permission. */
        fun openInstallPermissionSettings() {
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /** Downloads [url] to a private cache file, following GitHub's redirect to its CDN. */
        suspend fun download(url: String): File =
            withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(directory, "peoplehub-update.apk")
                if (target.exists()) target.delete()
                val connection =
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = CONNECT_TIMEOUT_MILLIS
                        readTimeout = READ_TIMEOUT_MILLIS
                        setRequestProperty("User-Agent", "PeopleHub-Updater")
                    }
                try {
                    connection.inputStream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                target
            }

        /** Launches the system installer for the downloaded [file]. */
        fun launchInstall(file: File) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }

        private companion object {
            const val CONNECT_TIMEOUT_MILLIS = 15_000
            const val READ_TIMEOUT_MILLIS = 60_000
        }
    }

package com.peoplehub.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the latest release of the configured GitHub repository. The public `releases/latest`
 * endpoint needs no authentication for a public repo, so the update check is a single lightweight
 * GET — the only network call the app ever makes.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches the latest release and returns the bundled APK as an [AvailableUpdate], or `null` when
     * there is no release / no APK asset. Throws on network failure so callers can distinguish
     * "up to date" from "couldn't reach GitHub".
     */
    suspend fun fetchLatest(owner: String, repo: String): AvailableUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.github.com/repos/$owner/$repo/releases/latest").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PeopleHub-Updater")
            }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString(GithubReleaseDto.serializer(), body)
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext null
            val versionName = release.tagName.removePrefix("v").trim()
            val versionCode = versionCodeOf(versionName) ?: return@withContext null
            AvailableUpdate(
                versionName = versionName,
                versionCode = versionCode,
                apkUrl = apk.browserDownloadUrl,
                notes = release.body?.trim().orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Mirrors the `major*10000 + minor*100 + patch` scheme used for the app's versionCode. */
    private fun versionCodeOf(versionName: String): Int? {
        val parts = versionName.split(".").map { it.toIntOrNull() }
        if (parts.size < 3 || parts.any { it == null }) return null
        return parts[0]!! * 10000 + parts[1]!! * 100 + parts[2]!!
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}

package com.peoplehub.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Minimal mirror of the GitHub "latest release" JSON used by the updater. */
@Serializable
internal data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
internal data class GithubAssetDto(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

/** A newer release the app can install. */
data class AvailableUpdate(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String,
)

package com.peoplehub.work

import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long a background sweep waits for the cloud. The data lives only in Firestore, so a sweep
 * without a connection (or before the server answers) cannot know what is due: it gives up, claims
 * nothing, and the next trigger tries again.
 */
internal const val CLOUD_READ_TIMEOUT_MILLIS: Long = 45_000L

/** Runs a cloud read for a worker, or returns `null` when it did not finish in time. */
internal suspend fun <T : Any> readCloudOrNull(read: suspend () -> T): T? = withTimeoutOrNull(CLOUD_READ_TIMEOUT_MILLIS) { read() }

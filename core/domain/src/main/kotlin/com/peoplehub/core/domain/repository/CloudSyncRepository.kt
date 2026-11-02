package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Two-way sync of the local database with Firestore (`docs/firestore-schema.md`). The local database
 * stays the source the app reads; the cloud is a replica that other writers (the MCP server) can also
 * change. Every operation returns a [Result] instead of throwing.
 */
interface CloudSyncRepository {
    /** The sync state shown in the Vault. */
    val status: Flow<SyncStatus>

    /** Signs in to Firebase with a Google ID token obtained on the phone. */
    suspend fun signInWithGoogle(idToken: String): Result<Unit>

    /** Signs out. Local data and pending changes stay on the phone. */
    suspend fun signOut()

    /** First sync of an empty cloud: sends every local row and the synced settings. */
    suspend fun uploadAll(): Result<Unit>

    /** First sync on a phone joining an account whose cloud has data: replaces local data with it. */
    suspend fun downloadAll(): Result<Unit>

    /** Pulls cloud changes and pushes local ones. A no-op until the first upload or download. */
    suspend fun syncNow(): Result<Unit>

    /** Looks again whether the cloud has data, after a failed check. */
    suspend fun refreshCloudCheck()

    /** Tells the engine whether the app is in the foreground (real-time listeners run only then). */
    fun setForeground(foreground: Boolean)
}

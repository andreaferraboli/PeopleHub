package com.peoplehub.core.domain.model

import java.time.Instant

/**
 * What the Vault shows about cloud sync.
 *
 * @property account the Google account signed in to Firebase, or `null` when signed out.
 * @property initialAction what the first sync for this account still needs (see [InitialSyncAction]).
 * @property isSyncing whether a sync, upload or download is running right now.
 * @property lastSyncAt when the last sync finished successfully.
 * @property pendingChanges local changes not yet sent to the cloud.
 * @property lastError the message of the last failure, cleared by the next success.
 */
data class SyncStatus(
    val account: SyncAccount? = null,
    val initialAction: InitialSyncAction = InitialSyncAction.NONE,
    val isSyncing: Boolean = false,
    val lastSyncAt: Instant? = null,
    val pendingChanges: Int = 0,
    val lastError: String? = null,
) {
    /** Whether automatic sync is running: signed in and the first upload or download is done. */
    val isActive: Boolean get() = account != null && initialAction == InitialSyncAction.NONE
}

/** The Google account behind the Firebase session. */
data class SyncAccount(
    val uid: String,
    val email: String?,
    val displayName: String?,
)

/**
 * The one-off step a signed-in account needs before automatic sync starts.
 *
 * - [NONE]: done (or signed out), sync runs on its own.
 * - [CHECKING]: looking whether the cloud already holds data for this account.
 * - [UPLOAD]: the cloud is empty, offer "upload to Firebase" (the phone's data becomes the cloud's).
 * - [DOWNLOAD]: the cloud already has data (reinstall, second phone), offer "download from the cloud"
 *   (the cloud's data replaces the phone's).
 */
enum class InitialSyncAction { NONE, CHECKING, UPLOAD, DOWNLOAD }

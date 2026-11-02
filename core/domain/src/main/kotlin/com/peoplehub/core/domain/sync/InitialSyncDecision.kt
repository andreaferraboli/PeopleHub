package com.peoplehub.core.domain.sync

import com.peoplehub.core.domain.model.InitialSyncAction

/** Decides which one-off step the Vault offers after signing in. */
object InitialSyncDecision {
    /**
     * @param signedIn whether a Firebase account is signed in.
     * @param initialSyncDone whether this account already completed its first upload or download on
     * this phone (a per-account flag, so switching account asks again).
     * @param cloudHasData whether the cloud holds data for the account, or `null` while unknown.
     */
    fun resolve(
        signedIn: Boolean,
        initialSyncDone: Boolean,
        cloudHasData: Boolean?,
    ): InitialSyncAction =
        when {
            !signedIn || initialSyncDone -> InitialSyncAction.NONE
            cloudHasData == null -> InitialSyncAction.CHECKING
            cloudHasData -> InitialSyncAction.DOWNLOAD
            else -> InitialSyncAction.UPLOAD
        }
}

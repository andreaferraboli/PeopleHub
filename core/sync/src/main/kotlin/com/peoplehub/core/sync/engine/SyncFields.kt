package com.peoplehub.core.sync.engine

/** The bookkeeping fields every synced document carries (`docs/firestore-schema.md`). */
internal object SyncFields {
    const val UPDATED_AT: String = "updatedAt"
    const val SERVER_UPDATED_AT: String = "serverUpdatedAt"
    const val DELETED: String = "deleted"
    const val UPDATED_BY: String = "updatedBy"

    /** `updatedBy` of this install's writes. */
    fun androidTag(installId: String): String = "android:$installId"
}

/**
 * Last write wins on the whole document. A cloud version is applied unless a local change to the same
 * row, still waiting in the outbox, is newer than it; on a tie the cloud wins.
 */
internal object ConflictRule {
    fun remoteWins(remoteUpdatedAt: Long?, localPendingChangedAt: Long?): Boolean =
        localPendingChangedAt == null || (remoteUpdatedAt ?: 0L) >= localPendingChangedAt
}

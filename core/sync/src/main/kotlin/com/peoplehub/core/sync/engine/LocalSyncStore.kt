package com.peoplehub.core.sync.engine

import kotlinx.coroutines.flow.Flow

/** One outbox entry: [remoteId] of [collection] changed locally at [changedAt]. */
internal data class PendingChange(
    val collection: String,
    val remoteId: String,
    val changedAt: Long,
)

/** The phone side of sync. The Room implementation is `local/RoomLocalSyncStore`. */
internal interface LocalSyncStore {
    /** The number of local changes waiting to be pushed. */
    fun observePendingCount(): Flow<Int>

    /** Up to [limit] pending changes, parents first, oldest first. */
    suspend fun pendingChanges(limit: Int): List<PendingChange>

    /** The current document for [change], or `null` when the row no longer exists (a tombstone). */
    suspend fun documentFor(change: PendingChange): Map<String, Any?>?

    /** Clears pushed entries (an entry that changed again since stays queued). */
    suspend fun acknowledge(changes: List<PendingChange>)

    /** Queues every local row, for the first full upload. */
    suspend fun enqueueEverything(now: Long)

    /** Deletes all synced local data and the outbox, before a full download. Nothing is queued. */
    suspend fun wipeForDownload()

    /**
     * Runs [block] in one transaction in which local writes are *not* queued (they came from the
     * cloud), then re-derives what depends on them (last check-in, search index).
     */
    suspend fun <T> applyRemote(block: suspend RemoteApplier.() -> T): T
}

/** Result of applying one live cloud document. */
internal enum class UpsertResult {
    APPLIED,

    /** Not written: the document references a parent that is not on the phone (yet). */
    ORPHAN,
}

/** Writes allowed inside [LocalSyncStore.applyRemote]. */
internal interface RemoteApplier {
    suspend fun pendingChangedAt(collection: String, remoteId: String): Long?

    suspend fun discardPending(collection: String, remoteId: String)

    /**
     * Inserts or updates the row for [remoteId] from [data]. When the parent is missing it returns
     * [UpsertResult.ORPHAN], except that with [allowMissingParent] an event (whose person link is
     * optional) is stored unlinked.
     */
    suspend fun upsert(collection: String, remoteId: String, data: Map<String, Any?>, allowMissingParent: Boolean): UpsertResult

    suspend fun delete(collection: String, remoteId: String)
}

/** Per-account pull cursors (the last applied `serverUpdatedAt`, in micros, per collection). */
internal interface CursorStore {
    suspend fun cursor(uid: String, collection: String): Long

    suspend fun setCursor(uid: String, collection: String, micros: Long)
}

package com.peoplehub.core.sync.engine

/**
 * Sends the outbox to the cloud in batches of at most [batchSize] writes (Firestore allows 500 per
 * batch; 400 leaves room). Each entry becomes the row's full current document, or a tombstone
 * (`deleted: true`, merged so the last content stays readable) when the row is gone. `updatedAt` is
 * the moment the change was made on the phone, which is what last-write-wins compares.
 */
internal class Pusher(
    private val remote: RemoteStore,
    private val local: LocalSyncStore,
    private val ownTag: String,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxBatches: Int = DEFAULT_MAX_BATCHES,
) {
    /** Pushes until the outbox is empty (or [maxBatches] batches went up). Returns the documents written. */
    suspend fun push(uid: String): Int {
        var written = 0
        repeat(maxBatches) {
            val changes = local.pendingChanges(batchSize)
            if (changes.isEmpty()) return written
            val writes = changes.map { change -> writeFor(change) }
            remote.commit(uid, writes)
            local.acknowledge(changes)
            written += changes.size
        }
        return written
    }

    private suspend fun writeFor(change: PendingChange): RemoteWrite {
        val document = local.documentFor(change)
        val bookkeeping =
            mapOf(
                SyncFields.UPDATED_AT to change.changedAt,
                SyncFields.DELETED to (document == null),
                SyncFields.UPDATED_BY to ownTag,
            )
        return if (document == null) {
            RemoteWrite(change.collection, change.remoteId, bookkeeping, merge = true)
        } else {
            RemoteWrite(change.collection, change.remoteId, document + bookkeeping, merge = false)
        }
    }

    companion object {
        const val DEFAULT_BATCH_SIZE: Int = 400
        const val DEFAULT_MAX_BATCHES: Int = 50
    }
}

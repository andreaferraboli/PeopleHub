package com.peoplehub.core.sync.engine

import java.io.Closeable

/**
 * The cloud side of sync, reduced to what the engine needs. The Firestore implementation lives in
 * `remote/`; tests use an in-memory one, so pulling and pushing are tested without Firebase.
 *
 * Collections are the names in `SyncCollections`, under `users/{uid}/`.
 */
internal interface RemoteStore {
    /**
     * Every document of [collection] whose `serverUpdatedAt` is after [afterMicros] (microseconds
     * since the epoch, Firestore's timestamp precision), tombstones included.
     */
    suspend fun fetchChangedSince(uid: String, collection: String, afterMicros: Long): List<RemoteDoc>

    /** Writes [writes] atomically (one batch; the caller keeps it within Firestore's limit). */
    suspend fun commit(uid: String, writes: List<RemoteWrite>)

    /** Whether the account already has live (non-deleted) data in the cloud. */
    suspend fun hasData(uid: String): Boolean

    /**
     * Calls [onRemoteChange] whenever a document of [cursors]' collections changes after its cursor,
     * unless the change was written by [ownTag] (our own push coming back). Closing stops listening.
     */
    fun listen(uid: String, cursors: Map<String, Long>, ownTag: String, onRemoteChange: () -> Unit): Closeable
}

/** A cloud document: its id, its fields (without `serverUpdatedAt`) and that server time in micros. */
internal data class RemoteDoc(
    val id: String,
    val data: Map<String, Any?>,
    val serverUpdatedMicros: Long,
) {
    val updatedAt: Long? get() = (data[SyncFields.UPDATED_AT] as? Number)?.toLong()
    val deleted: Boolean get() = data[SyncFields.DELETED] == true
    val updatedBy: String? get() = data[SyncFields.UPDATED_BY] as? String
}

/**
 * One document write. [merge] keeps the fields not in [data] (used for tombstones, so the last known
 * content stays readable); otherwise the document is replaced whole (last write wins on the document).
 * The store adds `serverUpdatedAt` itself.
 */
internal data class RemoteWrite(
    val collection: String,
    val id: String,
    val data: Map<String, Any?>,
    val merge: Boolean,
)

package com.peoplehub.core.data.cloud

import kotlinx.coroutines.flow.Flow

/**
 * The signed-in user's documents under `users/{uid}/` (see `docs/firestore-schema.md`), seen as plain
 * field maps. The repositories build the domain on top of it; tests swap in an in-memory fake.
 *
 * Reads only ever see **live** documents (`deleted == false`): a deletion is a tombstone, kept for the
 * MCP server and any other reader, never a removed document.
 */
internal interface CloudStore {
    /**
     * The live documents of [collection], re-emitted on every change. Emits an empty list while signed
     * out. The first emission waits for the server, so a cold start offline shows "loading", never an
     * empty list that would read as "no data".
     */
    fun observe(collection: String): Flow<List<CloudDoc>>

    /** One document, `null` while signed out, missing or tombstoned. Same first-emission rule. */
    fun observeDocument(collection: String, id: String): Flow<CloudDoc?>

    /**
     * Applies [writes], stamping the sync fields on every document. Split into batches of at most
     * [MAX_BATCH_WRITES] operations; each batch is atomic, the whole list is not.
     */
    suspend fun commit(writes: List<CloudWrite>)

    /** Asks the server (no cache) whether [collection] holds at least one live document. */
    suspend fun hasLiveDocuments(collection: String): Boolean

    companion object {
        /** Firestore accepts 500 writes per batch; 400 leaves room. */
        const val MAX_BATCH_WRITES: Int = 400
    }
}

/** A document id and its fields, without `serverUpdatedAt` (a moving server stamp, not data). */
internal data class CloudDoc(
    val id: String,
    val data: Map<String, Any?>,
)

/** One write; the store adds `updatedAt`, `serverUpdatedAt`, `deleted` and `updatedBy`. */
internal sealed interface CloudWrite {
    val collection: String
    val id: String

    /**
     * Creates the document or merges [data] into it: fields not mentioned stay as they are, so a
     * partial update never wipes what another writer (the MCP server) added.
     */
    data class Put(
        override val collection: String,
        override val id: String,
        val data: Map<String, Any?>,
    ) : CloudWrite

    /** Marks the document deleted (`deleted: true`), keeping its last content readable. */
    data class Tombstone(
        override val collection: String,
        override val id: String,
    ) : CloudWrite
}

/** Collection names and the bookkeeping fields of the contract. */
internal object Cloud {
    const val PEOPLE: String = "people"
    const val CHECK_INS: String = "checkIns"
    const val EVENTS: String = "events"
    const val REMINDERS: String = "reminders"
    const val REMINDER_COMPLETIONS: String = "reminderCompletions"
    const val SETTINGS: String = "settings"
    const val SETTINGS_MAIN: String = "main"

    const val UPDATED_AT: String = "updatedAt"
    const val SERVER_UPDATED_AT: String = "serverUpdatedAt"
    const val DELETED: String = "deleted"
    const val UPDATED_BY: String = "updatedBy"

    /** `updatedBy` of this install's writes. */
    fun androidTag(installId: String): String = "android:$installId"
}

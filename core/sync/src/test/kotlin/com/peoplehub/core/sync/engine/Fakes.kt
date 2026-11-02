package com.peoplehub.core.sync.engine

import com.peoplehub.core.database.sync.SyncCollections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.Closeable

/** In-memory cloud: documents per collection, each with a server time handed out monotonically. */
internal class FakeRemoteStore : RemoteStore {
    val docs = HashMap<String, MutableMap<String, RemoteDoc>>()
    val commits = ArrayList<List<RemoteWrite>>()
    private var clock = 1_000L

    /** Writes a document as another client (the MCP) would. */
    fun put(collection: String, id: String, data: Map<String, Any?>): RemoteDoc {
        val doc = RemoteDoc(id, data, ++clock)
        docs.getOrPut(collection) { LinkedHashMap() }[id] = doc
        return doc
    }

    override suspend fun fetchChangedSince(uid: String, collection: String, afterMicros: Long): List<RemoteDoc> =
        docs[collection].orEmpty().values.filter { it.serverUpdatedMicros > afterMicros }

    override suspend fun commit(uid: String, writes: List<RemoteWrite>) {
        commits += writes
        writes.forEach { write ->
            val previous = docs[write.collection]?.get(write.id)?.data.orEmpty()
            put(write.collection, write.id, if (write.merge) previous + write.data else write.data)
        }
    }

    override suspend fun hasData(uid: String): Boolean = docs.values.any { it.values.any { doc -> !doc.deleted } }

    override fun listen(uid: String, cursors: Map<String, Long>, ownTag: String, onRemoteChange: () -> Unit) = Closeable { }
}

/**
 * In-memory phone: rows keyed by collection and remote id, with parent references checked the way
 * the Room store checks them (check-ins, reminders and events reference people; completions reference
 * reminders), plus an outbox.
 */
internal class FakeLocalStore : LocalSyncStore {
    val rows = HashMap<String, MutableMap<String, Map<String, Any?>>>()
    val outbox = LinkedHashMap<Pair<String, String>, Long>()

    /** Every write applied from the cloud, in order, as "collection/id" (or "-collection/id" for deletes). */
    val applyLog = ArrayList<String>()

    fun row(collection: String, id: String): Map<String, Any?>? = rows[collection]?.get(id)

    fun localWrite(collection: String, id: String, data: Map<String, Any?>?, at: Long) {
        if (data == null) rows[collection]?.remove(id) else rows.getOrPut(collection) { HashMap() }[id] = data
        outbox[collection to id] = at
    }

    override fun observePendingCount(): Flow<Int> = MutableStateFlow(outbox.size)

    override suspend fun pendingChanges(limit: Int): List<PendingChange> =
        outbox.entries
            .sortedWith(compareBy({ SyncCollections.ORDERED.indexOf(it.key.first) }, { it.value }))
            .take(limit)
            .map { PendingChange(it.key.first, it.key.second, it.value) }

    override suspend fun documentFor(change: PendingChange): Map<String, Any?>? = row(change.collection, change.remoteId)

    override suspend fun acknowledge(changes: List<PendingChange>) {
        changes.forEach { if (outbox[it.collection to it.remoteId] == it.changedAt) outbox.remove(it.collection to it.remoteId) }
    }

    override suspend fun enqueueEverything(now: Long) {
        rows.forEach { (collection, byId) -> byId.keys.forEach { outbox[collection to it] = now } }
    }

    override suspend fun wipeForDownload() {
        rows.clear()
        outbox.clear()
    }

    override suspend fun <T> applyRemote(block: suspend RemoteApplier.() -> T): T = Applier().block()

    private inner class Applier : RemoteApplier {
        override suspend fun pendingChangedAt(collection: String, remoteId: String): Long? = outbox[collection to remoteId]

        override suspend fun discardPending(collection: String, remoteId: String) {
            outbox.remove(collection to remoteId)
        }

        override suspend fun upsert(
            collection: String,
            remoteId: String,
            data: Map<String, Any?>,
            allowMissingParent: Boolean,
        ): UpsertResult {
            val parent =
                when (collection) {
                    SyncCollections.CHECK_INS, SyncCollections.REMINDERS, SyncCollections.EVENTS ->
                        (data["personId"] as? String)?.let { SyncCollections.PEOPLE to it }
                    SyncCollections.REMINDER_COMPLETIONS -> (data["reminderId"] as? String)?.let { SyncCollections.REMINDERS to it }
                    else -> null
                }
            var stored = data
            if (parent != null && row(parent.first, parent.second) == null) {
                if (!(allowMissingParent && collection == SyncCollections.EVENTS)) return UpsertResult.ORPHAN
                stored = data + ("personId" to null)
            }
            rows.getOrPut(collection) { HashMap() }[remoteId] = stored
            applyLog += "$collection/$remoteId"
            return UpsertResult.APPLIED
        }

        override suspend fun delete(collection: String, remoteId: String) {
            rows[collection]?.remove(remoteId)
            applyLog += "-$collection/$remoteId"
        }
    }
}

internal class FakeCursors : CursorStore {
    val values = HashMap<String, Long>()

    override suspend fun cursor(uid: String, collection: String): Long = values[collection] ?: 0L

    override suspend fun setCursor(uid: String, collection: String, micros: Long) {
        values[collection] = micros
    }
}

package com.peoplehub.core.data

import com.peoplehub.core.data.cloud.CloudDoc
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.CloudWrite
import com.peoplehub.core.data.local.EventMedia
import com.peoplehub.core.data.local.LocalMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A stored document of the fake: its fields and whether it is a tombstone. */
internal data class StoredDoc(
    val data: Map<String, Any?>,
    val deleted: Boolean = false,
)

/**
 * In-memory [CloudStore] with Firestore's write semantics as the app uses them: a put merges into the
 * document and clears `deleted`, a tombstone keeps the content and sets it. Reads see live documents.
 */
internal class FakeCloudStore : CloudStore {
    private val docs = MutableStateFlow<Map<String, Map<String, StoredDoc>>>(emptyMap())

    /** Every commit, as passed in. */
    val commits = mutableListOf<List<CloudWrite>>()

    fun seed(collection: String, id: String, data: Map<String, Any?>, deleted: Boolean = false) {
        docs.update { all -> all + (collection to (all[collection].orEmpty() + (id to StoredDoc(data, deleted)))) }
    }

    fun stored(collection: String, id: String): StoredDoc? = docs.value[collection]?.get(id)

    fun live(collection: String): Map<String, Map<String, Any?>> =
        docs.value[collection]
            .orEmpty()
            .filterValues { !it.deleted }
            .mapValues { it.value.data }

    fun tombstoned(collection: String): Set<String> =
        docs.value[collection]
            .orEmpty()
            .filterValues { it.deleted }
            .keys

    override fun observe(collection: String): Flow<List<CloudDoc>> =
        docs.map { all -> all[collection].orEmpty().filterValues { !it.deleted }.map { (id, doc) -> CloudDoc(id, doc.data) } }

    override fun observeDocument(collection: String, id: String): Flow<CloudDoc?> =
        docs.map { all -> all[collection]?.get(id)?.takeIf { !it.deleted }?.let { CloudDoc(id, it.data) } }

    override suspend fun commit(writes: List<CloudWrite>) {
        if (writes.isEmpty()) return
        commits += writes
        docs.update { all ->
            writes.fold(all) { acc, write ->
                val collection = acc[write.collection].orEmpty()
                val current = collection[write.id]
                val next =
                    when (write) {
                        is CloudWrite.Put -> StoredDoc(current?.data.orEmpty() + write.data, deleted = false)
                        is CloudWrite.Tombstone -> StoredDoc(current?.data.orEmpty(), deleted = true)
                    }
                acc + (write.collection to (collection + (write.id to next)))
            }
        }
    }

    override suspend fun hasLiveDocuments(collection: String): Boolean = live(collection).isNotEmpty()
}

/** In-memory [LocalMedia]. */
internal class FakeLocalMedia : LocalMedia {
    override val personPhotos = MutableStateFlow<Map<String, String>>(emptyMap())
    override val eventMedia = MutableStateFlow<Map<String, EventMedia>>(emptyMap())
    override val pinnedEventId = MutableStateFlow<String?>(null)

    override suspend fun setPersonPhoto(personId: String, path: String?) {
        personPhotos.update { if (path == null) it - personId else it + (personId to path) }
    }

    override suspend fun setEventMedia(eventId: String, media: EventMedia?) {
        eventMedia.update { if (media?.imagePath == null) it - eventId else it + (eventId to media) }
    }

    override suspend fun setPinnedEvent(eventId: String?) {
        pinnedEventId.value = eventId
    }

    override suspend fun forget(personIds: Collection<String>, eventIds: Collection<String>) {
        personPhotos.update { it - personIds.toSet() }
        eventMedia.update { it - eventIds.toSet() }
        if (pinnedEventId.value in eventIds) pinnedEventId.value = null
    }

    override suspend fun putAll(photos: Map<String, String>, events: Map<String, EventMedia>, pinnedEventId: String?) {
        personPhotos.update { photos + it }
        eventMedia.update { events + it }
        if (pinnedEventId != null && this.pinnedEventId.value == null) this.pinnedEventId.value = pinnedEventId
    }
}

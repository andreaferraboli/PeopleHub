package com.peoplehub.core.sync.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.peoplehub.core.database.sync.SyncCollections
import com.peoplehub.core.sync.engine.RemoteDoc
import com.peoplehub.core.sync.engine.RemoteStore
import com.peoplehub.core.sync.engine.RemoteWrite
import com.peoplehub.core.sync.engine.SyncFields
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RemoteStore] on Cloud Firestore, under `users/{uid}/`.
 *
 * Reads always go to the server (`Source.SERVER`): the client runs with a memory-only cache (see
 * `SyncModule`), Room being the only local copy, and a pull served from a stale cache would move the
 * cursors past changes it never saw. Writes stamp `serverUpdatedAt` with the server time, which is
 * what every client's cursor follows.
 */
@Singleton
internal class FirestoreRemoteStore
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
    ) : RemoteStore {
        override suspend fun fetchChangedSince(uid: String, collection: String, afterMicros: Long): List<RemoteDoc> {
            val snapshot =
                withTimeout(TIMEOUT_MILLIS) {
                    collection(uid, collection)
                        .whereGreaterThan(SyncFields.SERVER_UPDATED_AT, timestampOf(afterMicros))
                        .orderBy(SyncFields.SERVER_UPDATED_AT)
                        .get(Source.SERVER)
                        .await()
                }
            return snapshot.documents.mapNotNull { document ->
                val stamp = document.getTimestamp(SyncFields.SERVER_UPDATED_AT) ?: return@mapNotNull null
                RemoteDoc(
                    id = document.id,
                    data = document.data.orEmpty() - SyncFields.SERVER_UPDATED_AT,
                    serverUpdatedMicros = microsOf(stamp),
                )
            }
        }

        override suspend fun commit(uid: String, writes: List<RemoteWrite>) {
            if (writes.isEmpty()) return
            val batch = firestore.batch()
            for (write in writes) {
                val reference = collection(uid, write.collection).document(write.id)
                val data = write.data + (SyncFields.SERVER_UPDATED_AT to FieldValue.serverTimestamp())
                if (write.merge) batch.set(reference, data, SetOptions.merge()) else batch.set(reference, data)
            }
            withTimeout(TIMEOUT_MILLIS) { batch.commit().await() }
        }

        override suspend fun hasData(uid: String): Boolean =
            withTimeout(TIMEOUT_MILLIS) {
                listOf(SyncCollections.PEOPLE, SyncCollections.EVENTS).any { name ->
                    !collection(uid, name)
                        .whereEqualTo(SyncFields.DELETED, false)
                        .limit(1)
                        .get(Source.SERVER)
                        .await()
                        .isEmpty
                }
            }

        override fun listen(
            uid: String,
            cursors: Map<String, Long>,
            ownTag: String,
            onRemoteChange: () -> Unit,
        ): Closeable {
            val registrations: List<ListenerRegistration> =
                cursors.map { (name, micros) ->
                    collection(uid, name)
                        .whereGreaterThan(SyncFields.SERVER_UPDATED_AT, timestampOf(micros))
                        .addSnapshotListener { snapshot, error ->
                            if (error != null || snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                            val foreign = snapshot.documentChanges.any { it.document.getString(SyncFields.UPDATED_BY) != ownTag }
                            if (foreign) onRemoteChange()
                        }
                }
            return Closeable { registrations.forEach(ListenerRegistration::remove) }
        }

        private fun collection(uid: String, name: String): CollectionReference =
            firestore.collection(USERS).document(uid).collection(name)

        private companion object {
            const val USERS = "users"
            const val TIMEOUT_MILLIS = 60_000L
            const val MICROS_PER_SECOND = 1_000_000L
            const val NANOS_PER_MICRO = 1_000

            fun microsOf(stamp: Timestamp): Long = stamp.seconds * MICROS_PER_SECOND + stamp.nanoseconds / NANOS_PER_MICRO

            fun timestampOf(micros: Long): Timestamp =
                Timestamp(micros / MICROS_PER_SECOND, ((micros % MICROS_PER_SECOND) * NANOS_PER_MICRO).toInt())
        }
    }

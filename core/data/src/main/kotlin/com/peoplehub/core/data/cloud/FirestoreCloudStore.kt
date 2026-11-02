package com.peoplehub.core.data.cloud

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.peoplehub.core.data.di.DataScope
import com.peoplehub.core.data.local.DeviceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CloudStore] on Cloud Firestore, under `users/{uid}/`, with a **memory-only** cache: nothing is
 * written to disk, so the phone holds no copy of the data between runs.
 *
 * - One snapshot listener per collection, shared by every reader ([shareIn], kept 5 s after the last
 *   reader leaves so screen changes do not restart it). Its first emission waits for the server; after
 *   that every change is emitted, including the phone's own writes before the server acknowledges them.
 * - Writes stamp the contract's sync fields and go out in batches of at most 400. A batch waits up to
 *   [ACK_TIMEOUT_MILLIS] for the server: a rejection surfaces as an exception, while a write still
 *   waiting (no connection) stays queued in memory and is sent when the network comes back.
 */
@Singleton
internal class FirestoreCloudStore
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val auth: CloudAuth,
        private val device: DeviceStore,
        private val clock: Clock,
        @DataScope private val scope: CoroutineScope,
    ) : CloudStore {
        private val collections = ConcurrentHashMap<String, Flow<List<CloudDoc>>>()
        private val documents = ConcurrentHashMap<String, Flow<CloudDoc?>>()

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observe(collection: String): Flow<List<CloudDoc>> =
            collections.getOrPut(collection) {
                auth.uid
                    .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else listen(uid, collection) }
                    .shared()
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeDocument(collection: String, id: String): Flow<CloudDoc?> =
            documents.getOrPut("$collection/$id") {
                auth.uid
                    .flatMapLatest { uid -> if (uid == null) flowOf(null) else listenDocument(uid, collection, id) }
                    .shared()
            }

        override suspend fun commit(writes: List<CloudWrite>) {
            if (writes.isEmpty()) return
            val uid = auth.currentUid() ?: error("Not signed in")
            val tag = Cloud.androidTag(device.installId())
            for (chunk in writes.chunked(CloudStore.MAX_BATCH_WRITES)) {
                val batch = firestore.batch()
                val now = clock.millis()
                for (write in chunk) {
                    val reference = collection(uid, write.collection).document(write.id)
                    val sync =
                        mapOf(
                            Cloud.UPDATED_AT to now,
                            Cloud.SERVER_UPDATED_AT to FieldValue.serverTimestamp(),
                            Cloud.UPDATED_BY to tag,
                        )
                    val data =
                        when (write) {
                            is CloudWrite.Put -> write.data + sync + (Cloud.DELETED to false)
                            is CloudWrite.Tombstone -> sync + (Cloud.DELETED to true)
                        }
                    batch.set(reference, data, SetOptions.merge())
                }
                awaitAck(batch.commit())
            }
        }

        override suspend fun hasLiveDocuments(collection: String): Boolean {
            val uid = auth.currentUid() ?: error("Not signed in")
            return withTimeout(READ_TIMEOUT_MILLIS) {
                !collection(uid, collection)
                    .whereEqualTo(Cloud.DELETED, false)
                    .limit(1)
                    .get(Source.SERVER)
                    .await()
                    .isEmpty
            }
        }

        private suspend fun awaitAck(task: com.google.android.gms.tasks.Task<Void>) {
            try {
                withTimeout(ACK_TIMEOUT_MILLIS) { task.await() }
            } catch (pending: TimeoutCancellationException) {
                // Offline: the write is already applied locally and queued; Firestore sends it later.
                Log.i(TAG, "Write not acknowledged yet, left queued", pending)
            }
        }

        private fun listen(uid: String, name: String): Flow<List<CloudDoc>> =
            callbackFlow {
                var synced = false
                val registration =
                    collection(uid, name)
                        .whereEqualTo(Cloud.DELETED, false)
                        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                            if (error != null) {
                                close(error)
                                return@addSnapshotListener
                            }
                            if (snapshot == null) return@addSnapshotListener
                            // Wait for the server once: a cold memory cache would otherwise say "empty".
                            if (!synced && snapshot.metadata.isFromCache) return@addSnapshotListener
                            synced = true
                            trySend(snapshot.documents.map { it.toCloudDoc() })
                        }
                awaitClose { registration.remove() }
            }.distinctUntilChanged()

        private fun listenDocument(uid: String, name: String, id: String): Flow<CloudDoc?> =
            callbackFlow {
                var synced = false
                val registration =
                    collection(uid, name)
                        .document(id)
                        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                            if (error != null) {
                                close(error)
                                return@addSnapshotListener
                            }
                            if (snapshot == null) return@addSnapshotListener
                            if (!synced && snapshot.metadata.isFromCache) return@addSnapshotListener
                            synced = true
                            val live = snapshot.exists() && snapshot.getBoolean(Cloud.DELETED) != true
                            trySend(if (live) snapshot.toCloudDoc() else null)
                        }
                awaitClose { registration.remove() }
            }.distinctUntilChanged()

        /**
         * One listener for everyone. A listener error (a sign-out racing the listener, a transient
         * failure) restarts it after a pause instead of tearing down the shared flow.
         */
        private fun <T> Flow<T>.shared(): Flow<T> =
            retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                Log.w(TAG, "Listener failed, retrying", cause)
                delay((attempt + 1).coerceAtMost(MAX_BACKOFF_STEPS) * RETRY_STEP_MILLIS)
                true
            }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0), replay = 1)

        private fun DocumentSnapshot.toCloudDoc(): CloudDoc = CloudDoc(id, data.orEmpty() - Cloud.SERVER_UPDATED_AT)

        private fun collection(uid: String, name: String): CollectionReference =
            firestore.collection(USERS).document(uid).collection(name)

        private companion object {
            const val TAG = "CloudStore"
            const val USERS = "users"
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val ACK_TIMEOUT_MILLIS = 10_000L
            const val READ_TIMEOUT_MILLIS = 30_000L
            const val RETRY_STEP_MILLIS = 2_000L
            const val MAX_BACKOFF_STEPS = 15L
        }
    }

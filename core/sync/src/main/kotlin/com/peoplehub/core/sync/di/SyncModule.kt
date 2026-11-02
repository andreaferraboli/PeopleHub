package com.peoplehub.core.sync.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.peoplehub.core.domain.repository.CloudSyncRepository
import com.peoplehub.core.sync.CloudSyncRepositoryImpl
import com.peoplehub.core.sync.engine.LocalSyncStore
import com.peoplehub.core.sync.engine.RemoteStore
import com.peoplehub.core.sync.local.RoomLocalSyncStore
import com.peoplehub.core.sync.prefs.SyncDataStore
import com.peoplehub.core.sync.remote.FirestoreRemoteStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** The process-wide scope the sync engine watches local changes and listens to the cloud in. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SyncScope

/** Binds the sync engine to the domain and its parts to their Room / Firestore implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncBindingsModule {
    @Binds
    @Singleton
    abstract fun bindCloudSyncRepository(impl: CloudSyncRepositoryImpl): CloudSyncRepository

    @Binds
    abstract fun bindRemoteStore(impl: FirestoreRemoteStore): RemoteStore

    @Binds
    abstract fun bindLocalSyncStore(impl: RoomLocalSyncStore): LocalSyncStore
}

/** Provides Firebase clients, the sync DataStore and the engine scope. */
@Module
@InstallIn(SingletonComponent::class)
internal object SyncProvidersModule {
    /**
     * Firestore with a memory-only cache: Room is the phone's copy of the data, so a second persistent
     * cache would only be a stale shadow of it, and pulls read from the server anyway.
     */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance().apply {
            firestoreSettings =
                FirebaseFirestoreSettings
                    .Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
        }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    @SyncDataStore
    fun provideSyncDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(SYNC_STORE_NAME) })

    @Provides
    @Singleton
    @SyncScope
    fun provideSyncScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private const val SYNC_STORE_NAME = "peoplehub_sync"
}

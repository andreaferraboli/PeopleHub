package com.peoplehub.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.peoplehub.core.data.cloud.CloudAuth
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.FirestoreCloudStore
import com.peoplehub.core.data.local.DeviceDataStore
import com.peoplehub.core.data.local.DeviceStore
import com.peoplehub.core.data.local.LocalMedia
import com.peoplehub.core.data.repository.BackupRepositoryImpl
import com.peoplehub.core.data.repository.CheckInRepositoryImpl
import com.peoplehub.core.data.repository.EventRepositoryImpl
import com.peoplehub.core.data.repository.PeopleRepositoryImpl
import com.peoplehub.core.data.repository.ReminderRepositoryImpl
import com.peoplehub.core.data.settings.ReminderStateRepositoryImpl
import com.peoplehub.core.data.settings.SettingsRepositoryImpl
import com.peoplehub.core.domain.repository.AuthRepository
import com.peoplehub.core.domain.repository.BackupRepository
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.repository.EventRepository
import com.peoplehub.core.domain.repository.PeopleRepository
import com.peoplehub.core.domain.repository.ReminderRepository
import com.peoplehub.core.domain.repository.ReminderStateRepository
import com.peoplehub.core.domain.repository.SettingsRepository
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

/** The process-wide scope the shared Firestore listeners live in. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class DataScope

/** Binds the domain repositories to their Firestore / DataStore implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindPeopleRepository(impl: PeopleRepositoryImpl): PeopleRepository

    @Binds
    @Singleton
    abstract fun bindCheckInRepository(impl: CheckInRepositoryImpl): CheckInRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindReminderStateRepository(impl: ReminderStateRepositoryImpl): ReminderStateRepository

    @Binds
    abstract fun bindAuthRepository(impl: CloudAuth): AuthRepository

    @Binds
    abstract fun bindCloudStore(impl: FirestoreCloudStore): CloudStore

    @Binds
    abstract fun bindLocalMedia(impl: DeviceStore): LocalMedia
}

/** Provides Firebase clients, the DataStores and the listener scope. */
@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidersModule {
    /**
     * Firestore with a memory-only cache: the data must not be kept on the phone, so nothing is
     * persisted to disk between runs. Pending writes live in memory until the server takes them.
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

    /** Device preferences (exact alarms) and the sweep bookkeeping, `peoplehub_settings`. */
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(SETTINGS_STORE_NAME) })

    /** The photo map, the widget pin and the install id, `peoplehub_device`. */
    @Provides
    @Singleton
    @DeviceDataStore
    fun provideDeviceDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(DEVICE_STORE_NAME) })

    @Provides
    @Singleton
    @DataScope
    fun provideDataScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private const val SETTINGS_STORE_NAME = "peoplehub_settings"
    private const val DEVICE_STORE_NAME = "peoplehub_device"
}

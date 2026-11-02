package com.peoplehub.core.database.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.peoplehub.core.database.ALL_MIGRATIONS
import com.peoplehub.core.database.PeopleHubDatabase
import com.peoplehub.core.database.dao.CheckInDao
import com.peoplehub.core.database.dao.EventDao
import com.peoplehub.core.database.dao.PeopleDao
import com.peoplehub.core.database.dao.ReminderDao
import com.peoplehub.core.database.dao.SyncDao
import com.peoplehub.core.database.sync.SyncTriggers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Room database, its DAOs, and the settings DataStore. */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): PeopleHubDatabase =
        Room
            .databaseBuilder(context, PeopleHubDatabase::class.java, PeopleHubDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(SyncTriggersCallback)
            .build()

    @Provides
    fun providePeopleDao(database: PeopleHubDatabase): PeopleDao = database.peopleDao()

    @Provides
    fun provideCheckInDao(database: PeopleHubDatabase): CheckInDao = database.checkInDao()

    @Provides
    fun provideEventDao(database: PeopleHubDatabase): EventDao = database.eventDao()

    @Provides
    fun provideReminderDao(database: PeopleHubDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun provideSyncDao(database: PeopleHubDatabase): SyncDao = database.syncDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(SETTINGS_STORE_NAME) },
        )

    private const val SETTINGS_STORE_NAME = "peoplehub_settings"
}

/**
 * Installs the sync triggers on a database Room creates from scratch (first launch, or after a
 * destructive downgrade), the same statements the v10 -> v11 migration runs on an upgraded one.
 */
private object SyncTriggersCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        SyncTriggers.install(db)
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        SyncTriggers.install(db)
    }
}

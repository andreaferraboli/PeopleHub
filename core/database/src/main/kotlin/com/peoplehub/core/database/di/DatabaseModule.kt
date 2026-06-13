package com.peoplehub.core.database.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.peoplehub.core.database.ALL_MIGRATIONS
import com.peoplehub.core.database.PeopleHubDatabase
import com.peoplehub.core.database.dao.CheckInDao
import com.peoplehub.core.database.dao.EventDao
import com.peoplehub.core.database.dao.PeopleDao
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
    fun provideDatabase(@ApplicationContext context: Context): PeopleHubDatabase =
        Room.databaseBuilder(context, PeopleHubDatabase::class.java, PeopleHubDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun providePeopleDao(database: PeopleHubDatabase): PeopleDao = database.peopleDao()

    @Provides
    fun provideCheckInDao(database: PeopleHubDatabase): CheckInDao = database.checkInDao()

    @Provides
    fun provideEventDao(database: PeopleHubDatabase): EventDao = database.eventDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(SETTINGS_STORE_NAME) },
        )

    private const val SETTINGS_STORE_NAME = "peoplehub_settings"
}

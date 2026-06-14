package com.peoplehub.core.database.di

import com.peoplehub.core.database.datastore.SettingsRepositoryImpl
import com.peoplehub.core.database.repository.BackupRepositoryImpl
import com.peoplehub.core.database.repository.CheckInRepositoryImpl
import com.peoplehub.core.database.repository.EventRepositoryImpl
import com.peoplehub.core.database.repository.PeopleRepositoryImpl
import com.peoplehub.core.domain.repository.BackupRepository
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.repository.EventRepository
import com.peoplehub.core.domain.repository.PeopleRepository
import com.peoplehub.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the domain repository interfaces to their Room/DataStore implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
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
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

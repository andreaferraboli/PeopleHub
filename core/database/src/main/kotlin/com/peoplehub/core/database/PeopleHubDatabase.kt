package com.peoplehub.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.peoplehub.core.database.dao.CheckInDao
import com.peoplehub.core.database.dao.EventDao
import com.peoplehub.core.database.dao.PeopleDao
import com.peoplehub.core.database.dao.ReminderDao
import com.peoplehub.core.database.entity.CheckInEntity
import com.peoplehub.core.database.entity.EventEntity
import com.peoplehub.core.database.entity.InterestEntity
import com.peoplehub.core.database.entity.PersonEntity
import com.peoplehub.core.database.entity.PersonFtsEntity
import com.peoplehub.core.database.entity.PersonTagEntity
import com.peoplehub.core.database.entity.ReminderCompletionEntity
import com.peoplehub.core.database.entity.ReminderEntity

/**
 * The single Room database for PeopleHub. Foreign keys are enabled in the DI builder so cascading
 * deletes (a person's tags/interests/check-ins) work as declared on the entities.
 */
@Database(
    entities = [
        PersonEntity::class,
        PersonTagEntity::class,
        InterestEntity::class,
        PersonFtsEntity::class,
        CheckInEntity::class,
        EventEntity::class,
        ReminderEntity::class,
        ReminderCompletionEntity::class,
    ],
    version = PeopleHubDatabase.VERSION,
    exportSchema = true,
)
abstract class PeopleHubDatabase : RoomDatabase() {
    abstract fun peopleDao(): PeopleDao

    abstract fun checkInDao(): CheckInDao

    abstract fun eventDao(): EventDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        const val VERSION: Int = 10
        const val NAME: String = "peoplehub.db"
    }
}

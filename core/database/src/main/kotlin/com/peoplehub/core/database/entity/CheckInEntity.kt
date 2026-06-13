package com.peoplehub.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room entity for a single recorded check-in (frequentation history). */
@Entity(
    tableName = "check_in",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("person_id")],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "timestamp_epoch_millis") val timestampEpochMillis: Long,
    @ColumnInfo(name = "note") val note: String?,
)

package com.peoplehub.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single recorded check-in (frequentation history).
 *
 * [outingId] is the grouping key of the outing the check-in belongs to: the rows written for the
 * several people seen on the same occasion share it, which is what lets the outings history render and
 * edit them as one card. It is indexed because every outing read looks rows up by it.
 */
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
    indices = [Index("person_id"), Index("outing_id")],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "timestamp_epoch_millis") val timestampEpochMillis: Long,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "outing_id", defaultValue = "0") val outingId: Long = 0L,
)

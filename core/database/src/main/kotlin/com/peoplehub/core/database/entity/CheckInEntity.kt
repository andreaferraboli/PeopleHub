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
 *
 * [outingRemoteId] (v11) is the cloud identity of that outing, shared by every row of it. Local code
 * leaves it `null` and a trigger (`SyncTriggers`) copies it from a sibling row with the same
 * [outingId], or draws a new UUID for a brand-new outing, so no write path has to know about it.
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
    indices = [
        Index("person_id"),
        Index("outing_id"),
        Index(value = ["remote_id"], unique = true),
        Index("outing_remote_id"),
    ],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "timestamp_epoch_millis") val timestampEpochMillis: Long,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "outing_id", defaultValue = "0") val outingId: Long = 0L,
    @ColumnInfo(name = "remote_id", defaultValue = "''") val remoteId: String = newRemoteId(),
    @ColumnInfo(name = "outing_remote_id") val outingRemoteId: String? = null,
)

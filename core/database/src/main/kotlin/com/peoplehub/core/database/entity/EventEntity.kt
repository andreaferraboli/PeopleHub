package com.peoplehub.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a personal event. The link to a person uses `SET_NULL` on delete so removing a
 * person leaves their events intact but unlinked.
 */
@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("person_id"), Index("pinned_to_widget")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "date_time_epoch_millis") val dateTimeEpochMillis: Long,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "person_id") val personId: Long?,
    @ColumnInfo(name = "pinned_to_widget") val pinnedToWidget: Boolean,
)

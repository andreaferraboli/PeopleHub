package com.peoplehub.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a per-person relationship reminder. The link to a person uses `CASCADE` on delete
 * so removing a person also removes their reminders (unlike events, which survive unlinked).
 *
 * Dates are stored as epoch millis and the [category] as the enum name, keeping mapping explicit and
 * TypeConverter-free. [nextFireEpochMillis] is indexed so the daily "what's due" sweep is cheap.
 */
@Entity(
    tableName = "reminder",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("person_id"), Index("next_fire_epoch_millis")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "target_interval_days") val targetIntervalDays: Int,
    @ColumnInfo(name = "jitter_percent") val jitterPercent: Int,
    @ColumnInfo(name = "enabled", defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(name = "last_fired_epoch_millis") val lastFiredEpochMillis: Long?,
    @ColumnInfo(name = "next_fire_epoch_millis") val nextFireEpochMillis: Long,
    @ColumnInfo(name = "created_epoch_millis") val createdEpochMillis: Long,
    @ColumnInfo(name = "preset_key") val presetKey: String?,
)

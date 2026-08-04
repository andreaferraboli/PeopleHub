package com.peoplehub.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one day on which the user ticked a reminder off as done.
 *
 * The log is what makes "done" durable: the reminder row itself only carries the *next* occurrence, so
 * without this table rescheduling would erase the fact that the gesture happened at all. One row per
 * (reminder, day) — the unique index makes ticking the same reminder twice in a day idempotent rather
 * than an error, which is what a card the user can tap repeatedly needs.
 *
 * The day is stored as an epoch day (not millis) because that is the granularity the feature is about:
 * "on that day it was done".
 */
@Entity(
    tableName = "reminder_completion",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["reminder_id", "done_epoch_day"], unique = true)],
)
data class ReminderCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "reminder_id") val reminderId: Long,
    @ColumnInfo(name = "done_epoch_day") val doneEpochDay: Long,
)

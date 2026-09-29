package com.peoplehub.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one time the user ticked a reminder off as done.
 *
 * The log is what makes "done" durable: the reminder row itself only carries the *next* occurrence, so
 * without this table rescheduling would erase the fact that the gesture happened at all. Since v10 it is
 * append-only with **one row per tap**: ticking the same reminder twice in a day writes two rows, so the
 * history keeps every moment the user said "done". Appending a row is a single `INSERT`, atomic on its
 * own, with no read-modify-write of a list.
 *
 * [doneEpochDay] is the local day of the tap, fixed when it was written (so a later time-zone change
 * never moves it to a different day), and is what "last done on" is computed from. [doneEpochMillis]
 * is the exact instant; it is `null` only for rows written before v10, which recorded the day alone.
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
    indices = [Index(value = ["reminder_id", "done_epoch_day"])],
)
data class ReminderCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "reminder_id") val reminderId: Long,
    @ColumnInfo(name = "done_epoch_day") val doneEpochDay: Long,
    @ColumnInfo(name = "done_epoch_millis") val doneEpochMillis: Long?,
)

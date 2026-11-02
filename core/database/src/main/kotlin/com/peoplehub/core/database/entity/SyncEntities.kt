package com.peoplehub.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One local change waiting to reach the cloud (v11): "document [remoteId] of [collection] changed at
 * [changedAt]". Rows are written only by the SQL triggers in `SyncTriggers`, never by app code, so
 * every write path (repositories, imports, cascades) is covered without knowing sync exists.
 *
 * The entry carries no payload and no "deleted" flag: the pusher reads the row's current state when
 * it runs, and a row that no longer exists becomes a tombstone. That makes repeated changes collapse
 * into one entry (the primary key) and makes ordering between a delete and a cascade irrelevant.
 * [changedAt] is the device clock in epoch millis; it becomes the document's `updatedAt` and is what a
 * remote change is compared against (last write wins).
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["collection", "remote_id"])
data class SyncOutboxEntity(
    @ColumnInfo(name = "collection") val collection: String,
    @ColumnInfo(name = "remote_id") val remoteId: String,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)

/**
 * Single-row switchboard read by the outbox triggers (v11). While the sync engine applies changes
 * that came from the cloud it sets [applyingRemote], and the triggers stay silent, so nothing that
 * arrived from the cloud is queued to be sent back to it.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "applying_remote", defaultValue = "0") val applyingRemote: Boolean = false,
) {
    companion object {
        /** The id of the only row. */
        const val SINGLE_ROW_ID: Int = 1
    }
}

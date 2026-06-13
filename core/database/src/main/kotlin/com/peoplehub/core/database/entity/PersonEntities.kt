package com.peoplehub.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Room entity for a person. Dates and timestamps are stored as primitives (ISO strings / epoch
 * millis) so no [androidx.room.TypeConverter]s are required and mapping stays explicit.
 */
@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "photo_path") val photoPath: String?,
    @ColumnInfo(name = "birthday") val birthday: String?,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "last_check_in_epoch_millis") val lastCheckInEpochMillis: Long?,
    @ColumnInfo(name = "warning_days") val warningDays: Int?,
    @ColumnInfo(name = "critical_days") val criticalDays: Int?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
)

/**
 * A tag attached to a person. Modelled as a child table (rather than a delimited string) so tags
 * can be filtered and enumerated with plain SQL.
 */
@Entity(
    tableName = "person_tag",
    primaryKeys = ["person_id", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("person_id"), Index("tag")],
)
data class PersonTagEntity(
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "tag") val tag: String,
)

/** A free-form interest key/value pair belonging to a person. */
@Entity(
    tableName = "interest",
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
data class InterestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "interest_key") val key: String,
    @ColumnInfo(name = "interest_value") val value: String,
)

/**
 * FTS4 index over a person's searchable text. Kept in sync manually by the repository on every
 * write, with [rowId] mirroring [PersonEntity.id] so the two tables can be joined.
 */
@Fts4
@Entity(tableName = "person_fts")
data class PersonFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "notes") val notes: String,
)

/**
 * Aggregate read model joining a person with their tags and interests in a single query.
 */
data class PersonWithDetails(
    @Embedded val person: PersonEntity,
    @Relation(parentColumn = "id", entityColumn = "person_id")
    val tags: List<PersonTagEntity>,
    @Relation(parentColumn = "id", entityColumn = "person_id")
    val interests: List<InterestEntity>,
)

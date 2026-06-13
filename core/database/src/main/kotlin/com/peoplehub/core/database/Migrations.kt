package com.peoplehub.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations for [PeopleHubDatabase].
 *
 * Version history:
 * - **v1** — initial schema without per-person check-in thresholds.
 * - **v2** — adds `warning_days` / `critical_days` to `person` so thresholds can be overridden per
 *   person (previously only a global default existed).
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE person ADD COLUMN warning_days INTEGER")
        db.execSQL("ALTER TABLE person ADD COLUMN critical_days INTEGER")
    }
}

/** All migrations registered with the database builder, in order. */
internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

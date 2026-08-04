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
 * - **v3** — adds `notifications_enabled` (per-person notification opt-in, defaulting to off/`0` for
 *   every existing profile) and `birthday_only` (entries that are bare birthdays, hidden from the
 *   directory) to `person`.
 * - **v4** — adds `background_image_path` to `event` (an optional image rendered behind the event
 *   card) and `check_in_disabled` to `person` (excludes the person from check-in tracking entirely,
 *   defaulting to off/`0`).
 * - **v5** — adds `is_family` to `person` (marks family members, who are exempt from the check-in
 *   frequency tracker and its reminders, defaulting to off/`0`).
 * - **v6** — adds `background_source_path` plus the `background_zoom` / `background_pan_x` /
 *   `background_pan_y` crop transform to `event`, so an event's background framing can be adjusted
 *   later from the untouched original. Existing rows keep a null source and the identity transform.
 * - **v7** — adds the `reminder` table: per-person relationship reminders that fire on a jittered
 *   (deliberately irregular) cadence, cascading on person delete.
 * - **v8** — adds `outing_id` to `check_in`, the grouping key that turns the per-person rows written
 *   for one occasion into a single editable outing. Existing rows are backfilled by grouping on
 *   (local day, description), which is exactly how a shared outing used to be recognisable.
 * - **v9** — adds `reminder_completion`, the log of the days each reminder was ticked off as done.
 *   Nothing to backfill: before it existed, doing a reminder left no trace beyond the reschedule.
 */
internal val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE person ADD COLUMN warning_days INTEGER")
            db.execSQL("ALTER TABLE person ADD COLUMN critical_days INTEGER")
        }
    }

internal val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE person ADD COLUMN notifications_enabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE person ADD COLUMN birthday_only INTEGER NOT NULL DEFAULT 0")
        }
    }

internal val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE event ADD COLUMN background_image_path TEXT")
            db.execSQL("ALTER TABLE person ADD COLUMN check_in_disabled INTEGER NOT NULL DEFAULT 0")
        }
    }

internal val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE person ADD COLUMN is_family INTEGER NOT NULL DEFAULT 0")
        }
    }

internal val MIGRATION_5_6: Migration =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE event ADD COLUMN background_source_path TEXT")
            db.execSQL("ALTER TABLE event ADD COLUMN background_zoom REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE event ADD COLUMN background_pan_x REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE event ADD COLUMN background_pan_y REAL NOT NULL DEFAULT 0.0")
        }
    }

internal val MIGRATION_6_7: Migration =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reminder` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`person_id` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`note` TEXT, " +
                    "`category` TEXT NOT NULL, " +
                    "`target_interval_days` INTEGER NOT NULL, " +
                    "`jitter_percent` INTEGER NOT NULL, " +
                    "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                    "`last_fired_epoch_millis` INTEGER, " +
                    "`next_fire_epoch_millis` INTEGER NOT NULL, " +
                    "`created_epoch_millis` INTEGER NOT NULL, " +
                    "`preset_key` TEXT, " +
                    "FOREIGN KEY(`person_id`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_person_id` ON `reminder` (`person_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminder_next_fire_epoch_millis` " +
                    "ON `reminder` (`next_fire_epoch_millis`)",
            )
        }
    }

internal val MIGRATION_7_8: Migration =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE check_in ADD COLUMN outing_id INTEGER NOT NULL DEFAULT 0")
            // Rows recorded together share a day and a description, so that pair reconstructs the
            // groups; the lowest row id of each group becomes its outing id (ids are never reused, so
            // `MAX(outing_id) + 1` keeps handing out fresh ones afterwards).
            db.execSQL(
                "UPDATE check_in SET outing_id = (" +
                    "SELECT MIN(other.id) FROM check_in AS other " +
                    "WHERE IFNULL(other.note, '') = IFNULL(check_in.note, '') " +
                    "AND strftime('%Y-%m-%d', other.timestamp_epoch_millis / 1000, 'unixepoch', 'localtime') = " +
                    "strftime('%Y-%m-%d', check_in.timestamp_epoch_millis / 1000, 'unixepoch', 'localtime'))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_check_in_outing_id` ON `check_in` (`outing_id`)")
        }
    }

internal val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reminder_completion` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`reminder_id` INTEGER NOT NULL, " +
                    "`done_epoch_day` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`reminder_id`) REFERENCES `reminder`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_reminder_completion_reminder_id_done_epoch_day` " +
                    "ON `reminder_completion` (`reminder_id`, `done_epoch_day`)",
            )
        }
    }

/** All migrations registered with the database builder, in order. */
internal val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
    )

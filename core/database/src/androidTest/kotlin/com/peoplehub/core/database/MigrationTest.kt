package com.peoplehub.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Runs the real migrations against databases created from the exported schemas, so a migration that
 * loses rows or leaves the table in a shape Room rejects fails here instead of on the user's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PeopleHubDatabase::class.java)

    @Test
    fun v9CompletionDaysSurviveAsHistoryAndASecondTapOnTheSameDayIsKept() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.of(2026, 9, 28)
        val lastFired =
            LocalDateTime
                .of(today, LocalTime.of(18, 42))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val olderDay = today.minusDays(18)

        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL(
                "INSERT INTO person (id, first_name, last_name, notes, created_at_epoch_millis) " +
                    "VALUES (1, 'Ada', 'Lovelace', '', 0)",
            )
            db.execSQL(
                "INSERT INTO reminder (id, person_id, title, category, target_interval_days, jitter_percent, " +
                    "enabled, last_fired_epoch_millis, next_fire_epoch_millis, created_epoch_millis) " +
                    "VALUES (1, 1, 'Call', 'CALL', 7, 20, 1, $lastFired, $lastFired, 0)",
            )
            db.execSQL(
                "INSERT INTO reminder_completion (reminder_id, done_epoch_day) " +
                    "VALUES (1, ${olderDay.toEpochDay()}), (1, ${today.toEpochDay()})",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10).use { db ->
            db.query("SELECT done_epoch_day, done_epoch_millis FROM reminder_completion ORDER BY done_epoch_day").use {
                assertEquals(2, it.count)
                it.moveToFirst()
                assertEquals(olderDay.toEpochDay(), it.getLong(0))
                assertTrue(it.isNull(1))
                it.moveToNext()
                assertEquals(today.toEpochDay(), it.getLong(0))
                assertEquals(lastFired, it.getLong(1))
            }

            db.execSQL(
                "INSERT INTO reminder_completion (reminder_id, done_epoch_day, done_epoch_millis) " +
                    "VALUES (1, ${today.toEpochDay()}, ${lastFired + 60_000})",
            )
            db.query("SELECT COUNT(*) FROM reminder_completion WHERE reminder_id = 1").use {
                it.moveToFirst()
                assertEquals(3, it.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}

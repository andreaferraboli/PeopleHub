package com.peoplehub.core.database

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the real v10 -> v11 SQL and the sync triggers on a plain SQLite database built from the
 * exported v10 schema, on the JVM. The on-device `MigrationTest` additionally checks the result
 * against Room's own v11 schema; this one checks behaviour: UUID backfill, one outing id per outing,
 * and what the triggers queue (or do not queue) in `sync_outbox`.
 */
class SyncMigrationSqlTest {
    private lateinit var db: Connection

    @BeforeEach
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("PRAGMA foreign_keys = ON")
        v10Schema().forEach(::exec)
        exec("INSERT INTO person (id, first_name, last_name, notes, created_at_epoch_millis) VALUES (1, 'Ada', 'L', '', 0)")
        exec("INSERT INTO person (id, first_name, last_name, notes, created_at_epoch_millis) VALUES (2, 'Bob', 'M', '', 0)")
        exec("INSERT INTO person_tag (person_id, tag) VALUES (1, 'friends')")
        exec("INSERT INTO check_in (id, person_id, timestamp_epoch_millis, note, outing_id) VALUES (1, 1, 10, 'x', 1)")
        exec("INSERT INTO check_in (id, person_id, timestamp_epoch_millis, note, outing_id) VALUES (2, 2, 10, 'x', 1)")
        exec("INSERT INTO check_in (id, person_id, timestamp_epoch_millis, note, outing_id) VALUES (3, 1, 50, NULL, 2)")
        exec(
            "INSERT INTO reminder (id, person_id, title, category, target_interval_days, jitter_percent, enabled, " +
                "next_fire_epoch_millis, created_epoch_millis) VALUES (1, 1, 'Call', 'CALL', 7, 20, 1, 0, 0)",
        )
        exec("INSERT INTO reminder_completion (id, reminder_id, done_epoch_day) VALUES (1, 1, 5)")
        exec("INSERT INTO event (id, title, date_time_epoch_millis, person_id, pinned_to_widget) VALUES (1, 'E', 0, 1, 0)")
        MIGRATION_10_11_SQL.forEach(::exec)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun everyRowGetsADistinctCanonicalUuidAndNothingIsQueued() {
        val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        for (table in listOf("person", "check_in", "event", "reminder", "reminder_completion")) {
            val ids = strings("SELECT remote_id FROM $table")
            assertTrue(ids.isNotEmpty())
            ids.forEach { assertTrue(uuid.matches(it), "$table: $it") }
            assertEquals(ids.size, ids.toSet().size)
        }
        assertEquals(0, count("SELECT COUNT(*) FROM sync_outbox"))
    }

    @Test
    fun rowsOfTheSameOutingShareOneOutingId() {
        val first = strings("SELECT outing_remote_id FROM check_in WHERE outing_id = 1")
        val second = strings("SELECT outing_remote_id FROM check_in WHERE outing_id = 2")
        assertEquals(1, first.toSet().size)
        assertNotEquals(first.first(), second.single())
    }

    @Test
    fun newCheckInsJoinTheirOutingsCloudIdOrDrawANewOne() {
        val outing1 = strings("SELECT outing_remote_id FROM check_in WHERE id = 1").single()
        exec("INSERT INTO check_in (id, person_id, timestamp_epoch_millis, outing_id, remote_id) VALUES (10, 2, 10, 1, 'r10')")
        exec("INSERT INTO check_in (id, person_id, timestamp_epoch_millis, outing_id, remote_id) VALUES (11, 2, 90, 7, 'r11')")
        assertEquals(outing1, strings("SELECT outing_remote_id FROM check_in WHERE id = 10").single())
        val fresh = strings("SELECT outing_remote_id FROM check_in WHERE id = 11").single()
        assertTrue(fresh.length == 36 && fresh != outing1)

        // Detaching a row into a new outing (outing id changed, cloud id cleared) draws a new one.
        exec("UPDATE check_in SET outing_id = 8, outing_remote_id = NULL WHERE id = 10")
        val detached = strings("SELECT outing_remote_id FROM check_in WHERE id = 10").single()
        assertTrue(detached != outing1 && detached != fresh)
    }

    @Test
    fun localWritesAreQueuedAndChildTablesMarkTheirPerson() {
        exec("INSERT INTO person_tag (person_id, tag) VALUES (2, 'work')")
        exec("UPDATE reminder SET enabled = 0")
        exec("UPDATE event SET pinned_to_widget = 1")
        assertEquals(listOf("people|1", "reminders|1"), queued())
    }

    @Test
    fun deletingAPersonQueuesTombstonesForEverythingThatCascades() {
        exec("DELETE FROM person WHERE id = 1")
        assertEquals(
            listOf("checkIns|2", "events|1", "people|1", "reminderCompletions|1", "reminders|1"),
            queued(),
        )
    }

    @Test
    fun changesAppliedFromTheCloudAreNotQueued() {
        exec("UPDATE sync_state SET applying_remote = 1")
        exec("DELETE FROM person")
        exec("INSERT INTO person (id, first_name, last_name, notes, created_at_epoch_millis, remote_id) VALUES (5, 'C', 'D', '', 0, 'x')")
        assertEquals(0, count("SELECT COUNT(*) FROM sync_outbox"))
    }

    /** "collection|number of entries", sorted. */
    private fun queued(): List<String> =
        strings("SELECT collection || '|' || COUNT(*) FROM sync_outbox GROUP BY collection ORDER BY collection")

    private fun exec(sql: String) {
        db.createStatement().use { it.execute(sql) }
    }

    private fun count(sql: String): Int =
        db.createStatement().use { st ->
            st.executeQuery(sql).use {
                it.next()
                it.getInt(1)
            }
        }

    private fun strings(sql: String): List<String> =
        db.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    /** The CREATE statements of the exported v10 schema, tables before their indices. */
    private fun v10Schema(): List<String> {
        val json = File(SCHEMA_DIR, "10.json").readText()
        val createSql = Regex("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"")
        val tables = Regex("\"tableName\": \"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toList()
        // Each entity block lists its table createSql followed by its indices; resolve ${TABLE_NAME} per block.
        val blocks = json.split("\"tableName\": \"").drop(1)
        return blocks.flatMapIndexed { index, block ->
            createSql.findAll(block).map { match ->
                match.groupValues[1].replace("\\\"", "\"").replace("\${TABLE_NAME}", tables[index])
            }
        }
    }

    private companion object {
        const val SCHEMA_DIR = "schemas/com.peoplehub.core.database.PeopleHubDatabase"
    }
}

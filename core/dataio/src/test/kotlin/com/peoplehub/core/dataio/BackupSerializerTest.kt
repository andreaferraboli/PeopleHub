package com.peoplehub.core.dataio

import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class BackupSerializerTest {
    private val serializer = BackupSerializer()

    @Test
    fun `encode then decode round-trips a full backup`() {
        val richPerson =
            Person(
                id = "1",
                firstName = "Ada",
                lastName = "Lovelace",
                photoPath = "/photos/ada.jpg",
                birthday = LocalDate.of(1815, 12, 10),
                tags = listOf("friend", "mentor"),
                interests =
                    listOf(
                        Interest(key = "Field", value = "Mathematics"),
                        Interest(key = "Hobby", value = "Poetry"),
                    ),
                notes = "First programmer",
                lastCheckInAt = Instant.ofEpochMilli(1_700_000_000_000L),
                checkInThreshold = CheckInThreshold(warningDays = 10, criticalDays = 20),
                createdAt = Instant.ofEpochMilli(1_600_000_000_000L),
            )
        val minimalPerson =
            Person(
                id = "2",
                firstName = "Bob",
                lastName = "Stone",
                createdAt = Instant.ofEpochMilli(1_650_000_000_000L),
            )
        val original =
            BackupData(
                schemaVersion = BackupData.CURRENT_SCHEMA_VERSION,
                people = listOf(richPerson, minimalPerson),
                checkIns =
                    listOf(
                        CheckIn(
                            id = "5",
                            personId = "1",
                            timestamp = Instant.ofEpochMilli(1_700_000_500_000L),
                            note = "Coffee",
                            endAt = Instant.ofEpochMilli(1_700_007_700_000L),
                        ),
                    ),
                events =
                    listOf(
                        PersonEvent(
                            id = "9",
                            title = "Gala",
                            dateTime = LocalDateTime.of(2026, 1, 15, 19, 30),
                            description = "Annual gala",
                            category = "Gala",
                            personId = "1",
                            pinnedToWidget = true,
                        ),
                    ),
            )

        val decoded = serializer.decode(serializer.encode(original))

        assertTrue(decoded.isSuccess, "decode should succeed for a round-tripped backup")
        assertEquals(original, decoded.getOrThrow())
    }

    @Test
    fun `decode rejects a newer schema version`() {
        val futureVersion = BackupData.CURRENT_SCHEMA_VERSION + 1
        val json =
            """
            { "schemaVersion": $futureVersion, "people": [], "checkIns": [], "events": [] }
            """.trimIndent()

        val result = serializer.decode(json)

        assertTrue(result.isFailure, "a newer schema version must be rejected")
    }

    @Test
    fun `decode tolerates unknown JSON fields`() {
        val json =
            """
            {
              "schemaVersion": ${BackupData.CURRENT_SCHEMA_VERSION},
              "people": [],
              "checkIns": [],
              "events": [],
              "exportedBy": "PeopleHub 9.9",
              "futureField": 42
            }
            """.trimIndent()

        val result = serializer.decode(json)

        assertTrue(result.isSuccess, "unknown fields should be ignored, not rejected")
        assertEquals(BackupData.CURRENT_SCHEMA_VERSION, result.getOrThrow().schemaVersion)
    }

    @Test
    fun `files written while ids were numbers still decode, ids as strings`() {
        val json =
            """
            {
              "schemaVersion": 1,
              "people": [{ "id": 4, "firstName": "Ada", "lastName": "L", "interests": [{ "key": "k", "value": "v", "id": 9 }] }],
              "checkIns": [{ "id": 11, "personId": 4, "timestampEpochMillis": 5, "outingId": 0 }],
              "events": [
                { "id": 2, "title": "Party", "dateTime": "2026-01-01T20:00:00", "personId": 4 },
                { "title": "Solo", "dateTime": "2026-01-02T20:00:00", "personId": null }
              ]
            }
            """.trimIndent()

        val data = serializer.decode(json).getOrThrow()

        assertEquals("4", data.people.single().id)
        assertEquals(listOf(Interest("k", "v")), data.people.single().interests)
        assertEquals(CheckIn(id = "11", personId = "4", timestamp = Instant.ofEpochMilli(5), outingId = "0"), data.checkIns.single())
        assertEquals(listOf("4", null), data.events.map { it.personId })
        assertEquals(listOf("2", ""), data.events.map { it.id })
    }
}

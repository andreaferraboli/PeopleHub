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
                id = 1L,
                firstName = "Ada",
                lastName = "Lovelace",
                photoPath = "/photos/ada.jpg",
                birthday = LocalDate.of(1815, 12, 10),
                tags = listOf("friend", "mentor"),
                interests =
                    listOf(
                        Interest(key = "Field", value = "Mathematics", id = 7L),
                        Interest(key = "Hobby", value = "Poetry", id = 8L),
                    ),
                notes = "First programmer",
                lastCheckInAt = Instant.ofEpochMilli(1_700_000_000_000L),
                checkInThreshold = CheckInThreshold(warningDays = 10, criticalDays = 20),
                createdAt = Instant.ofEpochMilli(1_600_000_000_000L),
            )
        val minimalPerson =
            Person(
                id = 2L,
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
                            id = 5L,
                            personId = 1L,
                            timestamp = Instant.ofEpochMilli(1_700_000_500_000L),
                            note = "Coffee",
                        ),
                    ),
                events =
                    listOf(
                        PersonEvent(
                            id = 9L,
                            title = "Gala",
                            dateTime = LocalDateTime.of(2026, 1, 15, 19, 30),
                            description = "Annual gala",
                            category = "Gala",
                            personId = 1L,
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
}

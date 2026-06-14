package com.peoplehub.core.dataio

import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class PersonJsonImporterTest {
    private val importer = PersonJsonImporter()

    private val existing =
        Person(
            id = 42L,
            firstName = "Ada",
            lastName = "Lovelace",
            photoPath = "/photos/ada.jpg",
            birthday = LocalDate.of(1815, 12, 10),
            tags = listOf("Pioneer"),
            interests = listOf(Interest(key = "Field", value = "Mathematics")),
            notes = "Original notes",
            lastCheckInAt = Instant.ofEpochMilli(1_000L),
            checkInThreshold = CheckInThreshold(warningDays = 7, criticalDays = 14),
            createdAt = Instant.ofEpochMilli(500L),
            notificationsEnabled = false,
            birthdayOnly = false,
        )

    @Test
    fun `parse forces a new id and defaults notifications to off`() {
        val json = """{"firstName":"Grace","lastName":"Hopper"}"""

        val person = importer.parse(json).getOrThrow()

        assertEquals(0L, person.id)
        assertEquals("Grace", person.firstName)
        assertFalse(person.notificationsEnabled)
        assertFalse(person.birthdayOnly)
    }

    @Test
    fun `merge keeps the id and preserves fields absent from the JSON`() {
        val json = """{"notes":"Updated notes","notificationsEnabled":true}"""

        val merged = importer.merge(json, existing).getOrThrow()

        // id is never changed
        assertEquals(42L, merged.id)
        // present keys overwrite
        assertEquals("Updated notes", merged.notes)
        assertTrue(merged.notificationsEnabled)
        // absent keys are kept
        assertEquals("Ada", merged.firstName)
        assertEquals("Lovelace", merged.lastName)
        assertEquals("/photos/ada.jpg", merged.photoPath)
        assertEquals(LocalDate.of(1815, 12, 10), merged.birthday)
        assertEquals(listOf("Pioneer"), merged.tags)
        assertEquals(listOf(Interest(key = "Field", value = "Mathematics")), merged.interests)
        assertEquals(CheckInThreshold(warningDays = 7, criticalDays = 14), merged.checkInThreshold)
        assertFalse(merged.birthdayOnly)
    }

    @Test
    fun `merge applies an explicit null to clear an optional field`() {
        val json = """{"photoPath":null}"""

        val merged = importer.merge(json, existing).getOrThrow()

        assertNull(merged.photoPath)
        // everything else stays
        assertEquals("Ada", merged.firstName)
    }

    @Test
    fun `merge overwrites collections and the birthday-only flag`() {
        val json = """{"tags":["Friend","Mentor"],"birthdayOnly":true,"birthday":"1900-01-01"}"""

        val merged = importer.merge(json, existing).getOrThrow()

        assertEquals(listOf("Friend", "Mentor"), merged.tags)
        assertTrue(merged.birthdayOnly)
        assertEquals(LocalDate.of(1900, 1, 1), merged.birthday)
    }

    @Test
    fun `merge fails on a malformed birthday`() {
        val json = """{"birthday":"not-a-date"}"""

        assertTrue(importer.merge(json, existing).isFailure)
    }
}

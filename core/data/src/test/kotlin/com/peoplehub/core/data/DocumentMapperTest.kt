package com.peoplehub.core.data

import com.peoplehub.core.data.cloud.CloudDoc
import com.peoplehub.core.data.local.EventMedia
import com.peoplehub.core.data.mapping.DocumentMapper
import com.peoplehub.core.data.mapping.SettingsMapper
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.CropTransform
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.domain.model.ReminderOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DocumentMapperTest {
    private val rome = ZoneId.of("Europe/Rome")

    private val person =
        Person(
            id = "p-1",
            firstName = "Ada",
            lastName = "Lovelace",
            photoPath = "/data/photos/ada.jpg",
            birthday = LocalDate.of(1815, 12, 10),
            tags = listOf("friends", "work"),
            interests = listOf(Interest("Food", "Sushi")),
            notes = "Maths",
            lastCheckInAt = Instant.ofEpochMilli(1_000L),
            checkInThreshold = CheckInThreshold(10, 20),
            createdAt = Instant.ofEpochMilli(5L),
            notificationsEnabled = true,
            checkInDisabled = true,
            isFamily = true,
        )

    @Test
    fun `a person document carries every contract field, hasPhoto instead of the local path`() {
        val doc = DocumentMapper.personToDoc(person)

        assertEquals(
            setOf(
                "firstName",
                "lastName",
                "birthday",
                "notes",
                "tags",
                "interests",
                "warningDays",
                "criticalDays",
                "notificationsEnabled",
                "birthdayOnly",
                "checkInDisabled",
                "isFamily",
                "createdAt",
                "lastCheckInAt",
                "hasPhoto",
            ),
            doc.keys,
        )
        assertEquals("1815-12-10", doc["birthday"])
        assertEquals(true, doc["hasPhoto"])
        assertEquals(listOf(mapOf("key" to "Food", "value" to "Sushi")), doc["interests"])
        assertFalse(doc.values.contains("/data/photos/ada.jpg"))
    }

    @Test
    fun `a person reads back with the local photo and the last-seen computed from check-ins`() {
        val doc = CloudDoc("p-1", DocumentMapper.personToDoc(person))
        val computed = Instant.ofEpochMilli(9_000L)

        val read = DocumentMapper.personFromDoc(doc, photoPath = "/data/photos/ada.jpg", lastCheckInAt = computed)

        assertEquals(person.copy(lastCheckInAt = computed), read)
    }

    @Test
    fun `a partial person written by the MCP reads with neutral defaults`() {
        val read =
            DocumentMapper.personFromDoc(
                CloudDoc("p-2", mapOf("firstName" to "Bo", "lastCheckInAt" to 7L, "warningDays" to 3, "updatedAt" to 42L)),
                photoPath = null,
                lastCheckInAt = null,
            )

        assertEquals("Bo", read.firstName)
        assertEquals("", read.lastName)
        assertNull(read.birthday)
        assertNull(read.checkInThreshold) // only one of the two thresholds
        assertEquals(Instant.ofEpochMilli(7L), read.lastCheckInAt) // stored copy when there are no check-ins
        assertEquals(Instant.ofEpochMilli(42L), read.createdAt)
        assertFalse(read.notificationsEnabled)
    }

    @Test
    fun `a check-in without person is skipped and one without outing is its own outing`() {
        assertNull(DocumentMapper.checkInFromDoc(CloudDoc("c-1", mapOf("at" to 1L))))

        val single = DocumentMapper.checkInFromDoc(CloudDoc("c-2", mapOf("personId" to "p-1", "at" to 1L, "note" to "Hi")))

        assertEquals(CheckIn(id = "c-2", personId = "p-1", timestamp = Instant.ofEpochMilli(1L), note = "Hi", outingId = "c-2"), single)
    }

    @Test
    fun `check-ins round trip`() {
        val checkIn = CheckIn(id = "c-3", personId = "p-1", timestamp = Instant.ofEpochMilli(77L), note = null, outingId = "o-1")

        assertEquals(checkIn, DocumentMapper.checkInFromDoc(CloudDoc("c-3", DocumentMapper.checkInToDoc(checkIn))))
    }

    @Test
    fun `an event keeps its background, crop and pin on the phone, the rest in the cloud`() {
        val event =
            PersonEvent(
                id = "e-1",
                title = "Lisbon",
                dateTime = LocalDateTime.of(2026, 11, 6, 9, 30),
                description = "Trip",
                category = "Travel",
                backgroundImagePath = "/bg.jpg",
                backgroundSourcePath = "/src.jpg",
                backgroundCrop = CropTransform(1.5f, 0.1f, -0.2f),
                personId = "p-1",
                pinnedToWidget = true,
            )
        val doc = DocumentMapper.eventToDoc(event, rome)

        assertEquals(setOf("title", "at", "description", "category", "personId"), doc.keys)
        val media = EventMedia("/bg.jpg", "/src.jpg", CropTransform(1.5f, 0.1f, -0.2f))
        assertEquals(event, DocumentMapper.eventFromDoc(CloudDoc("e-1", doc), media, pinned = true, zone = rome))
    }

    @Test
    fun `reminders round trip and fall back on missing fields`() {
        val reminder =
            Reminder(
                id = "r-1",
                personId = "p-1",
                title = "Flowers",
                category = ReminderCategory.LOVE,
                targetIntervalDays = 14,
                jitterPercent = 20,
                lastFiredAt = Instant.ofEpochMilli(3L),
                nextFireAt = Instant.ofEpochMilli(4L),
                createdAt = Instant.ofEpochMilli(1L),
                presetKey = "flowers",
            )
        assertEquals(reminder, DocumentMapper.reminderFromDoc(CloudDoc("r-1", DocumentMapper.reminderToDoc(reminder))))

        val sparse =
            DocumentMapper.reminderFromDoc(CloudDoc("r-2", mapOf("personId" to "p-1", "category" to "WHATEVER", "createdAt" to 0L)))
        assertEquals(ReminderCategory.CUSTOM, sparse?.category)
        assertEquals(Instant.ofEpochMilli(30L * 86_400_000L), sparse?.nextFireAt)
        assertTrue(sparse?.enabled == true)
        assertNull(DocumentMapper.reminderFromDoc(CloudDoc("r-3", mapOf("title" to "No person"))))
    }

    @Test
    fun `a completion keeps the day and the time`() {
        val at = Instant.parse("2026-07-20T18:42:00Z")
        val doc = DocumentMapper.completionToDoc("r-1", at, LocalDate.of(2026, 7, 20))

        val read = DocumentMapper.completionFromDoc(CloudDoc("x", doc))

        assertEquals("r-1", read?.reminderId)
        assertEquals(LocalDate.of(2026, 7, 20), read?.entry?.day)
        assertEquals(at, read?.entry?.at)
    }

    @Test
    fun `settings read from the cloud are clamped and unknown offsets ignored`() {
        val settings =
            SettingsMapper.fromDoc(
                mapOf("warningDays" to 0, "criticalDays" to 0, "birthdayOffsets" to listOf(0L, 7L, 5L), "dailyReminderHour" to 30),
            )

        assertEquals(CheckInThreshold(1, 2), settings.threshold)
        assertEquals(setOf(ReminderOffset.SAME_DAY, ReminderOffset.ONE_WEEK), settings.offsets)
        assertEquals(23, settings.dailyReminderHour)
        assertEquals(5, SettingsMapper.fromDoc(null).dailyReminderHour)
    }
}

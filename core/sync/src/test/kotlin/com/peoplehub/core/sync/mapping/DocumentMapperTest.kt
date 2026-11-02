package com.peoplehub.core.sync.mapping

import com.peoplehub.core.database.entity.CheckInEntity
import com.peoplehub.core.database.entity.EventEntity
import com.peoplehub.core.database.entity.InterestEntity
import com.peoplehub.core.database.entity.PersonEntity
import com.peoplehub.core.database.entity.PersonTagEntity
import com.peoplehub.core.database.entity.PersonWithDetails
import com.peoplehub.core.database.entity.ReminderCompletionEntity
import com.peoplehub.core.database.entity.ReminderEntity
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.ReminderOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DocumentMapperTest {
    private val person =
        PersonEntity(
            id = 7L,
            firstName = "Ada",
            lastName = "Lovelace",
            photoPath = "/data/photo.jpg",
            birthday = "1815-12-10",
            notes = "Maths",
            lastCheckInEpochMillis = 1_000L,
            warningDays = 10,
            criticalDays = 20,
            createdAtEpochMillis = 5L,
            notificationsEnabled = true,
            birthdayOnly = false,
            checkInDisabled = true,
            isFamily = true,
            remoteId = "p-uuid",
        )

    @Test
    fun aPersonDocumentCarriesEveryContractFieldAndNoLocalPath() {
        val doc =
            DocumentMapper.personToDoc(
                PersonWithDetails(
                    person = person,
                    tags = listOf(PersonTagEntity(7L, "friends"), PersonTagEntity(7L, "work")),
                    interests = listOf(InterestEntity(1L, 7L, "Food", "Sushi")),
                ),
            )

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
        assertEquals(listOf("friends", "work"), doc["tags"])
        assertEquals(listOf(mapOf("key" to "Food", "value" to "Sushi")), doc["interests"])
        assertEquals(true, doc["hasPhoto"])
        assertEquals("1815-12-10", doc["birthday"])
    }

    @Test
    fun aPersonRoundTripsAndKeepsThePhoneOnlyFields() {
        val doc =
            DocumentMapper.personToDoc(PersonWithDetails(person, listOf(PersonTagEntity(7L, "friends")), emptyList())) +
                // Firestore hands numbers back as Long or Double whatever was written.
                mapOf("warningDays" to 10.0, "createdAt" to 5.0)

        val rows = DocumentMapper.personFromDoc("p-uuid", doc, existing = person)

        assertEquals(person, rows.person)
        assertEquals(listOf("friends"), rows.tags)
        assertEquals(listOf(PersonTagEntity(7L, "friends")), rows.tagEntities(7L))
    }

    @Test
    fun aMinimalPersonFromTheMcpGetsNeutralDefaults() {
        val rows = DocumentMapper.personFromDoc("new", mapOf("firstName" to "Bob", "updatedAt" to 42L), existing = null)

        assertEquals(0L, rows.person.id)
        assertEquals("Bob", rows.person.firstName)
        assertEquals("", rows.person.lastName)
        assertEquals(42L, rows.person.createdAtEpochMillis)
        assertNull(rows.person.photoPath)
        assertEquals("new", rows.person.remoteId)
    }

    @Test
    fun aCheckInTravelsWithItsPersonAndOutingCloudIds() {
        val row =
            CheckInEntity(
                id = 3L,
                personId = 7L,
                timestampEpochMillis = 99L,
                note = "Pizza",
                outingId = 4L,
                remoteId = "c",
                outingRemoteId = "o",
            )

        val doc = DocumentMapper.checkInToDoc(row, personRemoteId = "p-uuid")
        val back = DocumentMapper.checkInFromDoc("c", doc, existing = row, personId = 7L, outingId = 4L, outingRemoteId = "o")

        assertEquals(mapOf("personId" to "p-uuid", "at" to 99L, "note" to "Pizza", "outingId" to "o"), doc)
        assertEquals(row, back)
    }

    @Test
    fun anEventKeepsItsBackgroundAndWidgetPinWhichNeverLeaveThePhone() {
        val row =
            EventEntity(
                id = 2L,
                title = "Wedding",
                dateTimeEpochMillis = 1_000L,
                description = null,
                category = "Family",
                backgroundImagePath = "/bg.jpg",
                backgroundZoom = 2f,
                personId = 7L,
                pinnedToWidget = true,
                remoteId = "e",
            )

        val doc = DocumentMapper.eventToDoc(row, personRemoteId = "p-uuid")
        val back = DocumentMapper.eventFromDoc("e", doc + ("title" to "Wedding!"), existing = row, personId = 7L)

        assertEquals(setOf("title", "at", "description", "category", "personId"), doc.keys)
        assertEquals(row.copy(title = "Wedding!"), back)
    }

    @Test
    fun aReminderRoundTripsAndAMinimalOneGetsAFireTime() {
        val row =
            ReminderEntity(
                id = 1L,
                personId = 7L,
                title = "Call",
                note = null,
                category = "CALL",
                targetIntervalDays = 14,
                jitterPercent = 20,
                enabled = false,
                lastFiredEpochMillis = 50L,
                nextFireEpochMillis = 60L,
                createdEpochMillis = 40L,
                presetKey = "call",
                remoteId = "r",
            )
        assertEquals(row, DocumentMapper.reminderFromDoc("r", DocumentMapper.reminderToDoc(row, "p-uuid"), existing = row, personId = 7L))

        val minimal =
            DocumentMapper.reminderFromDoc(
                "r2",
                mapOf("title" to "Write", "targetIntervalDays" to 2L, "createdAt" to 0L),
                null,
                7L,
            )
        assertEquals(2 * 86_400_000L, minimal.nextFireEpochMillis)
        assertEquals("CUSTOM", minimal.category)
        assertEquals(true, minimal.enabled)
    }

    @Test
    fun aCompletionRoundTrips() {
        val row = ReminderCompletionEntity(id = 9L, reminderId = 1L, doneEpochDay = 20_000L, doneEpochMillis = null, remoteId = "d")

        val doc = DocumentMapper.completionToDoc(row, "r")

        assertEquals(mapOf("reminderId" to "r", "day" to 20_000L, "at" to null), doc)
        assertEquals(row, DocumentMapper.completionFromDoc("d", doc, existing = row, reminderId = 1L))
    }

    @Test
    fun settingsTravelAsDayCountsAndInvalidCloudValuesAreClamped() {
        val local = SettingsMapper.fromApp(AppSettings(), "Europe/Rome")
        assertEquals(listOf(0, 1, 7), local.birthdayOffsets)
        assertEquals("Europe/Rome", local.toDoc()["timeZone"])

        val incoming =
            SettingsMapper.fromDoc(
                mapOf("warningDays" to 30L, "criticalDays" to 10L, "birthdayOffsets" to listOf(3L, 30L, 2L), "dailyReminderHour" to 40L),
                fallback = local,
            )

        assertEquals(30, incoming.warningDays)
        assertEquals(31, incoming.criticalDays)
        assertEquals(23, incoming.dailyReminderHour)
        assertEquals(setOf(ReminderOffset.THREE_DAYS, ReminderOffset.ONE_MONTH), incoming.offsets)
    }
}

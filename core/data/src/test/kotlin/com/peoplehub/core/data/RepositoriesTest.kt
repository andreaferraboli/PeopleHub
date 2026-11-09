package com.peoplehub.core.data

import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.mapping.DocumentMapper
import com.peoplehub.core.data.repository.BackupRepositoryImpl
import com.peoplehub.core.data.repository.CheckInRepositoryImpl
import com.peoplehub.core.data.repository.CloudReads
import com.peoplehub.core.data.repository.EventRepositoryImpl
import com.peoplehub.core.data.repository.PeopleRepositoryImpl
import com.peoplehub.core.data.repository.ReminderRepositoryImpl
import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class RepositoriesTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-10T09:00:00Z"), ZoneOffset.UTC)
    private val store = FakeCloudStore()
    private val media = FakeLocalMedia()
    private val reads = CloudReads(store, media)
    private val people = PeopleRepositoryImpl(store, reads, media, clock)
    private val checkIns = CheckInRepositoryImpl(store, reads, clock)
    private val events = EventRepositoryImpl(store, media, clock)
    private val reminders = ReminderRepositoryImpl(store)
    private val backup = BackupRepositoryImpl(store, reads, events, media, clock)

    private fun seedPerson(id: String, first: String, extra: Map<String, Any?> = emptyMap()) =
        store.seed(Cloud.PEOPLE, id, mapOf("firstName" to first, "lastName" to "") + extra)

    private fun seedCheckIn(id: String, personId: String, at: Long, outingId: String = id) =
        store.seed(Cloud.CHECK_INS, id, mapOf("personId" to personId, "at" to at, "outingId" to outingId))

    @Test
    fun `last seen comes from the newest check-in, the stored copy only without any`() =
        runTest {
            seedPerson("a", "Ada", mapOf("lastCheckInAt" to 1L))
            seedPerson("b", "Bo", mapOf("lastCheckInAt" to 5L))
            seedCheckIn("c1", "a", 100L)
            seedCheckIn("c2", "a", 300L)

            val byId = people.getAllPeople().associateBy { it.id }

            assertEquals(Instant.ofEpochMilli(300L), byId.getValue("a").lastCheckInAt)
            assertEquals(Instant.ofEpochMilli(5L), byId.getValue("b").lastCheckInAt)
        }

    @Test
    fun `a new person gets a uuid, its creation time and its photo in the local map`() =
        runTest {
            val id = people.upsertPerson(Person(firstName = "Ada", lastName = "L", photoPath = "/photo.jpg"))

            assertEquals(36, id.length)
            val doc = store.live(Cloud.PEOPLE).getValue(id)
            assertEquals(true, doc["hasPhoto"])
            assertEquals(clock.millis(), doc["createdAt"])
            assertEquals("/photo.jpg", people.getPerson(id)?.photoPath)
        }

    @Test
    fun `deleting a person tombstones their check-ins, reminders and history and unlinks events`() =
        runTest {
            seedPerson("a", "Ada")
            seedPerson("b", "Bo")
            seedCheckIn("c1", "a", 1L, outingId = "o")
            seedCheckIn("c2", "b", 1L, outingId = "o")
            store.seed(Cloud.REMINDERS, "r1", mapOf("personId" to "a", "title" to "Call"))
            store.seed(Cloud.REMINDERS, "r2", mapOf("personId" to "b", "title" to "Write"))
            store.seed(Cloud.REMINDER_COMPLETIONS, "d1", mapOf("reminderId" to "r1", "day" to 1L))
            store.seed(Cloud.REMINDER_COMPLETIONS, "d2", mapOf("reminderId" to "r2", "day" to 1L))
            store.seed(Cloud.EVENTS, "e1", mapOf("title" to "Party", "at" to 0L, "personId" to "a"))
            media.setPersonPhoto("a", "/a.jpg")

            people.deletePerson("a")

            assertEquals(setOf("a"), store.tombstoned(Cloud.PEOPLE))
            assertEquals(setOf("c1"), store.tombstoned(Cloud.CHECK_INS))
            assertEquals(setOf("r1"), store.tombstoned(Cloud.REMINDERS))
            assertEquals(setOf("d1"), store.tombstoned(Cloud.REMINDER_COMPLETIONS))
            assertNull(store.live(Cloud.EVENTS).getValue("e1")["personId"])
            assertFalse("a" in media.personPhotos.value)
            // The other attendee keeps the shared outing.
            assertEquals(
                listOf("b"),
                checkIns
                    .observeOutings()
                    .first()
                    .single()
                    .attendees
                    .map { it.personId },
            )
        }

    @Test
    fun `delete all tombstones every person with the same cascade`() =
        runTest {
            seedPerson("a", "Ada")
            seedPerson("b", "Bo")
            seedCheckIn("c1", "a", 1L)
            store.seed(Cloud.REMINDERS, "r1", mapOf("personId" to "b"))

            people.deleteAll()

            assertTrue(store.live(Cloud.PEOPLE).isEmpty())
            assertTrue(store.live(Cloud.CHECK_INS).isEmpty())
            assertTrue(store.live(Cloud.REMINDERS).isEmpty())
        }

    @Test
    fun `bulk changes and last-seen updates never revive a deleted person`() =
        runTest {
            seedPerson("a", "Ada")
            store.seed(Cloud.PEOPLE, "gone", mapOf("firstName" to "Gone"), deleted = true)

            people.bulkSetCheckInThreshold(listOf("a", "gone"), CheckInThreshold(3, 9))
            people.updateLastCheckIn("gone", 5L)

            assertEquals(3, store.live(Cloud.PEOPLE).getValue("a")["warningDays"])
            assertEquals(false, store.live(Cloud.PEOPLE).getValue("a")["checkInDisabled"])
            assertTrue(store.stored(Cloud.PEOPLE, "gone")!!.deleted)
        }

    @Test
    fun `search runs in memory over the live people`() =
        runTest {
            seedPerson("a", "Ada", mapOf("notes" to "Loves sushi"))
            seedPerson("b", "Bo")
            store.seed(Cloud.PEOPLE, "c", mapOf("firstName" to "Sushi fan"), deleted = true)

            assertEquals(listOf("a"), people.observePeople(PeopleFilter(query = "sushi")).first().map { it.id })
        }

    @Test
    fun `outings group check-ins by outing id and drop people who no longer exist`() =
        runTest {
            seedPerson("a", "Ada")
            seedPerson("b", "Bo")
            seedCheckIn("c1", "a", 1_000L, outingId = "o1")
            seedCheckIn("c2", "b", 1_000L, outingId = "o1")
            seedCheckIn("c3", "a", 5_000L, outingId = "o2")
            seedCheckIn("c4", "ghost", 9_000L, outingId = "o3")

            val outings = checkIns.observeOutings().first()

            assertEquals(listOf("o2", "o1"), outings.map { it.id })
            assertEquals(setOf("c1", "c2"), outings.last().checkInIds.toSet())
            assertEquals(Instant.ofEpochMilli(5_000L), checkIns.latestTimestamp("a"))
        }

    @Test
    fun `an outing carries the end its check-ins share`() =
        runTest {
            seedPerson("a", "Ada")
            seedPerson("b", "Bo")
            store.seed(Cloud.CHECK_INS, "c1", mapOf("personId" to "a", "at" to 1_000L, "outingId" to "o1", "endAt" to 4_000L))
            store.seed(Cloud.CHECK_INS, "c2", mapOf("personId" to "b", "at" to 1_000L, "outingId" to "o1", "endAt" to 4_000L))
            seedCheckIn("c3", "a", 5_000L, outingId = "o2")

            val outings = checkIns.observeOutings().first().associateBy { it.id }

            assertEquals(Instant.ofEpochMilli(4_000L), outings.getValue("o1").endAt)
            assertNull(outings.getValue("o2").endAt)
        }

    @Test
    fun `a recorded check-in gets ids and deleting it writes a tombstone`() =
        runTest {
            seedPerson("a", "Ada")
            val id = checkIns.recordCheckIn(CheckIn(personId = "a", timestamp = Instant.ofEpochMilli(10L), outingId = "o"))

            assertEquals("o", store.live(Cloud.CHECK_INS).getValue(id)["outingId"])
            checkIns.deleteCheckIns(listOf(id))
            assertEquals(setOf(id), store.tombstoned(Cloud.CHECK_INS))
            assertTrue(checkIns.observeHistory("a").first().isEmpty())
        }

    @Test
    fun `deleting a reminder tombstones its completion history`() =
        runTest {
            seedPerson("a", "Ada")
            store.seed(Cloud.REMINDERS, "r1", mapOf("personId" to "a", "title" to "Call", "enabled" to true, "nextFireAt" to 0L))
            reminders.recordCompletion("r1", Instant.ofEpochMilli(5L), LocalDate.ofEpochDay(0))
            assertEquals(
                1,
                reminders
                    .observeCompletions()
                    .first()
                    .getValue("r1")
                    .timesDone,
            )

            reminders.deleteReminder("r1")

            assertTrue(store.live(Cloud.REMINDERS).isEmpty())
            assertTrue(store.live(Cloud.REMINDER_COMPLETIONS).isEmpty())
            reminders.setEnabled("r1", false)
            assertTrue(store.stored(Cloud.REMINDERS, "r1")!!.deleted)
        }

    @Test
    fun `a completion can be moved and deleted, and a tombstoned one is never revived`() =
        runTest {
            seedPerson("a", "Ada")
            store.seed(Cloud.REMINDERS, "r1", mapOf("personId" to "a", "title" to "Call", "enabled" to true, "nextFireAt" to 0L))
            store.seed(Cloud.REMINDER_COMPLETIONS, "c1", mapOf("reminderId" to "r1", "day" to 0L, "at" to 5L, "note" to "From Claude"))
            store.seed(Cloud.REMINDER_COMPLETIONS, "c2", mapOf("reminderId" to "r1", "day" to 2L, "at" to 2 * 86_400_000L))

            assertEquals(listOf("c2", "c1"), reminders.getCompletions("r1").map { it.id })
            assertEquals("From Claude", reminders.getCompletions("r1").last().note)

            val moved = Instant.ofEpochMilli(3 * 86_400_000L + 60_000L)
            reminders.updateCompletion("c1", moved, LocalDate.ofEpochDay(3), note = "From Claude")
            assertEquals(3L, store.live(Cloud.REMINDER_COMPLETIONS).getValue("c1")["day"])
            assertEquals(moved.toEpochMilli(), store.live(Cloud.REMINDER_COMPLETIONS).getValue("c1")["at"])
            assertEquals("r1", store.live(Cloud.REMINDER_COMPLETIONS).getValue("c1")["reminderId"])
            assertEquals("From Claude", store.live(Cloud.REMINDER_COMPLETIONS).getValue("c1")["note"])
            assertEquals(listOf("c1", "c2"), reminders.getCompletions("r1").map { it.id })
            reminders.updateCompletion("c1", moved, LocalDate.ofEpochDay(3), note = null)
            assertTrue(store.live(Cloud.REMINDER_COMPLETIONS).getValue("c1").containsKey("note"))
            assertNull(store.live(Cloud.REMINDER_COMPLETIONS).getValue("c1")["note"])

            reminders.deleteCompletion("c2")
            assertEquals(setOf("c2"), store.tombstoned(Cloud.REMINDER_COMPLETIONS))
            reminders.deleteCompletion("ghost")
            assertNull(store.stored(Cloud.REMINDER_COMPLETIONS, "ghost"))
            reminders.updateCompletion("c2", moved, LocalDate.ofEpochDay(3), note = null)
            assertTrue(store.stored(Cloud.REMINDER_COMPLETIONS, "c2")!!.deleted)

            reminders.markFired("r1", Instant.ofEpochMilli(7L), Instant.ofEpochMilli(9L))
            reminders.reschedule("r1", Instant.ofEpochMilli(42L))
            assertEquals(42L, store.live(Cloud.REMINDERS).getValue("r1")["nextFireAt"])
            assertEquals(7L, store.live(Cloud.REMINDERS).getValue("r1")["lastFiredAt"])
            assertEquals("Call", store.live(Cloud.REMINDERS).getValue("r1")["title"])
            reminders.reschedule("r1", Instant.ofEpochMilli(43L), clearLastFired = true)
            assertNull(store.live(Cloud.REMINDERS).getValue("r1")["lastFiredAt"])
        }

    @Test
    fun `the done button never drops a note written by the MCP`() {
        val doc = DocumentMapper.completionToDoc("r1", Instant.ofEpochMilli(5L), LocalDate.ofEpochDay(0))

        assertFalse(doc.containsKey("note"))
        assertEquals("x", DocumentMapper.completionToDoc("r1", Instant.ofEpochMilli(5L), LocalDate.ofEpochDay(0), "x")["note"])
    }

    @Test
    fun `due reminders carry the person name and skip people that are gone`() =
        runTest {
            seedPerson("a", "Ada", mapOf("lastName" to "Lovelace"))
            store.seed(Cloud.REMINDERS, "r1", mapOf("personId" to "a", "title" to "Call", "enabled" to true, "nextFireAt" to 0L))
            store.seed(Cloud.REMINDERS, "r2", mapOf("personId" to "ghost", "title" to "x", "enabled" to true, "nextFireAt" to 0L))
            store.seed(Cloud.REMINDERS, "r3", mapOf("personId" to "a", "title" to "Later", "enabled" to true, "nextFireAt" to 99_999L))

            val due = reminders.getDueReminders(Instant.ofEpochMilli(1_000L))

            assertEquals(listOf("r1"), due.map { it.reminderId })
            assertEquals("Ada Lovelace", due.single().personName)
        }

    @Test
    fun `pinning an event is local and exclusive`() =
        runTest {
            store.seed(Cloud.EVENTS, "e1", mapOf("title" to "One", "at" to 0L))
            store.seed(Cloud.EVENTS, "e2", mapOf("title" to "Two", "at" to 0L))

            events.setPinned("e1", true)
            events.setPinned("e2", true)

            assertEquals("e2", events.observePinnedEvent().first()?.id)
            assertTrue(store.commits.isEmpty())
            events.deleteEvent("e2")
            assertNull(events.observePinnedEvent().first())
        }

    @Test
    fun `replace import tombstones the current data and writes the file with fresh ids`() =
        runTest {
            seedPerson("old", "Old")
            seedCheckIn("oc", "old", 1L)
            store.seed(Cloud.REMINDERS, "or", mapOf("personId" to "old"))
            val data =
                BackupData(
                    schemaVersion = 1,
                    people = listOf(Person(id = "7", firstName = "Ada", lastName = "L")),
                    checkIns =
                        listOf(
                            CheckIn(id = "1", personId = "7", timestamp = Instant.parse("2026-05-01T12:00:00Z"), note = "Trip"),
                            CheckIn(
                                id = "2",
                                personId = "7",
                                timestamp = Instant.parse("2026-05-01T18:00:00Z"),
                                note = "Trip",
                                outingId = "0",
                            ),
                        ),
                    events = listOf(PersonEvent(id = "3", title = "Party", dateTime = LocalDateTime.of(2026, 1, 1, 20, 0), personId = "7")),
                )

            backup.importReplace(data)

            assertEquals(setOf("old"), store.tombstoned(Cloud.PEOPLE))
            assertEquals(setOf("or"), store.tombstoned(Cloud.REMINDERS))
            val person = store.live(Cloud.PEOPLE).keys.single()
            val newCheckIns = store.live(Cloud.CHECK_INS).values
            assertTrue(newCheckIns.all { it["personId"] == person })
            assertEquals(1, newCheckIns.map { it["outingId"] }.distinct().size) // legacy rows regrouped by day + note
            assertEquals(person, store.live(Cloud.EVENTS).values.single()["personId"])
        }

    @Test
    fun `merge import skips people already there`() =
        runTest {
            seedPerson("a", "Ada", mapOf("lastName" to "L"))
            val incoming = listOf(Person(id = "1", firstName = "ada", lastName = "l"), Person(id = "2", firstName = "Bo", lastName = ""))
            val data = BackupData(1, incoming, emptyList(), emptyList())

            val report = backup.importMerge(data)

            assertEquals(1, report.peopleAdded)
            assertEquals(1, report.peopleSkipped)
            assertEquals(2, store.live(Cloud.PEOPLE).size)
        }

    @Test
    fun `writes are cut into batches the size Firestore accepts`() {
        assertEquals(400, CloudStore.MAX_BATCH_WRITES)
    }
}

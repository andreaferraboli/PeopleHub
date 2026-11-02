package com.peoplehub.core.sync.engine

import com.peoplehub.core.database.sync.SyncCollections.CHECK_INS
import com.peoplehub.core.database.sync.SyncCollections.EVENTS
import com.peoplehub.core.database.sync.SyncCollections.PEOPLE
import com.peoplehub.core.database.sync.SyncCollections.REMINDERS
import com.peoplehub.core.database.sync.SyncCollections.REMINDER_COMPLETIONS
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PullerTest {
    private val remote = FakeRemoteStore()
    private val local = FakeLocalStore()
    private val cursors = FakeCursors()
    private val puller = Puller(remote, local, cursors, ownTag = OWN, maxDeferrals = 2)

    private fun live(vararg fields: Pair<String, Any?>, updatedAt: Long = 100L, by: String = "mcp") =
        mapOf(*fields, "updatedAt" to updatedAt, "deleted" to false, "updatedBy" to by)

    @Test
    fun parentsAreAppliedBeforeChildrenWhateverOrderTheyWereWritten() =
        runTest {
            remote.put(REMINDER_COMPLETIONS, "done1", live("reminderId" to "r1"))
            remote.put(CHECK_INS, "c1", live("personId" to "p1"))
            remote.put(EVENTS, "e1", live("personId" to "p1"))
            remote.put(REMINDERS, "r1", live("personId" to "p1"))
            remote.put(PEOPLE, "p1", live("firstName" to "Ada"))

            val report = puller.pull(UID)

            assertEquals(5, report.applied)
            assertEquals(0, report.deferred)
            assertEquals(listOf("people/p1", "reminders/r1", "events/e1", "checkIns/c1", "reminderCompletions/done1"), local.applyLog)
        }

    @Test
    fun anOrphanIsDeferredWithoutMovingTheCursorPastItThenAppliedOnceItsParentArrives() =
        runTest {
            val before = remote.put(CHECK_INS, "c0", live("personId" to "known"))
            remote.put(PEOPLE, "known", live("firstName" to "K"))
            val orphan = remote.put(CHECK_INS, "c1", live("personId" to "p1"))
            remote.put(CHECK_INS, "c2", live("personId" to "known"))

            val first = puller.pull(UID)

            assertEquals(1, first.deferred)
            assertNull(local.row(CHECK_INS, "c1"))
            assertNotNull(local.row(CHECK_INS, "c2"))
            assertEquals(orphan.serverUpdatedMicros - 1, cursors.values[CHECK_INS])
            assertTrue(cursors.values.getValue(CHECK_INS) >= before.serverUpdatedMicros)

            remote.put(PEOPLE, "p1", live("firstName" to "Late"))
            val second = puller.pull(UID)

            assertEquals(0, second.deferred)
            assertNotNull(local.row(CHECK_INS, "c1"))
            assertEquals(
                remote.docs
                    .getValue(CHECK_INS)
                    .values
                    .maxOf { it.serverUpdatedMicros },
                cursors.values[CHECK_INS],
            )
        }

    @Test
    fun anOrphanWhoseParentNeverComesIsDroppedAfterTheLastRoundAndTheCursorMovesOn() =
        runTest {
            val orphan = remote.put(REMINDERS, "r1", live("personId" to "ghost"))

            repeat(2) { assertEquals(1, puller.pull(UID).deferred) }
            val last = puller.pull(UID)

            assertEquals(1, last.dropped)
            assertNull(local.row(REMINDERS, "r1"))
            assertEquals(orphan.serverUpdatedMicros, cursors.values[REMINDERS])
        }

    @Test
    fun anEventWhosePersonNeverComesIsKeptUnlinkedInsteadOfDropped() =
        runTest {
            remote.put(EVENTS, "e1", live("title" to "Trip", "personId" to "ghost"))

            repeat(3) { puller.pull(UID) }

            assertEquals(null, local.row(EVENTS, "e1")?.get("personId"))
            assertEquals("Trip", local.row(EVENTS, "e1")?.get("title"))
        }

    @Test
    fun ourOwnWritesComingBackAreSkippedButStillMoveTheCursor() =
        runTest {
            val echo = remote.put(PEOPLE, "p1", live("firstName" to "Mine", by = OWN))

            val report = puller.pull(UID)

            assertEquals(0, report.applied)
            assertNull(local.row(PEOPLE, "p1"))
            assertEquals(echo.serverUpdatedMicros, cursors.values[PEOPLE])
        }

    @Test
    fun aNewerPendingLocalChangeWinsOverAnOlderCloudVersion() =
        runTest {
            local.localWrite(PEOPLE, "p1", mapOf("firstName" to "Local"), at = 500L)
            remote.put(PEOPLE, "p1", live("firstName" to "Cloud", updatedAt = 400L))

            puller.pull(UID)

            assertEquals("Local", local.row(PEOPLE, "p1")?.get("firstName"))
            assertEquals(500L, local.outbox[PEOPLE to "p1"])
        }

    @Test
    fun aNewerCloudVersionWinsAndDropsThePendingLocalChange() =
        runTest {
            local.localWrite(PEOPLE, "p1", mapOf("firstName" to "Local"), at = 500L)
            remote.put(PEOPLE, "p1", live("firstName" to "Cloud", updatedAt = 600L))

            puller.pull(UID)

            assertEquals("Cloud", local.row(PEOPLE, "p1")?.get("firstName"))
            assertNull(local.outbox[PEOPLE to "p1"])
        }

    @Test
    fun aTombstoneDeletesTheLocalRow() =
        runTest {
            remote.put(PEOPLE, "p1", live("firstName" to "Ada"))
            puller.pull(UID)

            remote.put(PEOPLE, "p1", mapOf("deleted" to true, "updatedAt" to 200L, "updatedBy" to "mcp"))
            puller.pull(UID)

            assertNull(local.row(PEOPLE, "p1"))
            assertEquals("-people/p1", local.applyLog.last())
        }

    private companion object {
        const val UID = "uid"
        const val OWN = "android:me"
    }
}

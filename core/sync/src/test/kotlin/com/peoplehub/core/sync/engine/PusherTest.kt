package com.peoplehub.core.sync.engine

import com.peoplehub.core.database.sync.SyncCollections.CHECK_INS
import com.peoplehub.core.database.sync.SyncCollections.PEOPLE
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PusherTest {
    private val remote = FakeRemoteStore()
    private val local = FakeLocalStore()

    @Test
    fun aLiveRowIsSentWholeWithItsChangeTimeAndAGoneRowAsAMergedTombstone() =
        runTest {
            local.localWrite(PEOPLE, "p1", mapOf("firstName" to "Ada"), at = 10L)
            local.localWrite(PEOPLE, "p2", null, at = 20L)

            Pusher(remote, local, "android:me").push("uid")

            val writes = remote.commits.single().associateBy { it.id }
            val live = writes.getValue("p1")
            assertFalse(live.merge)
            assertEquals("Ada", live.data["firstName"])
            assertEquals(10L, live.data["updatedAt"])
            assertEquals(false, live.data["deleted"])
            assertEquals("android:me", live.data["updatedBy"])
            val tombstone = writes.getValue("p2")
            assertTrue(tombstone.merge)
            assertEquals(true, tombstone.data["deleted"])
            assertTrue(local.outbox.isEmpty())
        }

    @Test
    fun theOutboxGoesUpInBatchesParentsFirst() =
        runTest {
            repeat(5) { local.localWrite(CHECK_INS, "c$it", mapOf("personId" to "p"), at = it.toLong()) }
            local.localWrite(PEOPLE, "p", mapOf("firstName" to "P"), at = 99L)

            val written = Pusher(remote, local, "android:me", batchSize = 2).push("uid")

            assertEquals(6, written)
            assertEquals(listOf(2, 2, 2), remote.commits.map { it.size })
            assertEquals(
                PEOPLE,
                remote.commits
                    .first()
                    .first()
                    .collection,
            )
        }

    @Test
    fun aChangeMadeWhileItWasBeingPushedStaysQueued() =
        runTest {
            local.localWrite(PEOPLE, "p1", mapOf("firstName" to "Ada"), at = 10L)
            val changes = local.pendingChanges(10)
            local.localWrite(PEOPLE, "p1", mapOf("firstName" to "Ada L."), at = 11L)

            local.acknowledge(changes)

            assertEquals(11L, local.outbox[PEOPLE to "p1"])
        }
}

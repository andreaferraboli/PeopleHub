package com.peoplehub.core.sync.engine

import com.peoplehub.core.database.sync.SyncCollections

/** What a pull did: documents applied, children deferred because their parent is missing, dropped. */
internal data class PullReport(
    val applied: Int = 0,
    val deferred: Int = 0,
    val dropped: Int = 0,
) {
    operator fun plus(other: PullReport) = PullReport(applied + other.applied, deferred + other.deferred, dropped + other.dropped)
}

/**
 * Brings cloud changes into Room, one collection at a time in `SyncCollections.ORDERED` (parents
 * first), each from its own cursor.
 *
 * - A document written by this install ([ownTag]) is our own push coming back and is skipped.
 * - A document older than a pending local change to the same row is skipped: the local change wins
 *   and goes up with the next push ([ConflictRule]). Otherwise it is applied and that pending change
 *   discarded.
 * - A child whose parent is not on the phone (an MCP write that raced its parent, say) is *deferred*:
 *   the cursor stops just before it, so the next round fetches it again. After [maxDeferrals] rounds
 *   it is given up (an event is stored unlinked instead), so one broken reference cannot pin the
 *   cursor for ever.
 */
internal class Puller(
    private val remote: RemoteStore,
    private val local: LocalSyncStore,
    private val cursors: CursorStore,
    private val ownTag: String,
    private val maxDeferrals: Int = DEFAULT_MAX_DEFERRALS,
) {
    private val deferrals = HashMap<String, Int>()

    suspend fun pull(uid: String): PullReport {
        var report = PullReport()
        for (collection in SyncCollections.ORDERED) {
            report += pullCollection(uid, collection)
        }
        return report
    }

    private suspend fun pullCollection(uid: String, collection: String): PullReport {
        val cursor = cursors.cursor(uid, collection)
        val docs = remote.fetchChangedSince(uid, collection, cursor).sortedBy { it.serverUpdatedMicros }
        if (docs.isEmpty()) return PullReport()
        val orphanTimes = ArrayList<Long>()
        var applied = 0
        var dropped = 0
        local.applyRemote {
            for (doc in docs) {
                when (applyOne(collection, doc)) {
                    Outcome.APPLIED -> applied++
                    Outcome.SKIPPED -> Unit
                    Outcome.DEFERRED -> orphanTimes += doc.serverUpdatedMicros
                    Outcome.DROPPED -> dropped++
                }
            }
        }
        val next = orphanTimes.minOrNull()?.minus(1) ?: docs.last().serverUpdatedMicros
        if (next > cursor) cursors.setCursor(uid, collection, next)
        return PullReport(applied, orphanTimes.size, dropped)
    }

    private suspend fun RemoteApplier.applyOne(collection: String, doc: RemoteDoc): Outcome {
        if (doc.updatedBy == ownTag) return Outcome.SKIPPED
        if (!ConflictRule.remoteWins(doc.updatedAt, pendingChangedAt(collection, doc.id))) return Outcome.SKIPPED
        if (doc.deleted) {
            delete(collection, doc.id)
            discardPending(collection, doc.id)
            return Outcome.APPLIED
        }
        val key = "$collection/${doc.id}"
        val rounds = deferrals[key] ?: 0
        val giveUp = rounds >= maxDeferrals
        return when (upsert(collection, doc.id, doc.data, allowMissingParent = giveUp)) {
            UpsertResult.APPLIED -> {
                deferrals.remove(key)
                discardPending(collection, doc.id)
                Outcome.APPLIED
            }
            UpsertResult.ORPHAN ->
                if (giveUp) {
                    deferrals.remove(key)
                    Outcome.DROPPED
                } else {
                    deferrals[key] = rounds + 1
                    Outcome.DEFERRED
                }
        }
    }

    private enum class Outcome { APPLIED, SKIPPED, DEFERRED, DROPPED }

    companion object {
        const val DEFAULT_MAX_DEFERRALS: Int = 3
    }
}

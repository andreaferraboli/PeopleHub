package com.peoplehub.core.sync.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConflictRuleTest {
    @Test
    fun withNothingPendingLocallyTheCloudAlwaysWins() {
        assertTrue(ConflictRule.remoteWins(remoteUpdatedAt = 1L, localPendingChangedAt = null))
        assertTrue(ConflictRule.remoteWins(remoteUpdatedAt = null, localPendingChangedAt = null))
    }

    @Test
    fun aNewerLocalChangeWins() {
        assertFalse(ConflictRule.remoteWins(remoteUpdatedAt = 100L, localPendingChangedAt = 101L))
    }

    @Test
    fun aNewerOrEqualCloudVersionWins() {
        assertTrue(ConflictRule.remoteWins(remoteUpdatedAt = 101L, localPendingChangedAt = 100L))
        assertTrue(ConflictRule.remoteWins(remoteUpdatedAt = 100L, localPendingChangedAt = 100L))
    }

    @Test
    fun aCloudVersionWithoutUpdatedAtLosesToAnyLocalChange() {
        assertFalse(ConflictRule.remoteWins(remoteUpdatedAt = null, localPendingChangedAt = 1L))
    }
}

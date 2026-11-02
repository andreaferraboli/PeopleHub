package com.peoplehub.core.domain.sync

import com.peoplehub.core.domain.model.InitialSyncAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InitialSyncDecisionTest {
    @Test
    fun signedOutNeedsNothing() {
        assertEquals(InitialSyncAction.NONE, InitialSyncDecision.resolve(false, false, true))
        assertEquals(InitialSyncAction.NONE, InitialSyncDecision.resolve(false, false, null))
    }

    @Test
    fun onceTheFirstSyncIsDoneTheButtonsDisappear() {
        assertEquals(InitialSyncAction.NONE, InitialSyncDecision.resolve(true, true, false))
        assertEquals(InitialSyncAction.NONE, InitialSyncDecision.resolve(true, true, true))
        assertEquals(InitialSyncAction.NONE, InitialSyncDecision.resolve(true, true, null))
    }

    @Test
    fun anEmptyCloudOffersTheUpload() {
        assertEquals(InitialSyncAction.UPLOAD, InitialSyncDecision.resolve(true, false, false))
    }

    @Test
    fun aCloudWithDataOffersTheDownloadInstead() {
        assertEquals(InitialSyncAction.DOWNLOAD, InitialSyncDecision.resolve(true, false, true))
    }

    @Test
    fun whileTheCloudIsBeingCheckedNothingIsOfferedYet() {
        assertEquals(InitialSyncAction.CHECKING, InitialSyncDecision.resolve(true, false, null))
    }
}

package com.emberr.domain.sync

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanSyncWatermarkTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun theNextSyncAsksForAFewSecondsBeforeTheLastOne() {
        assertEquals(95_000L, sinceWithSafetyOverlap(100_000L))
    }

    @Test
    fun aFirstSyncAsksForEverything() {
        assertEquals(0L, sinceWithSafetyOverlap(0L))
    }

    @Test
    fun theOverlapNeverAsksForATimeBeforeZero() {
        assertEquals(0L, sinceWithSafetyOverlap(2_000L))
    }

    @Test
    fun aDesktopSaveStampedJustBeforeTheSnapshotButWrittenAfterItIsStillFetched() {
        val desktopSnapshotAt = 1_000_000L
        val desktopSaveStampedAt = desktopSnapshotAt - 1_000L

        assertTrue(desktopSaveStampedAt > sinceWithSafetyOverlap(desktopSnapshotAt))
    }

    @Test
    fun anOlderDesktopThatDoesNotSendItsClockTimeIsStillUnderstood() {
        val payload = json.decodeFromString<SyncPayload>("""{"changes":[]}""")

        assertNull(payload.serverSnapshotAt)
    }

    @Test
    fun theSameDesktopKeepsSyncingFromWhereItLeftOff() {
        assertFalse(mustStartOverWithDesktop("desktop-a", "desktop-a", thisSyncStartedFromScratch = false))
    }

    @Test
    fun aDifferentDesktopStartsOverFromScratch() {
        assertTrue(mustStartOverWithDesktop("desktop-a", "desktop-b", thisSyncStartedFromScratch = false))
    }

    @Test
    fun theFirstSyncAfterThisUpdateStartsOverOnce() {
        assertTrue(mustStartOverWithDesktop("", "desktop-a", thisSyncStartedFromScratch = false))
    }

    @Test
    fun aSyncThatAlreadySentEverythingDoesNotStartOverAgain() {
        assertFalse(mustStartOverWithDesktop("desktop-a", "desktop-b", thisSyncStartedFromScratch = true))
    }

    @Test
    fun aDesktopTooOldToSendItsIdChangesNothing() {
        assertFalse(mustStartOverWithDesktop("desktop-a", null, thisSyncStartedFromScratch = false))
    }

    @Test
    fun anOlderDesktopThatDoesNotSendItsIdIsStillUnderstood() {
        val payload = json.decodeFromString<SyncPayload>("""{"changes":[]}""")

        assertNull(payload.desktopId)
    }

    @Test
    fun theDesktopClockTimeSurvivesTheTripToThePhone() {
        val sent = SyncPayload(changes = emptyList(), serverSnapshotAt = 1_234L)

        val received = json.decodeFromString<SyncPayload>(json.encodeToString(sent))

        assertEquals(1_234L, received.serverSnapshotAt)
    }
}

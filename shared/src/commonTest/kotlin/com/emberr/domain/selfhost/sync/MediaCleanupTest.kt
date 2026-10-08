package com.emberr.domain.selfhost.sync

import com.emberr.domain.selfhost.webdav.WebDavResourceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaCleanupTest {

    private val now = 100L * 24 * 60 * 60 * 1000
    private val oneHour = 60L * 60 * 1000

    private fun note(entryId: String, vararg mediaFileNames: String, isDeleted: Boolean = false) =
        SelfHostManifestEntry(
            entryId = entryId,
            entryType = SelfHostEntryType.NOTE,
            updatedAt = 1L,
            isDeleted = isDeleted,
            mediaFileNames = mediaFileNames.toSet()
        )

    private fun daily(entryId: String, vararg mediaFileNames: String) =
        SelfHostManifestEntry(
            entryId = entryId,
            entryType = SelfHostEntryType.DAILY,
            updatedAt = 1L,
            mediaFileNames = mediaFileNames.toSet()
        )

    private fun media(fileName: String, orphanedAt: Long? = null, trashedAt: Long? = null) =
        SelfHostManifestEntry(
            entryId = fileName,
            entryType = SelfHostEntryType.MEDIA,
            updatedAt = 1L,
            orphanedAt = orphanedAt,
            trashedAt = trashedAt
        )

    @Test
    fun filesUsedByLiveNotesAndDailyNotesAreClaimed() {
        val claimed = mediaClaimedByLiveNotes(
            listOf(note("receipt", "receipt.jpg"), daily("daily_space_2026-10-07", "voice.m4a"), media("receipt.jpg"))
        )

        assertEquals(setOf("receipt.jpg", "voice.m4a"), claimed)
    }

    @Test
    fun filesUsedOnlyByADeletedNoteAreNotClaimed() {
        val claimed = mediaClaimedByLiveNotes(listOf(note("old", "old.jpg", isDeleted = true)))

        assertTrue(claimed.isEmpty())
    }

    @Test
    fun aDeviceThatNeverReceivedTheNoteStillKeepsItsPhoto() {
        val serverEntries = listOf(
            note("receipt", "receipt.jpg"),
            media("receipt.jpg", orphanedAt = now - 10 * MEDIA_UNCLAIMED_WAIT_MS)
        )
        val referencedOnThisDevice = emptySet<String>()

        val decisions = decideMediaCleanup(
            mediaEntries = serverEntries.filter { it.entryType == SelfHostEntryType.MEDIA },
            claimedFileNames = mediaClaimedByLiveNotes(serverEntries) + referencedOnThisDevice,
            nowMs = now
        )

        assertTrue(decisions.filesToMoveToTrash.isEmpty())
    }

    @Test
    fun aFileUnclaimedForMoreThanADayGoesToTheTrash() {
        val decisions = decideMediaCleanup(
            mediaEntries = listOf(media("unused.jpg", orphanedAt = now - MEDIA_UNCLAIMED_WAIT_MS - oneHour)),
            claimedFileNames = emptySet(),
            nowMs = now
        )

        assertEquals(setOf("unused.jpg"), decisions.filesToMoveToTrash)
    }

    @Test
    fun aFileUnclaimedForLessThanADayStays() {
        val decisions = decideMediaCleanup(
            mediaEntries = listOf(media("recent.jpg", orphanedAt = now - oneHour)),
            claimedFileNames = emptySet(),
            nowMs = now
        )

        assertTrue(decisions.filesToMoveToTrash.isEmpty())
    }

    @Test
    fun aFileJustNoticedAsUnclaimedIsNotTrashedYet() {
        val decisions = decideMediaCleanup(
            mediaEntries = listOf(media("just-uploaded.jpg", orphanedAt = null)),
            claimedFileNames = emptySet(),
            nowMs = now
        )

        assertTrue(decisions.filesToMoveToTrash.isEmpty())
    }

    @Test
    fun aTrashedFileThatANoteUsesAgainIsRestored() {
        val decisions = decideMediaCleanup(
            mediaEntries = listOf(media("restored.jpg", orphanedAt = now - 3 * MEDIA_UNCLAIMED_WAIT_MS, trashedAt = now - oneHour)),
            claimedFileNames = setOf("restored.jpg"),
            nowMs = now
        )

        assertEquals(setOf("restored.jpg"), decisions.filesToRestoreFromTrash)
        assertTrue(decisions.filesToEmptyFromTrash.isEmpty())
    }

    @Test
    fun aFileInTheTrashForMoreThanThirtyDaysIsEmptied() {
        val decisions = decideMediaCleanup(
            mediaEntries = listOf(media("old.jpg", orphanedAt = 1L, trashedAt = now - MEDIA_TRASH_KEEP_MS - oneHour)),
            claimedFileNames = emptySet(),
            nowMs = now
        )

        assertEquals(setOf("old.jpg"), decisions.filesToEmptyFromTrash)
    }

    @Test
    fun aFileInTheTrashForLessThanThirtyDaysIsKept() {
        val decisions = decideMediaCleanup(
            mediaEntries = listOf(media("old.jpg", orphanedAt = 1L, trashedAt = now - oneHour)),
            claimedFileNames = emptySet(),
            nowMs = now
        )

        assertTrue(decisions.filesToEmptyFromTrash.isEmpty())
        assertTrue(decisions.filesToMoveToTrash.isEmpty())
    }

    @Test
    fun aClaimedFileLosesItsUnclaimedMark() {
        val updated = updatedMediaEntries(
            mediaEntries = listOf(media("photo.jpg", orphanedAt = now - oneHour)),
            claimedFileNames = setOf("photo.jpg"),
            changes = MediaChangesThisRun(),
            nowMs = now
        )

        assertNull(updated.single().orphanedAt)
    }

    @Test
    fun anUnclaimedFileIsMarkedNowAndKeepsAnEarlierMark() {
        val updated = updatedMediaEntries(
            mediaEntries = listOf(media("new.jpg"), media("old.jpg", orphanedAt = now - oneHour)),
            claimedFileNames = emptySet(),
            changes = MediaChangesThisRun(),
            nowMs = now
        ).associateBy { it.entryId }

        assertEquals(now, updated.getValue("new.jpg").orphanedAt)
        assertEquals(now - oneHour, updated.getValue("old.jpg").orphanedAt)
    }

    @Test
    fun theResultsOfThisRunAreWrittenToTheList() {
        val updated = updatedMediaEntries(
            mediaEntries = listOf(
                media("trashed-now.jpg", orphanedAt = 1L),
                media("restored.jpg", orphanedAt = 1L, trashedAt = now - oneHour),
                media("emptied.jpg", orphanedAt = 1L, trashedAt = 1L)
            ),
            claimedFileNames = setOf("restored.jpg", "uploaded.jpg"),
            changes = MediaChangesThisRun(
                movedToTrash = setOf("trashed-now.jpg"),
                restoredFromTrash = setOf("restored.jpg"),
                goneFromServer = setOf("emptied.jpg"),
                newlyUploaded = mapOf("uploaded.jpg" to 1_234L)
            ),
            nowMs = now
        ).associateBy { it.entryId }

        assertEquals(setOf("trashed-now.jpg", "restored.jpg", "uploaded.jpg"), updated.keys)
        assertEquals(now, updated.getValue("trashed-now.jpg").trashedAt)
        assertNull(updated.getValue("restored.jpg").trashedAt)
        assertNull(updated.getValue("restored.jpg").orphanedAt)
        assertNull(updated.getValue("uploaded.jpg").trashedAt)
        assertNull(updated.getValue("uploaded.jpg").orphanedAt)
        assertEquals(1_234L, updated.getValue("uploaded.jpg").mediaSizeBytes)
    }

    @Test
    fun aFileKeepsItsSizeWhenNothingHappenedToItThisRun() {
        val updated = updatedMediaEntries(
            mediaEntries = listOf(media("photo.jpg").copy(mediaSizeBytes = 5_000L)),
            claimedFileNames = setOf("photo.jpg"),
            changes = MediaChangesThisRun(),
            nowMs = now
        )

        assertEquals(5_000L, updated.single().mediaSizeBytes)
    }

    @Test
    fun aListedFileWhosePiecesAreMissingIsRemovedFromTheListSoItCanBeUploadedAgain() {
        val updated = updatedMediaEntries(
            mediaEntries = listOf(media("video.mp4").copy(mediaSizeBytes = 200_000_000L), media("photo.jpg")),
            claimedFileNames = setOf("video.mp4", "photo.jpg"),
            changes = MediaChangesThisRun(missingOnServer = setOf("video.mp4")),
            nowMs = now
        )

        assertEquals(listOf("photo.jpg"), updated.map { it.entryId })
    }

    @Test
    fun aFileMovedToTheTrashMeanwhileKeepsItsTrashRecord() {
        val updated = updatedMediaEntries(
            mediaEntries = listOf(media("video.mp4", orphanedAt = 1L, trashedAt = now - oneHour)),
            claimedFileNames = emptySet(),
            changes = MediaChangesThisRun(missingOnServer = setOf("video.mp4")),
            nowMs = now
        )

        assertEquals(now - oneHour, updated.single().trashedAt)
    }

    @Test
    fun aLiveFileNoLocalNoteUsesIsNotDownloaded() {
        val toDownload = mediaFilesToDownload(
            mediaEntries = listOf(media("deleted_video.mp4", orphanedAt = now - oneHour)),
            referencedFileNames = emptySet(),
            existingLocalFileNames = emptySet()
        )

        assertTrue(toDownload.isEmpty())
    }

    @Test
    fun aLiveFileALocalNoteUsesIsDownloadedWhenMissingFromDisk() {
        val toDownload = mediaFilesToDownload(
            mediaEntries = listOf(media("photo.jpg"), media("already_here.jpg")),
            referencedFileNames = setOf("photo.jpg", "already_here.jpg"),
            existingLocalFileNames = setOf("already_here.jpg")
        )

        assertEquals(setOf("photo.jpg"), toDownload)
    }

    @Test
    fun aTrashedFileIsNotDownloadedEvenWhenALocalNoteUsesIt() {
        val toDownload = mediaFilesToDownload(
            mediaEntries = listOf(media("photo.jpg", trashedAt = now - oneHour)),
            referencedFileNames = setOf("photo.jpg"),
            existingLocalFileNames = emptySet()
        )

        assertTrue(toDownload.isEmpty())
    }

    private fun serverItem(lastModifiedMs: Long?) =
        WebDavResourceInfo(href = "/emberr_sync/media/img_video.mp4/", etag = null, isCollection = false, contentLength = null, lastModifiedMs = lastModifiedMs)

    @Test
    fun anUploadWithNoNewPieceForMoreThanSevenDaysIsAbandoned() {
        val lastProgress = lastUploadProgressAt(listOf(serverItem(now - 8 * 24 * oneHour), serverItem(now - 9 * 24 * oneHour)))

        assertTrue(isAbandonedUpload(lastProgress, now))
    }

    @Test
    fun anOldUploadThatGotANewPieceRecentlyIsKept() {
        val lastProgress = lastUploadProgressAt(listOf(serverItem(now - 20 * 24 * oneHour), serverItem(now - 6 * 24 * oneHour)))

        assertFalse(isAbandonedUpload(lastProgress, now))
    }

    @Test
    fun anUploadIsNeverDeletedWhenTheServerGivesNoTimes() {
        val lastProgress = lastUploadProgressAt(listOf(serverItem(null), serverItem(null)))

        assertNull(lastProgress)
        assertFalse(isAbandonedUpload(lastProgress, now))
    }
}

package com.emberr.domain.selfhost.media

import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.VoiceBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaReferenceScannerTest {

    @Test
    fun aNoteWithNoMediaReferencesNothing() {
        val blocks = listOf(TextBlock(id = "text-1", text = "just words"))

        assertTrue(MediaReferenceScanner.extractMediaFileNames(blocks).isEmpty())
    }

    @Test
    fun imagesDocumentsAndVoiceNotesAreAllCollected() {
        val blocks = listOf(
            ImageBlock(id = "image-1", localFilePath = "photo.png"),
            DocumentBlock(id = "document-1", localFilePath = "invoice.pdf"),
            VoiceBlock(id = "voice-1", localFilePath = "memo.m4a")
        )

        assertEquals(
            setOf("photo.png", "invoice.pdf", "memo.m4a"),
            MediaReferenceScanner.extractMediaFileNames(blocks)
        )
    }

    @Test
    fun onlyTheFileNameIsKeptAndNotTheFolderItSatIn() {
        val blocks = listOf(
            ImageBlock(id = "image-1", localFilePath = "/data/user/0/com.emberr/files/photo.png"),
            DocumentBlock(id = "document-1", localFilePath = "media/nested/invoice.pdf")
        )

        assertEquals(
            setOf("photo.png", "invoice.pdf"),
            MediaReferenceScanner.extractMediaFileNames(blocks)
        )
    }

    @Test
    fun mediaBlocksWithNoFileYetAreIgnored() {
        val blocks = listOf(
            ImageBlock(id = "image-1", localFilePath = null),
            DocumentBlock(id = "document-1", localFilePath = null),
            VoiceBlock(id = "voice-1", localFilePath = null)
        )

        assertTrue(MediaReferenceScanner.extractMediaFileNames(blocks).isEmpty())
    }

    @Test
    fun deletedBlocksNoLongerCountAsUsingTheirMedia() {
        val blocks = listOf(
            ImageBlock(id = "image-1", localFilePath = "kept.png"),
            ImageBlock(id = "image-2", localFilePath = "removed.png", isDeleted = true)
        )

        assertEquals(setOf("kept.png"), MediaReferenceScanner.extractMediaFileNames(blocks))
    }

    @Test
    fun theSameFileUsedTwiceIsOnlyListedOnce() {
        val blocks = listOf(
            ImageBlock(id = "image-1", localFilePath = "shared.png"),
            ImageBlock(id = "image-2", localFilePath = "folder/shared.png")
        )

        assertEquals(setOf("shared.png"), MediaReferenceScanner.extractMediaFileNames(blocks))
    }
}

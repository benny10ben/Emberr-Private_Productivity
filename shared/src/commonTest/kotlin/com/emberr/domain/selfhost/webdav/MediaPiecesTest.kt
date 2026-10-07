package com.emberr.domain.selfhost.webdav

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaPiecesTest {

    private val pieceSize = MEDIA_PIECE_SIZE_BYTES

    @Test
    fun anEmptyFileIsStillOnePiece() {
        assertEquals(1, mediaPieceCount(0))
        assertEquals(0, mediaPieceLength(0, 0))
    }

    @Test
    fun aSmallPhotoIsOnePiece() {
        assertEquals(1, mediaPieceCount(300_000))
        assertEquals(300_000, mediaPieceLength(0, 300_000))
    }

    @Test
    fun aFileOfExactlyTwoPiecesIsTwoFullPieces() {
        val fileSize = 2 * pieceSize

        assertEquals(2, mediaPieceCount(fileSize))
        assertEquals(pieceSize.toInt(), mediaPieceLength(1, fileSize))
    }

    @Test
    fun oneByteOverTwoPiecesMakesAThirdPieceOfOneByte() {
        val fileSize = 2 * pieceSize + 1

        assertEquals(3, mediaPieceCount(fileSize))
        assertEquals(2 * pieceSize, mediaPieceStart(2))
        assertEquals(1, mediaPieceLength(2, fileSize))
    }

    @Test
    fun thePiecesCoverTheWholeFileWithNoGapOrOverlap() {
        val fileSize = 5 * pieceSize + 12_345
        val pieceCount = mediaPieceCount(fileSize)

        val totalLength = (0 until pieceCount).sumOf { mediaPieceLength(it, fileSize).toLong() }

        assertEquals(fileSize, totalLength)
        (1 until pieceCount).forEach { index ->
            assertEquals(mediaPieceStart(index - 1) + mediaPieceLength(index - 1, fileSize), mediaPieceStart(index))
        }
    }

    @Test
    fun aDownloadThatHasNotStartedResumesFromTheFirstPiece() {
        assertEquals(0, piecesAlreadyDownloaded(partialFileBytes = 0, fileSizeBytes = 3 * pieceSize))
    }

    @Test
    fun aDownloadCutOffHalfwayThroughAPieceRedoesOnlyThatPiece() {
        val partialFileBytes = 2 * pieceSize + 500

        assertEquals(2, piecesAlreadyDownloaded(partialFileBytes, fileSizeBytes = 3 * pieceSize))
    }

    @Test
    fun aDownloadWhoseShortLastPieceAlreadyArrivedIsComplete() {
        val fileSize = 2 * pieceSize + 100

        assertEquals(3, piecesAlreadyDownloaded(partialFileBytes = fileSize, fileSizeBytes = fileSize))
    }

    @Test
    fun aPartialFileLargerThanTheRealFileStartsOver() {
        assertEquals(0, piecesAlreadyDownloaded(partialFileBytes = pieceSize + 1, fileSizeBytes = pieceSize))
    }

    @Test
    fun everyPieceGetsADifferentLabel() {
        val labels = setOf(
            mediaPieceLabel("video.mp4", 0, 3).decodeToString(),
            mediaPieceLabel("video.mp4", 1, 3).decodeToString(),
            mediaPieceLabel("video.mp4", 0, 2).decodeToString(),
            mediaPieceLabel("photo.jpg", 0, 3).decodeToString()
        )

        assertEquals(4, labels.size)
    }
}

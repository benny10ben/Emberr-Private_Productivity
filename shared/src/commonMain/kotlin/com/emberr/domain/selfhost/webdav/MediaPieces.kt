package com.emberr.domain.selfhost.webdav

internal const val MEDIA_PIECE_SIZE_BYTES = 8L * 1024 * 1024

internal fun mediaPieceCount(fileSizeBytes: Long): Int =
    maxOf(1L, (fileSizeBytes + MEDIA_PIECE_SIZE_BYTES - 1) / MEDIA_PIECE_SIZE_BYTES).toInt()

internal fun mediaPieceStart(pieceIndex: Int): Long = pieceIndex * MEDIA_PIECE_SIZE_BYTES

internal fun mediaPieceLength(pieceIndex: Int, fileSizeBytes: Long): Int =
    (minOf(fileSizeBytes, mediaPieceStart(pieceIndex + 1)) - mediaPieceStart(pieceIndex)).toInt()

internal fun mediaPieceLabel(mediaId: String, pieceIndex: Int, pieceCount: Int): ByteArray =
    "emberr-media:$mediaId:$pieceIndex:$pieceCount".encodeToByteArray()

internal fun piecesAlreadyDownloaded(partialFileBytes: Long, fileSizeBytes: Long): Int = when {
    partialFileBytes == fileSizeBytes -> mediaPieceCount(fileSizeBytes)
    partialFileBytes > fileSizeBytes -> 0
    else -> (partialFileBytes / MEDIA_PIECE_SIZE_BYTES).toInt()
}

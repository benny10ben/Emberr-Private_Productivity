package com.emberr.domain.canvas

import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.entity.NoteKind
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.media.LocalMediaGcLog
import com.emberr.domain.model.CanvasBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

val NoteMetadataEntity.isEmbeddedCanvas: Boolean
    get() = kind == NoteKind.CANVAS && isSubNote

object EmbeddedCanvasCleanup {
    const val CANVAS_NOTE_ID_FIELD = "canvasNoteId"
    const val DELETED_BLOCK_GRACE_PERIOD_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun canvasesToDeleteAtLaunch(
        embeddedCanvasIds: Set<String>,
        canvasBlocks: List<CanvasBlock>,
        now: Long
    ): Set<String> {
        val blocksByCanvasId = canvasBlocks.groupBy { it.canvasNoteId }
        return embeddedCanvasIds.filterTo(HashSet()) { canvasNoteId ->
            val blocksShowingCanvas = blocksByCanvasId[canvasNoteId].orEmpty()
            blocksShowingCanvas.isNotEmpty() && blocksShowingCanvas.all { block ->
                block.isDeleted && now - block.updatedAt >= DELETED_BLOCK_GRACE_PERIOD_MILLIS
            }
        }
    }

    fun canvasesNoBlockMentions(embeddedCanvasIds: Set<String>, canvasBlocks: List<CanvasBlock>): Set<String> =
        embeddedCanvasIds - canvasBlocks.mapTo(HashSet()) { it.canvasNoteId }

    fun isUntouchedLongEnoughToDeleteWhenEmpty(lastEditedAt: Long, now: Long): Boolean =
        now - lastEditedAt >= DELETED_BLOCK_GRACE_PERIOD_MILLIS

    fun canvasesNoLiveBlockUses(candidateCanvasIds: Set<String>, canvasBlocks: List<CanvasBlock>): Set<String> {
        val usedCanvasIds = canvasBlocks.filter { !it.isDeleted }.mapTo(HashSet()) { it.canvasNoteId }
        return candidateCanvasIds - usedCanvasIds
    }
}

class EmbeddedCanvasCleaner(
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val canvasRepository: CanvasRepository,
    private val noteRepository: NoteRepository,
    private val viewPositionStore: CanvasViewPositionStore
) {
    private val blockJson = Json { ignoreUnknownKeys = true }

    suspend fun deleteCanvasesOfRemovedBlocks() = withContext(Dispatchers.IO) {
        try {
            val embeddedCanvases = noteDao.getAllNotesForBackup().filter { it.isEmbeddedCanvas }
            if (embeddedCanvases.isEmpty()) return@withContext
            val embeddedCanvasIds = embeddedCanvases.mapTo(HashSet()) { it.noteId }
            val now = System.currentTimeMillis()

            val canvasBlocks = blockDao.findBlocksContainingIncludingDeleted(EmbeddedCanvasCleanup.CANVAS_NOTE_ID_FIELD)
                .mapNotNull { entity ->
                    runCatching { blockJson.decodeFromString<NoteBlock>(entity.blockDataJson) }.getOrNull() as? CanvasBlock
                }
            val canvasesOfRemovedBlocks = EmbeddedCanvasCleanup.canvasesToDeleteAtLaunch(
                embeddedCanvasIds = embeddedCanvasIds,
                canvasBlocks = canvasBlocks,
                now = now
            )
            val unmentionedCanvasIds = EmbeddedCanvasCleanup.canvasesNoBlockMentions(embeddedCanvasIds, canvasBlocks)
            val abandonedEmptyCanvases = embeddedCanvases
                .filter { canvas ->
                    canvas.noteId in unmentionedCanvasIds &&
                        EmbeddedCanvasCleanup.isUntouchedLongEnoughToDeleteWhenEmpty(canvas.updatedAt, now)
                }
                .filter { canvas -> canvasRepository.loadCanvasIncludingDeleted(canvas.noteId).isEmpty() }
                .map { it.noteId }

            val canvasIdsToDelete = canvasesOfRemovedBlocks + abandonedEmptyCanvases
            canvasIdsToDelete.forEach { canvasNoteId -> noteRepository.deleteNote(canvasNoteId, "") }
            LocalMediaGcLog.d("deleteCanvasesOfRemovedBlocks: deleted ${canvasIdsToDelete.size} embedded canvas(es)")
        } catch (e: Exception) {
            LocalMediaGcLog.e("deleteCanvasesOfRemovedBlocks: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }

    suspend fun forgetViewPositionsOfDeletedCanvases() = withContext(Dispatchers.IO) {
        try {
            val existingNoteIds = noteDao.getAllNotesForBackup().mapTo(HashSet()) { it.noteId }
            val forgottenCount = viewPositionStore.forgetPositionsOfMissingCanvases(existingNoteIds)
            LocalMediaGcLog.d("forgetViewPositionsOfDeletedCanvases: forgot $forgottenCount view position(s)")
        } catch (e: Exception) {
            LocalMediaGcLog.e("forgetViewPositionsOfDeletedCanvases: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }
}

// Loads a note from storage and turns its blocks into content the widget can draw.
package com.emberr.presentation.widget.note

import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.emberr.presentation.widget.WidgetLog

@Serializable
data class WidgetNoteContent(
    val title: String,
    val elements: List<WidgetElement>
)

class WidgetContentReader(
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val noteRepository: NoteRepository
) {
    private val blockJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun readNoteContentOnce(noteId: String): WidgetNoteContent? =
        withContext(Dispatchers.IO) {
            val blocks = noteRepository.getNoteContent(noteId)?.blocks
                ?: blockDao.getBlocksForNote(noteId).mapNotNull { entity -> decodeBlock(entity.blockDataJson) }
            buildContentFromBlocks(noteId, blocks)
        }

    suspend fun buildContentFromBlocks(noteId: String, blocks: List<NoteBlock>): WidgetNoteContent? =
        withContext(Dispatchers.Default) {
            val metadata = noteDao.getNoteById(noteId)
            if (metadata == null || metadata.trashedAt != null) return@withContext null

            WidgetNoteContent(
                title = metadata.title.trim().ifBlank { "Untitled" },
                elements = buildElementsFromBlocks(blocks, resolveLinkedNoteTitles(blocks))
            )
        }

    private suspend fun resolveLinkedNoteTitles(blocks: List<NoteBlock>): Map<String, String> {
        val linkedNoteIds = blocks.filterIsInstance<LinkedNoteBlock>().map { it.linkedNoteId }.distinct()
        if (linkedNoteIds.isEmpty()) return emptyMap()
        return noteDao.getNotesByIdsIncludingTemplates(linkedNoteIds)
            .associate { note -> note.noteId to note.title.trim().ifBlank { "Untitled" } }
    }

    private fun decodeBlock(blockDataJson: String): NoteBlock? =
        try {
            blockJson.decodeFromString<NoteBlock>(blockDataJson)
        } catch (cause: Exception) {
            WidgetLog.e("Skipped a block that could not be decoded", cause)
            null
        }
}

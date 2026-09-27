package com.emberr.domain.search

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteSearchResult

const val LINKED_NOTE_BLOCK_JSON_MARKER = "\"type\":\"linked_note\""

fun List<NoteSearchResult>.withSubNoteParents(
    parentsByDatabaseId: Map<String, NoteMetadataEntity>,
    parentsByLinkedNoteId: Map<String, NoteMetadataEntity>
): List<NoteSearchResult> = mapNotNull { result ->
    val databaseId = result.note.databaseId
    when {
        databaseId != null -> parentsByDatabaseId[databaseId]?.let { parent -> result.copy(parentTitle = parent.titleAsParent()) }
        result.note.isSubNote -> result.copy(parentTitle = parentsByLinkedNoteId[result.note.noteId]?.titleAsParent())
        else -> result
    }
}

fun List<NoteMetadataEntity>.subNoteParentTitles(
    parentsByDatabaseId: Map<String, NoteMetadataEntity>,
    parentsByLinkedNoteId: Map<String, NoteMetadataEntity>
): Map<String, String> = mapNotNull { note ->
    val databaseId = note.databaseId
    val parent = when {
        databaseId != null -> parentsByDatabaseId[databaseId]
        note.isSubNote -> parentsByLinkedNoteId[note.noteId]
        else -> null
    }
    parent?.let { note.noteId to it.titleAsParent() }
}.toMap()

fun List<Pair<String, NoteMetadataEntity>>.latestParentByChildId(): Map<String, NoteMetadataEntity> =
    groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, parents) -> parents.maxBy { it.updatedAt } }

private fun NoteMetadataEntity.titleAsParent(): String =
    dateString?.takeIf { isDaily } ?: title.ifBlank { "Untitled" }

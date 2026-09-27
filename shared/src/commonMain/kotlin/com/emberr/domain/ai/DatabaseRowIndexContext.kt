package com.emberr.domain.ai

import com.emberr.data.local.room.entity.NoteMetadataEntity

data class DatabaseRowLocation(
    val databaseTitle: String,
    val hostNoteTitle: String?
)

fun databaseRowIndexContext(rowNote: NoteMetadataEntity, location: DatabaseRowLocation?): String = buildString {
    appendLine("[Source: Database row]")
    appendLine("Title: ${rowNote.title.ifBlank { "Untitled" }}")
    location?.databaseTitle?.takeIf { it.isNotBlank() }?.let { appendLine("Database: $it") }
    location?.hostNoteTitle?.takeIf { it.isNotBlank() }?.let { appendLine("Inside note: $it") }
    if (rowNote.snippet.isNotBlank()) appendLine("Description: ${rowNote.snippet}")
}

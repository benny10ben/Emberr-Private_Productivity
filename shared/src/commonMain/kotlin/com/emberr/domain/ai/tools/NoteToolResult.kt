package com.emberr.domain.ai.tools

data class FoundNote(
    val noteId: String,
    val title: String,
    val folderPath: String
)

sealed class NoteToolResult {
    data class Notes(val notes: List<FoundNote>, val truncated: Boolean = false) : NoteToolResult()
    data class FullNote(val noteId: String, val title: String, val markdown: String) : NoteToolResult()
    data class Failure(val reason: String) : NoteToolResult()
}

fun NoteToolResult.renderForModel(): String = when (this) {
    is NoteToolResult.Notes -> {
        val body = if (notes.isEmpty()) "No notes found." else notes.joinToString("\n") { it.renderForModel() }
        if (truncated) "$body\n\n(showing the first ${notes.size} matches only, there are more)" else body
    }
    is NoteToolResult.FullNote -> markdown
    is NoteToolResult.Failure -> "Error: $reason"
}

private fun FoundNote.renderForModel(): String {
    val folder = if (folderPath.isEmpty()) "" else ", folder: $folderPath"
    return "- $title (note_id: $noteId$folder)"
}

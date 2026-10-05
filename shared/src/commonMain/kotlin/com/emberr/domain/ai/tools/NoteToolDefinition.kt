package com.emberr.domain.ai.tools

data class NoteToolParameter(
    val name: String,
    val description: String,
    val isRequired: Boolean = true
)

data class NoteToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<NoteToolParameter>
)

object NoteTools {

    val listNotes = NoteToolDefinition(
        name = "list_notes",
        description = "List the user's notes in the current space, most recently edited first, with each " +
            "note's id and folder. Daily notes are not listed; find them with search_notes.",
        parameters = listOf(
            NoteToolParameter(
                name = "folder_path",
                description = "Only list notes inside this folder and its subfolders, e.g. \"Work\" or " +
                    "\"Work/Projects\". Leave empty to list every note.",
                isRequired = false
            )
        )
    )

    val readNote = NoteToolDefinition(
        name = "read_note",
        description = "Read the full content of one note as markdown.",
        parameters = listOf(
            NoteToolParameter(
                name = "note_id",
                description = "The note's id, exactly as list_notes or search_notes returned it."
            )
        )
    )

    val searchNotes = NoteToolDefinition(
        name = "search_notes",
        description = "Search the titles and content of every note in the current space, including daily " +
            "notes, for a case-insensitive text match. Returns the matching notes with their ids.",
        parameters = listOf(
            NoteToolParameter(
                name = "query",
                description = "Text to search for."
            )
        )
    )

    val all: List<NoteToolDefinition> = listOf(listNotes, readNote, searchNotes)
}

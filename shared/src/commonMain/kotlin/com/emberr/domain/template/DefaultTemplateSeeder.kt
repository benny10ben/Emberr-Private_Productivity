package com.emberr.domain.template

import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.space.ActiveSpaceStore

// Registry of every template that should exist out of the box. Add a new object (in its own
// file, implementing PredefinedTemplate) and list it here to ship another default template.
val PREDEFINED_TEMPLATES: List<PredefinedTemplate> = listOf(ProjectsTemplate, ResearchTemplate)

/**
 * Writes any predefined template that has NEVER existed in the DB. Safe to call on every app
 * launch and every time the templates menu opens.
 *
 * Existence is checked with [NoteRepository.getNoteById] (unfiltered by isTemplate/trashedAt),
 * NOT [NoteRepository.getAllTemplates] (which only returns *visible*, non-trashed templates).
 * That distinction matters: deleteTemplate soft-deletes via trashedAt rather than removing the
 * row outright (see NoteRepositoryImpl.deleteTemplate for why), so a deliberately-deleted
 * predefined template's row still exists - checking against getAllTemplates would treat it as
 * "missing" and silently recreate it the next time this menu opens, undoing the user's deletion.
 * getNoteById still finds the soft-deleted row, so it's correctly left alone. Only a genuinely
 * new install (or a row that has since aged out via the 30-day cleanupOldTrashedNotes sweep) is
 * missing entirely and gets (re)created here.
 */
class DefaultTemplateSeeder(
    private val repository: NoteRepository,
    private val activeSpaceStore: ActiveSpaceStore
) {

    suspend fun seedIfMissing() {
        val now = System.currentTimeMillis()
        val spaceId = activeSpaceStore.currentActiveSpaceId()
        for (template in PREDEFINED_TEMPLATES) {
            val noteId = template.noteIdInSpace(spaceId)
            if (repository.getNoteById(noteId) != null) continue

            repository.saveNote(
                metadata = NoteMetadataEntity(
                    noteId = noteId,
                    title = template.title,
                    icon = template.icon,
                    folderId = null,
                    isDaily = false,
                    dateString = null,
                    createdAt = now,
                    updatedAt = now,
                    filePath = "",
                    isTemplate = true
                ),
                content = template.buildContent()
            )
        }
    }
}

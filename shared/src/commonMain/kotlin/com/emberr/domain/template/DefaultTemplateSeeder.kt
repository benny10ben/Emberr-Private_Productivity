package com.emberr.domain.template

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.sample.STARTER_CONTENT_UPDATED_AT
import com.emberr.domain.space.ActiveSpaceStore

// Registry of every template that should exist out of the box. Add a new object (in its own
// file, implementing PredefinedTemplate) and list it here to ship another default template.
val PREDEFINED_TEMPLATES: List<PredefinedTemplate> = listOf(ProjectsTemplate, ResearchTemplate)

fun templatesNeverSeededInSpace(spaceId: String, seededTemplateNoteIds: Set<String>): List<PredefinedTemplate> =
    PREDEFINED_TEMPLATES.filter { template -> template.noteIdInSpace(spaceId) !in seededTemplateNoteIds }

class DefaultTemplateSeeder(
    private val repository: NoteRepository,
    private val activeSpaceStore: ActiveSpaceStore,
    private val settingsManager: SettingsManager
) {

    suspend fun seedIfMissing() {
        val now = System.currentTimeMillis()
        val spaceId = activeSpaceStore.currentActiveSpaceId()
        for (template in templatesNeverSeededInSpace(spaceId, settingsManager.getSeededTemplateNoteIds())) {
            val noteId = template.noteIdInSpace(spaceId)
            if (repository.getNoteById(noteId) == null) {
                repository.saveNote(
                    metadata = NoteMetadataEntity(
                        noteId = noteId,
                        title = template.title,
                        icon = template.icon,
                        folderId = null,
                        isDaily = false,
                        dateString = null,
                        createdAt = now,
                        updatedAt = STARTER_CONTENT_UPDATED_AT,
                        filePath = "",
                        isTemplate = true
                    ),
                    content = template.buildContent(),
                    stampUpdatedAt = false
                )
            }
            settingsManager.saveSeededTemplateNoteIds(settingsManager.getSeededTemplateNoteIds() + noteId)
        }
    }
}

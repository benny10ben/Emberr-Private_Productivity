package com.emberr.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteContent
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import emberr.shared.generated.resources.Res
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class OnboardingViewModel(
    private val settingsManager: SettingsManager,
    private val noteRepository: NoteRepository,
    private val mediaStorageHelper: MediaStorageHelper
) : ViewModel() {

    private val _previewNoteId = MutableStateFlow<String?>(null)
    val previewNoteId: StateFlow<String?> = _previewNoteId.asStateFlow()

    private var createdNoteId: String? = null
    private var createdNoteFilePath: String? = null
    private var previewNoteCreationJob: Job? = null

    val fontStylePreference: StateFlow<String> = settingsManager.fontStylePreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
    )

    val fontSizePreference: StateFlow<String> = settingsManager.fontSizePreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
    )

    val subNoteOpenMode: StateFlow<String> = settingsManager.subNoteOpenModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE
    )

    val showScrollbar: StateFlow<Boolean> = settingsManager.showScrollbarFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_SHOW_SCROLLBAR
    )

    val aiFeaturesDisabled: StateFlow<Boolean> = settingsManager.aiFeaturesDisabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncConstants.DEFAULT_AI_FEATURES_DISABLED
    )

    fun setFontStylePreference(preference: String) {
        settingsManager.saveFontStylePreference(preference)
    }

    fun setFontSizePreference(preference: String) {
        settingsManager.saveFontSizePreference(preference)
    }

    fun setSubNoteOpenMode(mode: String) {
        settingsManager.saveSubNoteOpenMode(mode)
    }

    fun setShowScrollbar(enabled: Boolean) {
        settingsManager.saveShowScrollbar(enabled)
    }

    fun setAiFeaturesDisabled(disabled: Boolean) {
        settingsManager.saveAiFeaturesDisabled(disabled)
    }

    fun completeOnboarding() {
        settingsManager.saveOnboardingCompleted(true)
    }

    fun ensurePreviewNoteCreated() {
        if (previewNoteCreationJob != null) return

        val newNoteId = UUID.randomUUID().toString()
        val fileName = "note_$newNoteId.json"
        createdNoteId = newNoteId
        createdNoteFilePath = fileName

        previewNoteCreationJob = viewModelScope.launch {
            val coverImagePath = runCatching {
                val bytes = Res.readBytes("files/onboarding_cover_img.jpg")
                mediaStorageHelper.saveBytesToInternalStorage(bytes, "jpg", "image/jpeg")?.localFileName
            }.getOrNull()

            val metadata = NoteMetadataEntity(
                noteId = newNoteId,
                title = "My First Note",
                icon = "📝",
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                filePath = fileName,
                snippet = "",
                coverImagePath = coverImagePath
            )
            noteRepository.saveNote(metadata, NoteContent(blocks = emptyList()))
            _previewNoteId.value = newNoteId
        }
    }

    suspend fun deletePreviewNoteIfExists() {
        val noteId = createdNoteId ?: return
        val filePath = createdNoteFilePath ?: return

        runCatching { previewNoteCreationJob?.join() }

        ActiveEditorRegistry.discardAllPendingWrites()

        previewNoteCreationJob = null
        createdNoteId = null
        createdNoteFilePath = null
        _previewNoteId.value = null

        runCatching { noteRepository.deleteNote(noteId, filePath) }
    }
}

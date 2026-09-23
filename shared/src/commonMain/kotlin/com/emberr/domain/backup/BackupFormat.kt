package com.emberr.domain.backup

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import kotlinx.coroutines.flow.first

object BackupFormat {

    const val DATABASE_ENTRY_NAME = "emberr_database.db"
    const val SETTINGS_ENTRY_NAME = "settings.properties"
    const val MEDIA_ENTRY_PREFIX = "media/"

    suspend fun buildPreferencesText(settingsManager: SettingsManager): String {
        val lines = mutableListOf<String>()
        lines += "sort_type=${settingsManager.sortTypeFlow.first()}"
        lines += "sort_order=${settingsManager.sortOrderFlow.first()}"
        val homeSectionPrefix = SyncConstants.KEY_HOME_SECTION_EXPANDED_PREFIX
        lines += "$homeSectionPrefix${SyncConstants.HOME_SECTION_FAVORITES}=${settingsManager.isHomeSectionExpanded(SyncConstants.HOME_SECTION_FAVORITES)}"
        lines += "$homeSectionPrefix${SyncConstants.HOME_SECTION_NOTES}=${settingsManager.isHomeSectionExpanded(SyncConstants.HOME_SECTION_NOTES)}"
        lines += "$homeSectionPrefix${SyncConstants.HOME_SECTION_RECENTS}=${settingsManager.isHomeSectionExpanded(SyncConstants.HOME_SECTION_RECENTS)}"
        lines += "calendar_view_mode=${settingsManager.calendarViewModeFlow.first()}"
        lines += "theme_preference=${settingsManager.themePreferenceFlow.first()}"
        lines += "font_size_preference=${settingsManager.fontSizePreferenceFlow.first()}"
        lines += "font_style_preference=${settingsManager.fontStylePreferenceFlow.first()}"
        lines += "top_bar_fade_style=${settingsManager.getTopBarFadeStyle()}"
        lines += "subnote_open_mode=${settingsManager.subNoteOpenModeFlow.first()}"
        lines += "show_scrollbar=${settingsManager.isShowScrollbarEnabled()}"
        lines += "ai_generation_mode=${settingsManager.aiGenerationModeFlow.first()}"
        lines += "selected_external_ai_provider=${settingsManager.selectedExternalAiProviderFlow.first()}"
        lines += "ai_knowledge_mode=${settingsManager.knowledgeModeFlow.first()}"
        lines += "ai_max_output_tokens=${settingsManager.maxOutputTokensFlow.first()}"
        lines += "ai_local_context_length=${settingsManager.localContextLengthFlow.first()}"
        lines += "ai_features_disabled=${settingsManager.isAiFeaturesDisabled()}"
        lines += "auto_backup_enabled=${settingsManager.autoBackupEnabledFlow.first()}"
        lines += "backup_frequency=${settingsManager.backupFrequencyFlow.first()}"
        lines += "backup_time=${settingsManager.backupTimeFlow.first()}"
        lines += "backup_day=${settingsManager.backupDayFlow.first()}"
        lines += "bookmark_category_order=${settingsManager.getBookmarkCategoryOrderJson()}"
        lines += "favorite_note_order=${settingsManager.getFavoriteNoteOrderJson()}"
        lines += "expanded_folder_ids=${settingsManager.getExpandedFolderIdsJson()}"
        return lines.joinToString("\n")
    }

    suspend fun applyPreferencesText(settingsManager: SettingsManager, text: String) {
        val values = text.lineSequence()
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
            }
            .toMap()

        val homeSectionPrefix = SyncConstants.KEY_HOME_SECTION_EXPANDED_PREFIX
        values["sort_type"]?.let { sortType ->
            val sortOrder = values["sort_order"] ?: settingsManager.sortOrderFlow.first()
            settingsManager.saveSortSettings(sortType, sortOrder)
        }
        values["$homeSectionPrefix${SyncConstants.HOME_SECTION_FAVORITES}"]?.toBooleanStrictOrNull()?.let {
            settingsManager.saveHomeSectionExpanded(SyncConstants.HOME_SECTION_FAVORITES, it)
        }
        values["$homeSectionPrefix${SyncConstants.HOME_SECTION_NOTES}"]?.toBooleanStrictOrNull()?.let {
            settingsManager.saveHomeSectionExpanded(SyncConstants.HOME_SECTION_NOTES, it)
        }
        values["$homeSectionPrefix${SyncConstants.HOME_SECTION_RECENTS}"]?.toBooleanStrictOrNull()?.let {
            settingsManager.saveHomeSectionExpanded(SyncConstants.HOME_SECTION_RECENTS, it)
        }
        values["calendar_view_mode"]?.let { settingsManager.saveCalendarViewMode(it) }
        values["theme_preference"]?.let { settingsManager.saveThemePreference(it) }
        values["font_size_preference"]?.let { settingsManager.saveFontSizePreference(it) }
        values["font_style_preference"]?.let { settingsManager.saveFontStylePreference(it) }
        values["top_bar_fade_style"]?.let { settingsManager.saveTopBarFadeStyle(it) }
        values["subnote_open_mode"]?.let { settingsManager.saveSubNoteOpenMode(it) }
        values["show_scrollbar"]?.toBooleanStrictOrNull()?.let { settingsManager.saveShowScrollbar(it) }
        values["ai_generation_mode"]?.let { settingsManager.saveAiGenerationMode(it) }
        values["selected_external_ai_provider"]?.let { settingsManager.saveSelectedExternalAiProvider(it) }
        values["ai_knowledge_mode"]?.let { settingsManager.saveKnowledgeMode(it) }
        values["ai_max_output_tokens"]?.toIntOrNull()?.let { settingsManager.saveMaxOutputTokens(it) }
        values["ai_local_context_length"]?.toIntOrNull()?.let { settingsManager.saveLocalContextLength(it) }
        values["ai_features_disabled"]?.toBooleanStrictOrNull()?.let { settingsManager.saveAiFeaturesDisabled(it) }
        values["auto_backup_enabled"]?.toBooleanStrictOrNull()?.let { settingsManager.saveAutoBackupEnabled(it) }
        values["backup_frequency"]?.let { settingsManager.saveBackupFrequency(it) }
        values["backup_time"]?.let { settingsManager.saveBackupTime(it) }
        values["backup_day"]?.let { settingsManager.saveBackupDay(it) }
        values["bookmark_category_order"]?.let { settingsManager.saveBookmarkCategoryOrderJson(it) }
        values["favorite_note_order"]?.let { settingsManager.saveFavoriteNoteOrderJson(it) }
        values["expanded_folder_ids"]?.let { settingsManager.saveExpandedFolderIdsJson(it) }
    }
}

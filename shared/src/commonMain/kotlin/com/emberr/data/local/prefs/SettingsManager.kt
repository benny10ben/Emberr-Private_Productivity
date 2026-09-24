package com.emberr.data.local.prefs

import kotlinx.coroutines.flow.Flow

/**
 * Multiplatform contract for handling user preferences.
 */
interface SettingsManager {
    val activeSpaceIdFlow: Flow<String>
    fun getActiveSpaceId(): String
    fun saveActiveSpaceId(spaceId: String)

    val sortTypeFlow: Flow<String>
    val sortOrderFlow: Flow<String>
    val lastOpenedDesktopStateFlow: Flow<String>

    fun saveSortSettings(type: String, order: String)
    fun saveLastOpenedDesktopState(state: String)

    fun homeSectionExpandedFlow(sectionKey: String): Flow<Boolean>
    fun isHomeSectionExpanded(sectionKey: String): Boolean
    fun saveHomeSectionExpanded(sectionKey: String, expanded: Boolean)

    fun getExpandedFolderIdsJson(): String
    fun saveExpandedFolderIdsJson(json: String)

    fun getLastSyncTimestamp(): Long
    fun saveLastSyncTimestamp(timestamp: Long)

    fun getSelfHostLastSyncTimestamp(): Long
    fun saveSelfHostLastSyncTimestamp(timestamp: Long)

    fun getSelfHostSupportsETags(): Boolean?
    fun saveSelfHostSupportsETags(supports: Boolean)

    fun getSelfHostManifestEtag(): String?
    fun saveSelfHostManifestEtag(etag: String?)

    fun getSyncAuthToken(): String
    fun saveSyncAuthToken(token: String)

    fun getSyncIpAddress(): String
    fun saveSyncIpAddress(ip: String)

    fun getSyncPort(): Int
    fun saveSyncPort(port: Int)

    fun getSyncEncryptionKey(): String
    fun saveSyncEncryptionKey(key: String)

    fun isSyncPairingConfirmed(): Boolean
    fun saveSyncPairingConfirmed(confirmed: Boolean)

    fun clearSyncPairing()

    // Automatic Backups
    val autoBackupEnabledFlow: Flow<Boolean>
    val backupFrequencyFlow: Flow<String>
    val backupDirectoryUriFlow: Flow<String?>

    fun saveAutoBackupEnabled(enabled: Boolean)
    fun saveBackupFrequency(frequency: String)
    fun saveBackupDirectory(uriString: String)
    val backupTimeFlow: Flow<String>
    val backupDayFlow: Flow<String>
    fun saveBackupTime(time: String)
    fun saveBackupDay(day: String)

    // desktop panel resize
    val desktopSidebarWidthFlow: Flow<Float>
    fun saveDesktopSidebarWidth(width: Float)

    // Remembers which calendar view (day/3-day/week/month) was last selected.
    val calendarViewModeFlow: Flow<String>
    fun saveCalendarViewMode(mode: String)

    // Appearance
    val themePreferenceFlow: Flow<String>
    fun saveThemePreference(preference: String)

    val fontSizePreferenceFlow: Flow<String>
    fun saveFontSizePreference(preference: String)

    val fontStylePreferenceFlow: Flow<String>
    fun saveFontStylePreference(preference: String)

    val topBarFadeStyleFlow: Flow<String>
    fun getTopBarFadeStyle(): String
    fun saveTopBarFadeStyle(style: String)

    val subNoteOpenModeFlow: Flow<String>
    fun saveSubNoteOpenMode(mode: String)

    val showScrollbarFlow: Flow<Boolean>
    fun isShowScrollbarEnabled(): Boolean
    fun saveShowScrollbar(enabled: Boolean)

    val customWindowFrameEnabledFlow: Flow<Boolean>
    fun isCustomWindowFrameEnabled(): Boolean
    fun saveCustomWindowFrameEnabled(enabled: Boolean)

    val autoHideTitleBarFlow: Flow<Boolean>
    fun isAutoHideTitleBarEnabled(): Boolean
    fun saveAutoHideTitleBar(enabled: Boolean)

    val automaticUpdateCheckEnabledFlow: Flow<Boolean>
    fun isAutomaticUpdateCheckEnabled(): Boolean
    fun saveAutomaticUpdateCheckEnabled(enabled: Boolean)

    // AI generation
    val aiGenerationModeFlow: Flow<String>
    fun saveAiGenerationMode(mode: String)

    val selectedExternalAiProviderFlow: Flow<String>
    fun saveSelectedExternalAiProvider(provider: String)

    val knowledgeModeFlow: Flow<String>
    fun saveKnowledgeMode(mode: String)

    val maxOutputTokensFlow: Flow<Int>
    fun saveMaxOutputTokens(tokens: Int)

    val localContextLengthFlow: Flow<Int>
    fun saveLocalContextLength(tokens: Int)

    fun getInstalledLocalModelsJson(): String
    fun saveInstalledLocalModelsJson(json: String)

    fun getSelectedLocalModelFileName(): String
    fun saveSelectedLocalModelFileName(fileName: String)

    val aiFeaturesDisabledFlow: Flow<Boolean>
    fun isAiFeaturesDisabled(): Boolean
    fun saveAiFeaturesDisabled(disabled: Boolean)

    val externalAiReadOnlyFlow: Flow<Boolean>
    fun isExternalAiReadOnly(): Boolean
    fun saveExternalAiReadOnly(readOnly: Boolean)

    val bookmarkCategoryOrderJsonFlow: Flow<String>
    fun getBookmarkCategoryOrderJson(): String
    fun saveBookmarkCategoryOrderJson(json: String)

    val favoriteNoteOrderJsonFlow: Flow<String>
    fun getFavoriteNoteOrderJson(): String
    fun saveFavoriteNoteOrderJson(json: String)

    val hasCompletedOnboardingFlow: Flow<Boolean>
    fun isOnboardingCompleted(): Boolean
    fun saveOnboardingCompleted(completed: Boolean)

    fun isSampleDailyNoteSeeded(): Boolean
    fun saveSampleDailyNoteSeeded(seeded: Boolean)

    fun isMediaReferenceListBuilt(): Boolean
    fun saveMediaReferenceListBuilt(built: Boolean)

    fun isSampleNotesSeeded(): Boolean
    fun saveSampleNotesSeeded(seeded: Boolean)
}
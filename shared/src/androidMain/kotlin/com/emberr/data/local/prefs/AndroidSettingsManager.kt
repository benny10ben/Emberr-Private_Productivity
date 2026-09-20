package com.emberr.data.local.prefs

import android.content.SharedPreferences
import com.emberr.core.security.TinkSecretStore
import com.emberr.data.local.room.entity.DEFAULT_SPACE_ID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import androidx.core.content.edit

class AndroidSettingsManager(
    private val sharedPreferences: SharedPreferences,
    private val secretStore: TinkSecretStore
) : SettingsManager {

    override val activeSpaceIdFlow: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == SyncConstants.KEY_ACTIVE_SPACE_ID) {
                trySend(prefs.getString(SyncConstants.KEY_ACTIVE_SPACE_ID, DEFAULT_SPACE_ID) ?: DEFAULT_SPACE_ID)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        trySend(sharedPreferences.getString(SyncConstants.KEY_ACTIVE_SPACE_ID, DEFAULT_SPACE_ID) ?: DEFAULT_SPACE_ID)

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun getActiveSpaceId(): String =
        sharedPreferences.getString(SyncConstants.KEY_ACTIVE_SPACE_ID, DEFAULT_SPACE_ID) ?: DEFAULT_SPACE_ID

    override fun saveActiveSpaceId(spaceId: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_ACTIVE_SPACE_ID, spaceId) }
    }

    override val sortTypeFlow: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == SyncConstants.KEY_SORT_TYPE) {
                trySend(prefs.getString(SyncConstants.KEY_SORT_TYPE, SyncConstants.DEFAULT_SORT_TYPE) ?: SyncConstants.DEFAULT_SORT_TYPE)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        trySend(sharedPreferences.getString(SyncConstants.KEY_SORT_TYPE, SyncConstants.DEFAULT_SORT_TYPE) ?: SyncConstants.DEFAULT_SORT_TYPE)

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override val sortOrderFlow: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == SyncConstants.KEY_SORT_ORDER) {
                trySend(prefs.getString(SyncConstants.KEY_SORT_ORDER, SyncConstants.DEFAULT_SORT_ORDER) ?: SyncConstants.DEFAULT_SORT_ORDER)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        trySend(sharedPreferences.getString(SyncConstants.KEY_SORT_ORDER, SyncConstants.DEFAULT_SORT_ORDER) ?: SyncConstants.DEFAULT_SORT_ORDER)

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override val lastOpenedDesktopStateFlow: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == SyncConstants.KEY_LAST_OPENED_STATE) {
                trySend(prefs.getString(SyncConstants.KEY_LAST_OPENED_STATE, "") ?: "")
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        trySend(sharedPreferences.getString(SyncConstants.KEY_LAST_OPENED_STATE, "") ?: "")

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun saveSortSettings(type: String, order: String) {
        sharedPreferences.edit {
            putString(SyncConstants.KEY_SORT_TYPE, type)
                .putString(SyncConstants.KEY_SORT_ORDER, order)
        }
    }

    override fun saveLastOpenedDesktopState(state: String) {
        sharedPreferences.edit {
            putString(SyncConstants.KEY_LAST_OPENED_STATE, state)
        }
    }

    private val homeSectionExpandedStates = mutableMapOf<String, MutableStateFlow<Boolean>>()

    private fun homeSectionExpandedState(sectionKey: String): MutableStateFlow<Boolean> =
        synchronized(homeSectionExpandedStates) {
            homeSectionExpandedStates.getOrPut(sectionKey) {
                MutableStateFlow(
                    sharedPreferences.getBoolean(
                        SyncConstants.KEY_HOME_SECTION_EXPANDED_PREFIX + sectionKey,
                        SyncConstants.DEFAULT_HOME_SECTION_EXPANDED
                    )
                )
            }
        }

    override fun homeSectionExpandedFlow(sectionKey: String): Flow<Boolean> = homeSectionExpandedState(sectionKey)

    override fun isHomeSectionExpanded(sectionKey: String): Boolean = homeSectionExpandedState(sectionKey).value

    override fun saveHomeSectionExpanded(sectionKey: String, expanded: Boolean) {
        sharedPreferences.edit { putBoolean(SyncConstants.KEY_HOME_SECTION_EXPANDED_PREFIX + sectionKey, expanded) }
        homeSectionExpandedState(sectionKey).value = expanded
    }

    override fun getExpandedFolderIdsJson(): String =
        sharedPreferences.getString(
            SyncConstants.KEY_EXPANDED_FOLDER_IDS_JSON,
            SyncConstants.DEFAULT_EXPANDED_FOLDER_IDS_JSON
        ) ?: SyncConstants.DEFAULT_EXPANDED_FOLDER_IDS_JSON

    override fun saveExpandedFolderIdsJson(json: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_EXPANDED_FOLDER_IDS_JSON, json) }
    }

    override fun getLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(SyncConstants.KEY_SYNC_TIMESTAMP, 0L)
    }

    override fun saveLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit(commit = true) { putLong(SyncConstants.KEY_SYNC_TIMESTAMP, timestamp) }
    }

    override fun getSelfHostLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(SyncConstants.KEY_SELF_HOST_SYNC_TIMESTAMP, 0L)
    }

    override fun saveSelfHostLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit { putLong(SyncConstants.KEY_SELF_HOST_SYNC_TIMESTAMP, timestamp) }
    }

    override fun getSelfHostSupportsETags(): Boolean? {
        if (!sharedPreferences.contains(SyncConstants.KEY_SELF_HOST_SUPPORTS_ETAGS)) return null
        return sharedPreferences.getBoolean(SyncConstants.KEY_SELF_HOST_SUPPORTS_ETAGS, false)
    }

    override fun saveSelfHostSupportsETags(supports: Boolean) {
        sharedPreferences.edit { putBoolean(SyncConstants.KEY_SELF_HOST_SUPPORTS_ETAGS, supports) }
    }

    override fun getSelfHostManifestEtag(): String? {
        return sharedPreferences.getString(SyncConstants.KEY_SELF_HOST_MANIFEST_ETAG, null)
    }

    override fun saveSelfHostManifestEtag(etag: String?) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SELF_HOST_MANIFEST_ETAG, etag) }
    }

    override fun getSyncAuthToken(): String {
        return secretStore.readSecret(SyncConstants.KEY_SYNC_AUTH_TOKEN) ?: ""
    }

    override fun saveSyncAuthToken(token: String) {
        secretStore.writeSecret(SyncConstants.KEY_SYNC_AUTH_TOKEN, token)
    }

    override fun getSyncIpAddress(): String {
        return sharedPreferences.getString(SyncConstants.KEY_SYNC_IP_ADDRESS, "") ?: ""
    }

    override fun saveSyncIpAddress(ip: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SYNC_IP_ADDRESS, ip) }
    }

    override fun getSyncPort(): Int {
        return sharedPreferences.getInt(SyncConstants.KEY_SYNC_PORT, SyncConstants.DEFAULT_PORT)
    }

    override fun saveSyncPort(port: Int) {
        sharedPreferences.edit { putInt(SyncConstants.KEY_SYNC_PORT, port) }
    }

    override fun getSyncEncryptionKey(): String {
        return secretStore.readSecret(SyncConstants.KEY_SYNC_ENCRYPTION_KEY) ?: ""
    }

    override fun saveSyncEncryptionKey(key: String) {
        secretStore.writeSecret(SyncConstants.KEY_SYNC_ENCRYPTION_KEY, key)
    }

    override fun isSyncPairingConfirmed(): Boolean {
        return sharedPreferences.getBoolean(SyncConstants.KEY_SYNC_PAIRING_CONFIRMED, false)
    }

    override fun saveSyncPairingConfirmed(confirmed: Boolean) {
        sharedPreferences.edit { putBoolean(SyncConstants.KEY_SYNC_PAIRING_CONFIRMED, confirmed) }
    }

    override fun clearSyncPairing() {
        secretStore.removeSecret(SyncConstants.KEY_SYNC_AUTH_TOKEN)
        secretStore.removeSecret(SyncConstants.KEY_SYNC_ENCRYPTION_KEY)
        sharedPreferences.edit {
            putString(SyncConstants.KEY_SYNC_IP_ADDRESS, "")
            putInt(SyncConstants.KEY_SYNC_PORT, SyncConstants.DEFAULT_PORT)
            putBoolean(SyncConstants.KEY_SYNC_PAIRING_CONFIRMED, false)
        }
    }

    // Automatic backups
    private val _autoBackupEnabled = MutableStateFlow(sharedPreferences.getBoolean("KEY_AUTO_BACKUP", false))
    private val _backupFrequency = MutableStateFlow(sharedPreferences.getString("KEY_BACKUP_FREQ", "Daily") ?: "Daily")
    private val _backupDirectoryUri = MutableStateFlow(
        sharedPreferences.getString("KEY_BACKUP_DIR", null)?.takeIf { it.isNotBlank() }
    )

    override val autoBackupEnabledFlow: Flow<Boolean> = _autoBackupEnabled
    override val backupFrequencyFlow: Flow<String> = _backupFrequency
    override val backupDirectoryUriFlow: Flow<String?> = _backupDirectoryUri

    override fun saveAutoBackupEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("KEY_AUTO_BACKUP", enabled) }
        _autoBackupEnabled.value = enabled
    }

    override fun saveBackupFrequency(frequency: String) {
        sharedPreferences.edit { putString("KEY_BACKUP_FREQ", frequency) }
        _backupFrequency.value = frequency
    }

    override fun saveBackupDirectory(uriString: String) {
        sharedPreferences.edit { putString("KEY_BACKUP_DIR", uriString) }
        _backupDirectoryUri.value = uriString
    }

    private val _backupTime = MutableStateFlow(sharedPreferences.getString("KEY_BACKUP_TIME", "02:00") ?: "02:00")
    private val _backupDay = MutableStateFlow(sharedPreferences.getString("KEY_BACKUP_DAY", "Sunday") ?: "Sunday")

    override val backupTimeFlow: Flow<String> = _backupTime
    override val backupDayFlow: Flow<String> = _backupDay

    override fun saveBackupTime(time: String) {
        sharedPreferences.edit { putString("KEY_BACKUP_TIME", time) }
        _backupTime.value = time
    }

    override fun saveBackupDay(day: String) {
        sharedPreferences.edit { putString("KEY_BACKUP_DAY", day) }
        _backupDay.value = day
    }

    // panel resizing
    override val desktopSidebarWidthFlow: Flow<Float> = MutableStateFlow(340f)
    override fun saveDesktopSidebarWidth(width: Float) { /* desktop-only */ }

    private val _calendarViewMode = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_CALENDAR_VIEW_MODE, SyncConstants.DEFAULT_CALENDAR_VIEW_MODE)
            ?: SyncConstants.DEFAULT_CALENDAR_VIEW_MODE
    )
    override val calendarViewModeFlow: Flow<String> = _calendarViewMode

    override fun saveCalendarViewMode(mode: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_CALENDAR_VIEW_MODE, mode) }
        _calendarViewMode.value = mode
    }

    private val _themePreference = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_THEME_PREFERENCE, SyncConstants.DEFAULT_THEME_PREFERENCE)
            ?: SyncConstants.DEFAULT_THEME_PREFERENCE
    )
    override val themePreferenceFlow: Flow<String> = _themePreference

    override fun saveThemePreference(preference: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_THEME_PREFERENCE, preference) }
        _themePreference.value = preference
    }

    private val _fontSizePreference = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_FONT_SIZE_PREFERENCE, SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE)
            ?: SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
    )
    override val fontSizePreferenceFlow: Flow<String> = _fontSizePreference

    override fun saveFontSizePreference(preference: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_FONT_SIZE_PREFERENCE, preference) }
        _fontSizePreference.value = preference
    }

    private val _fontStylePreference = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_FONT_STYLE_PREFERENCE, SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE)
            ?: SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
    )
    override val fontStylePreferenceFlow: Flow<String> = _fontStylePreference

    override fun saveFontStylePreference(preference: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_FONT_STYLE_PREFERENCE, preference) }
        _fontStylePreference.value = preference
    }

    private val _subNoteOpenMode = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_SUBNOTE_OPEN_MODE, SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE)
            ?: SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE
    )
    override val subNoteOpenModeFlow: Flow<String> = _subNoteOpenMode

    override fun saveSubNoteOpenMode(mode: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SUBNOTE_OPEN_MODE, mode) }
        _subNoteOpenMode.value = mode
    }

    private val _showScrollbar = MutableStateFlow(
        sharedPreferences.getBoolean(SyncConstants.KEY_SHOW_SCROLLBAR, SyncConstants.DEFAULT_SHOW_SCROLLBAR)
    )
    override val showScrollbarFlow: Flow<Boolean> = _showScrollbar

    override fun isShowScrollbarEnabled(): Boolean = _showScrollbar.value

    override fun saveShowScrollbar(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(SyncConstants.KEY_SHOW_SCROLLBAR, enabled) }
        _showScrollbar.value = enabled
    }

    private val _customWindowFrameEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(
            SyncConstants.KEY_CUSTOM_WINDOW_FRAME,
            SyncConstants.DEFAULT_CUSTOM_WINDOW_FRAME
        )
    )
    override val customWindowFrameEnabledFlow: Flow<Boolean> = _customWindowFrameEnabled

    override fun isCustomWindowFrameEnabled(): Boolean = _customWindowFrameEnabled.value

    override fun saveCustomWindowFrameEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(SyncConstants.KEY_CUSTOM_WINDOW_FRAME, enabled) }
        _customWindowFrameEnabled.value = enabled
    }

    private val _autoHideTitleBar = MutableStateFlow(
        sharedPreferences.getBoolean(
            SyncConstants.KEY_AUTO_HIDE_TITLE_BAR,
            SyncConstants.DEFAULT_AUTO_HIDE_TITLE_BAR
        )
    )
    override val autoHideTitleBarFlow: Flow<Boolean> = _autoHideTitleBar

    override fun isAutoHideTitleBarEnabled(): Boolean = _autoHideTitleBar.value

    override fun saveAutoHideTitleBar(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(SyncConstants.KEY_AUTO_HIDE_TITLE_BAR, enabled) }
        _autoHideTitleBar.value = enabled
    }

    private val _aiGenerationMode = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_AI_GENERATION_MODE, SyncConstants.DEFAULT_AI_GENERATION_MODE)
            ?: SyncConstants.DEFAULT_AI_GENERATION_MODE
    )
    override val aiGenerationModeFlow: Flow<String> = _aiGenerationMode

    override fun saveAiGenerationMode(mode: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_AI_GENERATION_MODE, mode) }
        _aiGenerationMode.value = mode
    }

    private val _selectedExternalAiProvider = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_SELECTED_EXTERNAL_AI_PROVIDER, SyncConstants.DEFAULT_SELECTED_EXTERNAL_AI_PROVIDER)
            ?: SyncConstants.DEFAULT_SELECTED_EXTERNAL_AI_PROVIDER
    )
    override val selectedExternalAiProviderFlow: Flow<String> = _selectedExternalAiProvider

    override fun saveSelectedExternalAiProvider(provider: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SELECTED_EXTERNAL_AI_PROVIDER, provider) }
        _selectedExternalAiProvider.value = provider
    }

    private val _knowledgeMode = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_KNOWLEDGE_MODE, SyncConstants.DEFAULT_KNOWLEDGE_MODE)
            ?: SyncConstants.DEFAULT_KNOWLEDGE_MODE
    )
    override val knowledgeModeFlow: Flow<String> = _knowledgeMode

    override fun saveKnowledgeMode(mode: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_KNOWLEDGE_MODE, mode) }
        _knowledgeMode.value = mode
    }

    private val _maxOutputTokens = MutableStateFlow(
        sharedPreferences.getInt(SyncConstants.KEY_MAX_OUTPUT_TOKENS, SyncConstants.DEFAULT_MAX_OUTPUT_TOKENS)
    )
    override val maxOutputTokensFlow: Flow<Int> = _maxOutputTokens

    override fun saveMaxOutputTokens(tokens: Int) {
        sharedPreferences.edit { putInt(SyncConstants.KEY_MAX_OUTPUT_TOKENS, tokens) }
        _maxOutputTokens.value = tokens
    }

    private val _localContextLength = MutableStateFlow(
        sharedPreferences.getInt(SyncConstants.KEY_LOCAL_CONTEXT_LENGTH, SyncConstants.DEFAULT_LOCAL_CONTEXT_LENGTH)
    )
    override val localContextLengthFlow: Flow<Int> = _localContextLength

    override fun saveLocalContextLength(tokens: Int) {
        sharedPreferences.edit { putInt(SyncConstants.KEY_LOCAL_CONTEXT_LENGTH, tokens) }
        _localContextLength.value = tokens
    }

    override fun getInstalledLocalModelsJson(): String =
        sharedPreferences.getString(SyncConstants.KEY_INSTALLED_LOCAL_MODELS_JSON, SyncConstants.DEFAULT_INSTALLED_LOCAL_MODELS_JSON)
            ?: SyncConstants.DEFAULT_INSTALLED_LOCAL_MODELS_JSON

    override fun saveInstalledLocalModelsJson(json: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_INSTALLED_LOCAL_MODELS_JSON, json) }
    }

    override fun getSelectedLocalModelFileName(): String =
        sharedPreferences.getString(SyncConstants.KEY_SELECTED_LOCAL_MODEL_FILE_NAME, "") ?: ""

    override fun saveSelectedLocalModelFileName(fileName: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SELECTED_LOCAL_MODEL_FILE_NAME, fileName) }
    }

    private val _aiFeaturesDisabled = MutableStateFlow(
        sharedPreferences.getBoolean(SyncConstants.KEY_AI_FEATURES_DISABLED, SyncConstants.DEFAULT_AI_FEATURES_DISABLED)
    )
    override val aiFeaturesDisabledFlow: Flow<Boolean> = _aiFeaturesDisabled

    override fun isAiFeaturesDisabled(): Boolean = _aiFeaturesDisabled.value

    override fun saveAiFeaturesDisabled(disabled: Boolean) {
        sharedPreferences.edit(commit = true) { putBoolean(SyncConstants.KEY_AI_FEATURES_DISABLED, disabled) }
        _aiFeaturesDisabled.value = disabled
    }

    private val _externalAiReadOnly = MutableStateFlow(
        sharedPreferences.getBoolean(SyncConstants.KEY_EXTERNAL_AI_READ_ONLY, SyncConstants.DEFAULT_EXTERNAL_AI_READ_ONLY)
    )
    override val externalAiReadOnlyFlow: Flow<Boolean> = _externalAiReadOnly

    override fun isExternalAiReadOnly(): Boolean = _externalAiReadOnly.value

    override fun saveExternalAiReadOnly(readOnly: Boolean) {
        sharedPreferences.edit(commit = true) { putBoolean(SyncConstants.KEY_EXTERNAL_AI_READ_ONLY, readOnly) }
        _externalAiReadOnly.value = readOnly
    }

    private val _bookmarkCategoryOrderJson = MutableStateFlow(
        sharedPreferences.getString(
            SyncConstants.KEY_BOOKMARK_CATEGORY_ORDER_JSON,
            SyncConstants.DEFAULT_BOOKMARK_CATEGORY_ORDER_JSON
        ) ?: SyncConstants.DEFAULT_BOOKMARK_CATEGORY_ORDER_JSON
    )
    override val bookmarkCategoryOrderJsonFlow: Flow<String> = _bookmarkCategoryOrderJson

    override fun getBookmarkCategoryOrderJson(): String = _bookmarkCategoryOrderJson.value

    override fun saveBookmarkCategoryOrderJson(json: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_BOOKMARK_CATEGORY_ORDER_JSON, json) }
        _bookmarkCategoryOrderJson.value = json
    }

    private val _favoriteNoteOrderJson = MutableStateFlow(
        sharedPreferences.getString(
            SyncConstants.KEY_FAVORITE_NOTE_ORDER_JSON,
            SyncConstants.DEFAULT_FAVORITE_NOTE_ORDER_JSON
        ) ?: SyncConstants.DEFAULT_FAVORITE_NOTE_ORDER_JSON
    )
    override val favoriteNoteOrderJsonFlow: Flow<String> = _favoriteNoteOrderJson

    override fun getFavoriteNoteOrderJson(): String = _favoriteNoteOrderJson.value

    override fun saveFavoriteNoteOrderJson(json: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_FAVORITE_NOTE_ORDER_JSON, json) }
        _favoriteNoteOrderJson.value = json
    }

    private val _hasCompletedOnboarding = MutableStateFlow(
        sharedPreferences.getBoolean(SyncConstants.KEY_ONBOARDING_COMPLETED, SyncConstants.DEFAULT_ONBOARDING_COMPLETED)
    )
    override val hasCompletedOnboardingFlow: Flow<Boolean> = _hasCompletedOnboarding

    override fun isOnboardingCompleted(): Boolean = _hasCompletedOnboarding.value

    override fun saveOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit(commit = true) { putBoolean(SyncConstants.KEY_ONBOARDING_COMPLETED, completed) }
        _hasCompletedOnboarding.value = completed
    }

    private var hasSeededSampleDailyNote = sharedPreferences.getBoolean(
        SyncConstants.KEY_SAMPLE_DAILY_NOTE_SEEDED,
        SyncConstants.DEFAULT_SAMPLE_DAILY_NOTE_SEEDED
    )

    override fun isSampleDailyNoteSeeded(): Boolean = hasSeededSampleDailyNote

    override fun saveSampleDailyNoteSeeded(seeded: Boolean) {
        sharedPreferences.edit(commit = true) { putBoolean(SyncConstants.KEY_SAMPLE_DAILY_NOTE_SEEDED, seeded) }
        hasSeededSampleDailyNote = seeded
    }

    private var hasBuiltMediaReferenceList = sharedPreferences.getBoolean(
        SyncConstants.KEY_MEDIA_REFERENCE_LIST_BUILT,
        SyncConstants.DEFAULT_MEDIA_REFERENCE_LIST_BUILT
    )

    override fun isMediaReferenceListBuilt(): Boolean = hasBuiltMediaReferenceList

    override fun saveMediaReferenceListBuilt(built: Boolean) {
        sharedPreferences.edit(commit = true) { putBoolean(SyncConstants.KEY_MEDIA_REFERENCE_LIST_BUILT, built) }
        hasBuiltMediaReferenceList = built
    }

    private var hasSeededSampleNotes = sharedPreferences.getBoolean(
        SyncConstants.KEY_SAMPLE_NOTES_SEEDED,
        SyncConstants.DEFAULT_SAMPLE_NOTES_SEEDED
    )

    override fun isSampleNotesSeeded(): Boolean = hasSeededSampleNotes

    override fun saveSampleNotesSeeded(seeded: Boolean) {
        sharedPreferences.edit(commit = true) { putBoolean(SyncConstants.KEY_SAMPLE_NOTES_SEEDED, seeded) }
        hasSeededSampleNotes = seeded
    }
}
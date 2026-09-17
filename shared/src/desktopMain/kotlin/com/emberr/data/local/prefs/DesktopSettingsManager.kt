package com.emberr.data.local.prefs

import com.emberr.core.security.secrets.DesktopSecretStore
import com.emberr.core.security.secrets.SecretNamespace
import com.emberr.data.local.room.DEFAULT_SPACE_ID
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class DesktopSettingsManager(private val secretStore: DesktopSecretStore) : SettingsManager {
    // Standard unencrypted preferences for basic app state, stored alongside the
    // database and media so that removing the app folder resets the app completely.
    private val prefs = DesktopPreferenceStore(
        storageDirectory = File(System.getProperty("user.home"), ".emberr")
    )

    private val _activeSpaceId = MutableStateFlow(prefs.get(SyncConstants.KEY_ACTIVE_SPACE_ID, DEFAULT_SPACE_ID))

    override val activeSpaceIdFlow: Flow<String> = _activeSpaceId

    override fun getActiveSpaceId(): String = _activeSpaceId.value

    override fun saveActiveSpaceId(spaceId: String) {
        prefs.put(SyncConstants.KEY_ACTIVE_SPACE_ID, spaceId)
        _activeSpaceId.value = spaceId
    }

    private val _sortType = MutableStateFlow(prefs.get(SyncConstants.KEY_SORT_TYPE, SyncConstants.DEFAULT_SORT_TYPE))
    private val _sortOrder = MutableStateFlow(prefs.get(SyncConstants.KEY_SORT_ORDER, SyncConstants.DEFAULT_SORT_ORDER))
    private val _lastOpenedState = MutableStateFlow(prefs.get(SyncConstants.KEY_LAST_OPENED_STATE, ""))
    private val _desktopSidebarWidth = MutableStateFlow(prefs.getFloat("KEY_DESKTOP_SIDEBAR_WIDTH", 340f))

    override val sortTypeFlow: Flow<String> = _sortType
    override val sortOrderFlow: Flow<String> = _sortOrder
    override val lastOpenedDesktopStateFlow: Flow<String> = _lastOpenedState

    override fun saveSortSettings(type: String, order: String) {
        prefs.put(SyncConstants.KEY_SORT_TYPE, type)
        prefs.put(SyncConstants.KEY_SORT_ORDER, order)
        _sortType.value = type
        _sortOrder.value = order
    }

    override fun saveLastOpenedDesktopState(state: String) {
        prefs.put(SyncConstants.KEY_LAST_OPENED_STATE, state)
        _lastOpenedState.value = state
    }

    private val homeSectionExpandedStates = mutableMapOf<String, MutableStateFlow<Boolean>>()

    private fun homeSectionExpandedState(sectionKey: String): MutableStateFlow<Boolean> =
        synchronized(homeSectionExpandedStates) {
            homeSectionExpandedStates.getOrPut(sectionKey) {
                MutableStateFlow(
                    prefs.getBoolean(
                        SyncConstants.KEY_HOME_SECTION_EXPANDED_PREFIX + sectionKey,
                        SyncConstants.DEFAULT_HOME_SECTION_EXPANDED
                    )
                )
            }
        }

    override fun homeSectionExpandedFlow(sectionKey: String): Flow<Boolean> = homeSectionExpandedState(sectionKey)

    override fun isHomeSectionExpanded(sectionKey: String): Boolean = homeSectionExpandedState(sectionKey).value

    override fun saveHomeSectionExpanded(sectionKey: String, expanded: Boolean) {
        prefs.putBoolean(SyncConstants.KEY_HOME_SECTION_EXPANDED_PREFIX + sectionKey, expanded)
        homeSectionExpandedState(sectionKey).value = expanded
    }

    override fun getExpandedFolderIdsJson(): String =
        prefs.get(
            SyncConstants.KEY_EXPANDED_FOLDER_IDS_JSON,
            SyncConstants.DEFAULT_EXPANDED_FOLDER_IDS_JSON
        )

    override fun saveExpandedFolderIdsJson(json: String) {
        prefs.put(SyncConstants.KEY_EXPANDED_FOLDER_IDS_JSON, json)
    }

    override fun getLastSyncTimestamp(): Long {
        return prefs.getLong(SyncConstants.KEY_SYNC_TIMESTAMP, 0L)
    }

    override fun saveLastSyncTimestamp(timestamp: Long) {
        prefs.putLong(SyncConstants.KEY_SYNC_TIMESTAMP, timestamp)
    }

    override fun getSelfHostLastSyncTimestamp(): Long {
        return prefs.getLong(SyncConstants.KEY_SELF_HOST_SYNC_TIMESTAMP, 0L)
    }

    override fun saveSelfHostLastSyncTimestamp(timestamp: Long) {
        prefs.putLong(SyncConstants.KEY_SELF_HOST_SYNC_TIMESTAMP, timestamp)
    }

    override fun getSelfHostSupportsETags(): Boolean? {
        return when (prefs.getOrNull(SyncConstants.KEY_SELF_HOST_SUPPORTS_ETAGS)) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    override fun saveSelfHostSupportsETags(supports: Boolean) {
        prefs.put(SyncConstants.KEY_SELF_HOST_SUPPORTS_ETAGS, supports.toString())
    }

    override fun getSelfHostManifestEtag(): String? {
        return prefs.getOrNull(SyncConstants.KEY_SELF_HOST_MANIFEST_ETAG)
    }

    override fun saveSelfHostManifestEtag(etag: String?) {
        if (etag == null) {
            prefs.remove(SyncConstants.KEY_SELF_HOST_MANIFEST_ETAG)
        } else {
            prefs.put(SyncConstants.KEY_SELF_HOST_MANIFEST_ETAG, etag)
        }
    }

    private fun saveSecureString(account: String, secret: String) {
        secretStore.writeSecret(SecretNamespace.AppSettings, account, secret)
    }

    private fun getSecureString(account: String): String =
        secretStore.readSecret(SecretNamespace.AppSettings, account).orEmpty()

    override fun getSyncAuthToken(): String = getSecureString(SyncConstants.KEY_SYNC_AUTH_TOKEN)
    override fun saveSyncAuthToken(token: String) = saveSecureString(SyncConstants.KEY_SYNC_AUTH_TOKEN, token)

    override fun getSyncEncryptionKey(): String = getSecureString(SyncConstants.KEY_SYNC_ENCRYPTION_KEY)
    override fun saveSyncEncryptionKey(key: String) = saveSecureString(SyncConstants.KEY_SYNC_ENCRYPTION_KEY, key)

    override fun getSyncIpAddress(): String = prefs.get(SyncConstants.KEY_SYNC_IP_ADDRESS, "")
    override fun saveSyncIpAddress(ip: String) = prefs.put(SyncConstants.KEY_SYNC_IP_ADDRESS, ip)

    override fun getSyncPort(): Int = prefs.getInt(SyncConstants.KEY_SYNC_PORT, SyncConstants.DEFAULT_PORT)
    override fun saveSyncPort(port: Int) = prefs.putInt(SyncConstants.KEY_SYNC_PORT, port)

    override fun isSyncPairingConfirmed(): Boolean = prefs.getBoolean(SyncConstants.KEY_SYNC_PAIRING_CONFIRMED, false)
    override fun saveSyncPairingConfirmed(confirmed: Boolean) =
        prefs.putBoolean(SyncConstants.KEY_SYNC_PAIRING_CONFIRMED, confirmed)

    override fun clearSyncPairing() {
        secretStore.removeSecret(SecretNamespace.AppSettings, SyncConstants.KEY_SYNC_AUTH_TOKEN)
        secretStore.removeSecret(SecretNamespace.AppSettings, SyncConstants.KEY_SYNC_ENCRYPTION_KEY)
        prefs.put(SyncConstants.KEY_SYNC_IP_ADDRESS, "")
        prefs.putInt(SyncConstants.KEY_SYNC_PORT, SyncConstants.DEFAULT_PORT)
        prefs.putBoolean(SyncConstants.KEY_SYNC_PAIRING_CONFIRMED, false)
    }

    // Automatic Backups
    private val _autoBackupEnabled = MutableStateFlow(prefs.getBoolean("KEY_AUTO_BACKUP", false))
    private val _backupFrequency = MutableStateFlow(prefs.get("KEY_BACKUP_FREQ", "Daily"))
    private val _backupDirectoryUri = MutableStateFlow(prefs.get("KEY_BACKUP_DIR", "").takeIf { it.isNotBlank() })
    private val _backupTime = MutableStateFlow(prefs.get("KEY_BACKUP_TIME", "02:00"))
    private val _backupDay = MutableStateFlow(prefs.get("KEY_BACKUP_DAY", "Sunday"))

    override val autoBackupEnabledFlow: Flow<Boolean> = _autoBackupEnabled
    override val backupFrequencyFlow: Flow<String> = _backupFrequency
    override val backupDirectoryUriFlow: Flow<String?> = _backupDirectoryUri
    override val backupTimeFlow: Flow<String> = _backupTime
    override val backupDayFlow: Flow<String> = _backupDay

    override fun saveAutoBackupEnabled(enabled: Boolean) {
        prefs.putBoolean("KEY_AUTO_BACKUP", enabled)
        _autoBackupEnabled.value = enabled
    }

    override fun saveBackupFrequency(frequency: String) {
        prefs.put("KEY_BACKUP_FREQ", frequency)
        _backupFrequency.value = frequency
    }

    override fun saveBackupDirectory(uriString: String) {
        prefs.put("KEY_BACKUP_DIR", uriString)
        _backupDirectoryUri.value = uriString
    }

    override fun saveBackupTime(time: String) {
        prefs.put("KEY_BACKUP_TIME", time)
        _backupTime.value = time
    }

    override fun saveBackupDay(day: String) {
        prefs.put("KEY_BACKUP_DAY", day)
        _backupDay.value = day
    }

    // panel resizing
    override val desktopSidebarWidthFlow: Flow<Float> = _desktopSidebarWidth
    override fun saveDesktopSidebarWidth(width: Float) {
        prefs.putFloat("KEY_DESKTOP_SIDEBAR_WIDTH", width)
        _desktopSidebarWidth.value = width
    }

    private val _calendarViewMode = MutableStateFlow(
        prefs.get(SyncConstants.KEY_CALENDAR_VIEW_MODE, SyncConstants.DEFAULT_CALENDAR_VIEW_MODE)
    )
    override val calendarViewModeFlow: Flow<String> = _calendarViewMode

    override fun saveCalendarViewMode(mode: String) {
        prefs.put(SyncConstants.KEY_CALENDAR_VIEW_MODE, mode)
        _calendarViewMode.value = mode
    }

    private val _themePreference = MutableStateFlow(
        prefs.get(SyncConstants.KEY_THEME_PREFERENCE, SyncConstants.DEFAULT_THEME_PREFERENCE)
    )
    override val themePreferenceFlow: Flow<String> = _themePreference

    override fun saveThemePreference(preference: String) {
        prefs.put(SyncConstants.KEY_THEME_PREFERENCE, preference)
        _themePreference.value = preference
    }

    private val _fontSizePreference = MutableStateFlow(
        prefs.get(SyncConstants.KEY_FONT_SIZE_PREFERENCE, SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE)
    )
    override val fontSizePreferenceFlow: Flow<String> = _fontSizePreference

    override fun saveFontSizePreference(preference: String) {
        prefs.put(SyncConstants.KEY_FONT_SIZE_PREFERENCE, preference)
        _fontSizePreference.value = preference
    }

    private val _fontStylePreference = MutableStateFlow(
        prefs.get(SyncConstants.KEY_FONT_STYLE_PREFERENCE, SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE)
    )
    override val fontStylePreferenceFlow: Flow<String> = _fontStylePreference

    override fun saveFontStylePreference(preference: String) {
        prefs.put(SyncConstants.KEY_FONT_STYLE_PREFERENCE, preference)
        _fontStylePreference.value = preference
    }

    private val _subNoteOpenMode = MutableStateFlow(
        prefs.get(SyncConstants.KEY_SUBNOTE_OPEN_MODE, SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE)
    )
    override val subNoteOpenModeFlow: Flow<String> = _subNoteOpenMode

    override fun saveSubNoteOpenMode(mode: String) {
        prefs.put(SyncConstants.KEY_SUBNOTE_OPEN_MODE, mode)
        _subNoteOpenMode.value = mode
    }

    private val _showScrollbar = MutableStateFlow(
        prefs.getBoolean(SyncConstants.KEY_SHOW_SCROLLBAR, SyncConstants.DEFAULT_SHOW_SCROLLBAR)
    )
    override val showScrollbarFlow: Flow<Boolean> = _showScrollbar

    override fun isShowScrollbarEnabled(): Boolean = _showScrollbar.value

    override fun saveShowScrollbar(enabled: Boolean) {
        prefs.putBoolean(SyncConstants.KEY_SHOW_SCROLLBAR, enabled)
        _showScrollbar.value = enabled
    }

    private val _aiGenerationMode = MutableStateFlow(
        prefs.get(SyncConstants.KEY_AI_GENERATION_MODE, SyncConstants.DEFAULT_AI_GENERATION_MODE)
    )
    override val aiGenerationModeFlow: Flow<String> = _aiGenerationMode

    override fun saveAiGenerationMode(mode: String) {
        prefs.put(SyncConstants.KEY_AI_GENERATION_MODE, mode)
        _aiGenerationMode.value = mode
    }

    private val _selectedExternalAiProvider = MutableStateFlow(
        prefs.get(SyncConstants.KEY_SELECTED_EXTERNAL_AI_PROVIDER, SyncConstants.DEFAULT_SELECTED_EXTERNAL_AI_PROVIDER)
    )
    override val selectedExternalAiProviderFlow: Flow<String> = _selectedExternalAiProvider

    override fun saveSelectedExternalAiProvider(provider: String) {
        prefs.put(SyncConstants.KEY_SELECTED_EXTERNAL_AI_PROVIDER, provider)
        _selectedExternalAiProvider.value = provider
    }

    private val _knowledgeMode = MutableStateFlow(
        prefs.get(SyncConstants.KEY_KNOWLEDGE_MODE, SyncConstants.DEFAULT_KNOWLEDGE_MODE)
    )
    override val knowledgeModeFlow: Flow<String> = _knowledgeMode

    override fun saveKnowledgeMode(mode: String) {
        prefs.put(SyncConstants.KEY_KNOWLEDGE_MODE, mode)
        _knowledgeMode.value = mode
    }

    private val _maxOutputTokens = MutableStateFlow(
        prefs.getInt(SyncConstants.KEY_MAX_OUTPUT_TOKENS, SyncConstants.DEFAULT_MAX_OUTPUT_TOKENS)
    )
    override val maxOutputTokensFlow: Flow<Int> = _maxOutputTokens

    override fun saveMaxOutputTokens(tokens: Int) {
        prefs.putInt(SyncConstants.KEY_MAX_OUTPUT_TOKENS, tokens)
        _maxOutputTokens.value = tokens
    }

    private val _localContextLength = MutableStateFlow(
        prefs.getInt(SyncConstants.KEY_LOCAL_CONTEXT_LENGTH, SyncConstants.DEFAULT_LOCAL_CONTEXT_LENGTH)
    )
    override val localContextLengthFlow: Flow<Int> = _localContextLength

    override fun saveLocalContextLength(tokens: Int) {
        prefs.putInt(SyncConstants.KEY_LOCAL_CONTEXT_LENGTH, tokens)
        _localContextLength.value = tokens
    }

    override fun getInstalledLocalModelsJson(): String =
        prefs.get(SyncConstants.KEY_INSTALLED_LOCAL_MODELS_JSON, SyncConstants.DEFAULT_INSTALLED_LOCAL_MODELS_JSON)

    override fun saveInstalledLocalModelsJson(json: String) {
        prefs.put(SyncConstants.KEY_INSTALLED_LOCAL_MODELS_JSON, json)
    }

    override fun getSelectedLocalModelFileName(): String =
        prefs.get(SyncConstants.KEY_SELECTED_LOCAL_MODEL_FILE_NAME, "")

    override fun saveSelectedLocalModelFileName(fileName: String) {
        prefs.put(SyncConstants.KEY_SELECTED_LOCAL_MODEL_FILE_NAME, fileName)
    }

    private val _aiFeaturesDisabled = MutableStateFlow(
        prefs.getBoolean(SyncConstants.KEY_AI_FEATURES_DISABLED, SyncConstants.DEFAULT_AI_FEATURES_DISABLED)
    )
    override val aiFeaturesDisabledFlow: Flow<Boolean> = _aiFeaturesDisabled

    override fun isAiFeaturesDisabled(): Boolean = _aiFeaturesDisabled.value

    override fun saveAiFeaturesDisabled(disabled: Boolean) {
        prefs.putBoolean(SyncConstants.KEY_AI_FEATURES_DISABLED, disabled)
        _aiFeaturesDisabled.value = disabled
    }

    private val _externalAiReadOnly = MutableStateFlow(
        prefs.getBoolean(SyncConstants.KEY_EXTERNAL_AI_READ_ONLY, SyncConstants.DEFAULT_EXTERNAL_AI_READ_ONLY)
    )
    override val externalAiReadOnlyFlow: Flow<Boolean> = _externalAiReadOnly

    override fun isExternalAiReadOnly(): Boolean = _externalAiReadOnly.value

    override fun saveExternalAiReadOnly(readOnly: Boolean) {
        prefs.putBoolean(SyncConstants.KEY_EXTERNAL_AI_READ_ONLY, readOnly)
        _externalAiReadOnly.value = readOnly
    }

    private val _bookmarkCategoryOrderJson = MutableStateFlow(
        prefs.get(
            SyncConstants.KEY_BOOKMARK_CATEGORY_ORDER_JSON,
            SyncConstants.DEFAULT_BOOKMARK_CATEGORY_ORDER_JSON
        )
    )
    override val bookmarkCategoryOrderJsonFlow: Flow<String> = _bookmarkCategoryOrderJson

    override fun getBookmarkCategoryOrderJson(): String = _bookmarkCategoryOrderJson.value

    override fun saveBookmarkCategoryOrderJson(json: String) {
        prefs.put(SyncConstants.KEY_BOOKMARK_CATEGORY_ORDER_JSON, json)
        _bookmarkCategoryOrderJson.value = json
    }

    private val _favoriteNoteOrderJson = MutableStateFlow(
        prefs.get(
            SyncConstants.KEY_FAVORITE_NOTE_ORDER_JSON,
            SyncConstants.DEFAULT_FAVORITE_NOTE_ORDER_JSON
        )
    )
    override val favoriteNoteOrderJsonFlow: Flow<String> = _favoriteNoteOrderJson

    override fun getFavoriteNoteOrderJson(): String = _favoriteNoteOrderJson.value

    override fun saveFavoriteNoteOrderJson(json: String) {
        prefs.put(SyncConstants.KEY_FAVORITE_NOTE_ORDER_JSON, json)
        _favoriteNoteOrderJson.value = json
    }

    private val _hasCompletedOnboarding = MutableStateFlow(
        prefs.getBoolean(SyncConstants.KEY_ONBOARDING_COMPLETED, SyncConstants.DEFAULT_ONBOARDING_COMPLETED)
    )
    override val hasCompletedOnboardingFlow: Flow<Boolean> = _hasCompletedOnboarding

    override fun isOnboardingCompleted(): Boolean = _hasCompletedOnboarding.value

    override fun saveOnboardingCompleted(completed: Boolean) {
        prefs.putBoolean(SyncConstants.KEY_ONBOARDING_COMPLETED, completed)
        _hasCompletedOnboarding.value = completed
    }

    private var hasSeededSampleDailyNote = prefs.getBoolean(
        SyncConstants.KEY_SAMPLE_DAILY_NOTE_SEEDED,
        SyncConstants.DEFAULT_SAMPLE_DAILY_NOTE_SEEDED
    )

    override fun isSampleDailyNoteSeeded(): Boolean = hasSeededSampleDailyNote

    override fun saveSampleDailyNoteSeeded(seeded: Boolean) {
        prefs.putBoolean(SyncConstants.KEY_SAMPLE_DAILY_NOTE_SEEDED, seeded)
        hasSeededSampleDailyNote = seeded
    }

    private var hasBuiltMediaReferenceList = prefs.getBoolean(
        SyncConstants.KEY_MEDIA_REFERENCE_LIST_BUILT,
        SyncConstants.DEFAULT_MEDIA_REFERENCE_LIST_BUILT
    )

    override fun isMediaReferenceListBuilt(): Boolean = hasBuiltMediaReferenceList

    override fun saveMediaReferenceListBuilt(built: Boolean) {
        prefs.putBoolean(SyncConstants.KEY_MEDIA_REFERENCE_LIST_BUILT, built)
        hasBuiltMediaReferenceList = built
    }

    private var hasSeededSampleNotes = prefs.getBoolean(
        SyncConstants.KEY_SAMPLE_NOTES_SEEDED,
        SyncConstants.DEFAULT_SAMPLE_NOTES_SEEDED
    )

    override fun isSampleNotesSeeded(): Boolean = hasSeededSampleNotes

    override fun saveSampleNotesSeeded(seeded: Boolean) {
        prefs.putBoolean(SyncConstants.KEY_SAMPLE_NOTES_SEEDED, seeded)
        hasSeededSampleNotes = seeded
    }
}
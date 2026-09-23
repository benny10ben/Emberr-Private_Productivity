package com.emberr.presentation.rag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.domain.ai.AiGenerationMode
import com.emberr.domain.ai.chat.ChatMessage
import com.emberr.domain.ai.chat.ChatSession
import com.emberr.domain.ai.chat.ChatSessionRepository
import com.emberr.domain.ai.chat.ChatTurn
import com.emberr.domain.ai.models.InstalledLocalModel
import com.emberr.domain.ai.KnowledgeMode
import com.emberr.domain.ai.LocalAiUnsupportedException
import com.emberr.domain.ai.RagRepository
import com.emberr.domain.ai.external.AiSettingsRepository
import com.emberr.domain.ai.external.ExternalAiException
import com.emberr.domain.ai.external.ExternalAiProvider
import com.emberr.domain.ai.external.ExternalAiProviderConfig
import com.emberr.domain.ai.tools.VaultPendingWrite
import com.emberr.domain.ai.tools.VaultPendingWriteEvents
import com.emberr.domain.ai.tools.VaultPendingWriteStatus
import com.emberr.domain.ai.tools.VaultToolCallEvents
import com.emberr.domain.ai.tools.VaultToolResult
import com.emberr.domain.ai.tools.VaultToolRunner
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import com.emberr.domain.ai.models.LocalModelUploadManager
import com.emberr.domain.ai.models.ModelDownloadLog
import com.emberr.domain.ai.models.ModelDownloadProgress
import com.emberr.domain.ai.models.ModelDownloadScheduler
import com.emberr.domain.ai.models.ModelFileNames
import com.emberr.domain.ai.ReindexAllNotesUseCase
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.ai.models.hasPendingModelDeletion
import com.emberr.domain.ai.models.hasResumableDownload
import com.emberr.domain.ai.models.resumeProgressFraction
import com.emberr.domain.ai.models.restoreModelFile
import com.emberr.domain.ai.models.softDeleteModelFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface EmbeddingSetupState {
    data object Checking : EmbeddingSetupState
    data object Required : EmbeddingSetupState
    data class Downloading(val progress: Float) : EmbeddingSetupState
    data class DownloadFailed(val message: String) : EmbeddingSetupState
    data object DownloadComplete : EmbeddingSetupState
    data class Indexing(val completed: Int, val total: Int) : EmbeddingSetupState
    data object Ready : EmbeddingSetupState
}

sealed interface LocalModelUploadState {
    data object Idle : LocalModelUploadState
    data object Uploading : LocalModelUploadState
    data object Success : LocalModelUploadState
    data class Failed(val message: String) : LocalModelUploadState
}

class RagViewModel(
    private val ragRepository: RagRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val modelDownloadScheduler: ModelDownloadScheduler,
    private val reindexAllNotesUseCase: ReindexAllNotesUseCase,
    private val localModelUploadManager: LocalModelUploadManager,
    private val vaultToolRunner: VaultToolRunner,
    private val vaultPendingWriteEvents: VaultPendingWriteEvents,
    private val vaultToolCallEvents: VaultToolCallEvents,
    private val activeSpaceStore: ActiveSpaceStore
) : ViewModel() {

    val localAiUnsupportedReason: String? = ragRepository.localAiUnsupportedReason

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // null = still checking, true = available, false = not available
    private val _isModelAvailable = MutableStateFlow<Boolean?>(null)
    val isModelAvailable: StateFlow<Boolean?> = _isModelAvailable.asStateFlow()

    val aiGenerationMode: StateFlow<AiGenerationMode> = aiSettingsRepository.aiGenerationMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiGenerationMode.LOCAL)

    val selectedExternalAiProvider: StateFlow<ExternalAiProvider> = aiSettingsRepository.selectedExternalAiProvider
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExternalAiProvider.OPENAI)

    val knowledgeMode: StateFlow<KnowledgeMode> = aiSettingsRepository.knowledgeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, KnowledgeMode.DEFAULT)

    val maxOutputTokens: StateFlow<Int> = aiSettingsRepository.maxOutputTokens
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MAX_OUTPUT_TOKENS)

    val externalAiReadOnly: StateFlow<Boolean> = aiSettingsRepository.externalAiReadOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun selectExternalAiReadOnly(readOnly: Boolean) {
        viewModelScope.launch { aiSettingsRepository.selectExternalAiReadOnly(readOnly) }
    }

    val localContextLength: StateFlow<Int> = aiSettingsRepository.localContextLength
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_LOCAL_CONTEXT_LENGTH)

    private val _installedLocalModels = MutableStateFlow<List<InstalledLocalModel>>(emptyList())
    val installedLocalModels: StateFlow<List<InstalledLocalModel>> = _installedLocalModels.asStateFlow()

    private val _selectedLocalModelFileName = MutableStateFlow("")
    val selectedLocalModelFileName: StateFlow<String> = _selectedLocalModelFileName.asStateFlow()

    private val _pendingDeletionLocalModelFileNames = MutableStateFlow<Set<String>>(emptySet())
    val pendingDeletionLocalModelFileNames: StateFlow<Set<String>> = _pendingDeletionLocalModelFileNames.asStateFlow()

    fun refreshInstalledLocalModels() {
        val models = aiSettingsRepository.getInstalledLocalModels()
        _installedLocalModels.value = models
        _selectedLocalModelFileName.value = aiSettingsRepository.getSelectedLocalModelFileName()
        _pendingDeletionLocalModelFileNames.value = models
            .map { it.fileName }
            .filter { hasPendingModelDeletion(it) }
            .toSet()
    }

    val sessions: StateFlow<List<ChatSession>> = chatSessionRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private var currentSessionCreatedAt: Long? = null
    private var currentSessionSpaceId: String? = null
    private val _editingMessageId = MutableStateFlow<String?>(null)

    private val _embeddingSetupState = MutableStateFlow<EmbeddingSetupState>(EmbeddingSetupState.Checking)
    val embeddingSetupState: StateFlow<EmbeddingSetupState> = _embeddingSetupState.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        refreshModelAvailability()
        refreshEmbeddingSetupState()
        refreshInstalledLocalModels()
        reattachToRunningDownloadsIfAny()
        observeChatSessionSyncEvents()
        observePendingVaultWrites()
        observeVaultToolCalls()
        observeActiveSpaceChanges()
    }

    private fun observeActiveSpaceChanges() {
        viewModelScope.launch {
            activeSpaceStore.activeSpaceId.drop(1).collect {
                activeGenerationJob?.cancel()
                activeGenerationJob = null
                _isLoading.value = false
                clearChat()
            }
        }
    }

    private fun observePendingVaultWrites() {
        viewModelScope.launch {
            vaultPendingWriteEvents.proposed.collect { write ->
                insertBeforeActivePlaceholder(ChatMessage(text = "", isUser = false, pendingVaultWrite = write))
            }
        }
    }

    private fun observeVaultToolCalls() {
        viewModelScope.launch {
            vaultToolCallEvents.calls.collect { call ->
                insertBeforeActivePlaceholder(ChatMessage(text = "", isUser = false, toolCallSummary = call.description))
            }
        }
    }

    private suspend fun insertBeforeActivePlaceholder(message: ChatMessage) {
        val list = _messages.value.toMutableList()
        val insertIndex = list.lastIndex.coerceAtLeast(0)
        list.add(insertIndex, message)
        _messages.value = list
        persistCurrentSession()
    }

    private fun observeChatSessionSyncEvents() {
        viewModelScope.launch {
            com.emberr.domain.util.sync.ChatSyncEventBus.events.collect { changedSessionId ->
                if (changedSessionId != _currentSessionId.value) return@collect
                if (_isLoading.value) return@collect
                loadSession(changedSessionId)
            }
        }
    }

    private fun reattachToRunningDownloadsIfAny() {
        viewModelScope.launch {
            if (modelDownloadScheduler.isEmbeddingDownloadRunning()) {
                attachEmbeddingDownload(modelDownloadScheduler.observeEmbeddingModelDownload())
            }
            if (modelDownloadScheduler.isGeneratorDownloadRunning()) {
                attachGeneratorDownload(modelDownloadScheduler.observeGeneratorModelDownload())
            }
        }
    }

    private fun refreshModelAvailability() {
        viewModelScope.launch {
            _isModelAvailable.value = ragRepository.isModelAvailable()
        }
    }

    private fun refreshEmbeddingSetupState() {
        val hasNothingToSetUp = localAiUnsupportedReason != null
        _embeddingSetupState.value = if (hasNothingToSetUp || ragRepository.isEmbeddingModelAvailable()) {
            EmbeddingSetupState.Ready
        } else {
            EmbeddingSetupState.Required
        }
    }

    fun refreshAiAvailability() {
        val hasTransientSetupState = _embeddingSetupState.value.let { state ->
            state is EmbeddingSetupState.Downloading ||
                state is EmbeddingSetupState.Indexing ||
                state == EmbeddingSetupState.DownloadComplete
        }
        if (!hasTransientSetupState) refreshEmbeddingSetupState()

        refreshInstalledLocalModels()
        refreshModelAvailability()
    }

    fun hasResumableEmbeddingDownload(): Boolean = hasResumableDownload(ModelFileNames.EMBEDDER)

    private var embeddingDownloadJob: Job? = null

    fun downloadEmbeddingModel() {
        if (localAiUnsupportedReason != null) {
            ModelDownloadLog.e("Download blocked because local AI is unsupported: $localAiUnsupportedReason")
            return
        }
        if (_embeddingSetupState.value is EmbeddingSetupState.Downloading) return
        if (ragRepository.isEmbeddingModelAvailable()) {
            _embeddingSetupState.value = EmbeddingSetupState.Ready
            return
        }
        _embeddingSetupState.value = EmbeddingSetupState.Downloading(resumeProgressFraction(ModelFileNames.EMBEDDER))
        attachEmbeddingDownload(modelDownloadScheduler.scheduleEmbeddingModelDownload())
    }

    private fun attachEmbeddingDownload(flow: Flow<ModelDownloadProgress>) {
        if (_embeddingSetupState.value !is EmbeddingSetupState.Downloading) {
            _embeddingSetupState.value = EmbeddingSetupState.Downloading(resumeProgressFraction(ModelFileNames.EMBEDDER))
        }
        embeddingDownloadJob = viewModelScope.launch {
            flow.collect { progress ->
                _embeddingSetupState.value = when (progress) {
                    is ModelDownloadProgress.Downloading -> EmbeddingSetupState.Downloading(progress.fraction)
                    ModelDownloadProgress.Completed -> EmbeddingSetupState.DownloadComplete
                    is ModelDownloadProgress.Failed -> EmbeddingSetupState.DownloadFailed(progress.message)
                    ModelDownloadProgress.Paused -> EmbeddingSetupState.Required
                }
            }
        }
    }

    fun pauseEmbeddingModelDownload() {
        embeddingDownloadJob?.cancel()
        embeddingDownloadJob = null
        modelDownloadScheduler.cancelEmbeddingModelDownload()
        _embeddingSetupState.value = EmbeddingSetupState.Required
    }

    fun proceedAfterEmbeddingModelDownload() {
        viewModelScope.launch {
            _embeddingSetupState.value = EmbeddingSetupState.Indexing(0, 0)
            reindexAllNotesUseCase.execute().collect { progress ->
                _embeddingSetupState.value = EmbeddingSetupState.Indexing(progress.completed, progress.total)
            }
            refreshModelAvailability()
            _embeddingSetupState.value = EmbeddingSetupState.Ready
        }
    }

    fun selectLocalAi() {
        viewModelScope.launch {
            aiSettingsRepository.selectLocalAi()
            _isModelAvailable.value = ragRepository.isModelAvailable()
        }
    }

    fun selectLocalModel(fileName: String) {
        viewModelScope.launch {
            aiSettingsRepository.selectLocalModel(fileName)
            aiSettingsRepository.selectLocalAi()
            refreshInstalledLocalModels()
            _isModelAvailable.value = ragRepository.isModelAvailable()
        }
    }

    fun selectExternalProvider(provider: ExternalAiProvider) {
        viewModelScope.launch {
            aiSettingsRepository.selectExternalAi(provider)
            _isModelAvailable.value = ragRepository.isModelAvailable()
        }
    }

    suspend fun getExternalAiConfig(provider: ExternalAiProvider): ExternalAiProviderConfig? =
        aiSettingsRepository.getProviderConfig(provider)

    fun isLocalAiAvailable(): Boolean = ragRepository.isLocalGeneratorAvailable()

    private val _localGeneratorDownloadProgress = MutableStateFlow<ModelDownloadProgress?>(null)
    val localGeneratorDownloadProgress: StateFlow<ModelDownloadProgress?> = _localGeneratorDownloadProgress.asStateFlow()

    fun hasResumableGeneratorDownload(): Boolean = hasResumableDownload(ModelFileNames.GENERATOR)

    private var generatorDownloadJob: Job? = null

    fun downloadGeneratorModel() {
        val unsupportedReason = localAiUnsupportedReason
        if (unsupportedReason != null) {
            _localGeneratorDownloadProgress.value = ModelDownloadProgress.Failed(unsupportedReason)
            return
        }
        if (_localGeneratorDownloadProgress.value is ModelDownloadProgress.Downloading) return
        if (aiSettingsRepository.getInstalledLocalModels().any { it.fileName == ModelFileNames.GENERATOR }) {
            _localGeneratorDownloadProgress.value = ModelDownloadProgress.Completed
            return
        }
        _localGeneratorDownloadProgress.value = ModelDownloadProgress.Downloading(resumeProgressFraction(ModelFileNames.GENERATOR))
        attachGeneratorDownload(modelDownloadScheduler.scheduleGeneratorModelDownload())
    }

    private fun attachGeneratorDownload(flow: Flow<ModelDownloadProgress>) {
        if (_localGeneratorDownloadProgress.value !is ModelDownloadProgress.Downloading) {
            _localGeneratorDownloadProgress.value = ModelDownloadProgress.Downloading(resumeProgressFraction(ModelFileNames.GENERATOR))
        }
        generatorDownloadJob = viewModelScope.launch {
            flow.collect { progress ->
                _localGeneratorDownloadProgress.value = progress
                if (progress is ModelDownloadProgress.Completed) {
                    aiSettingsRepository.registerLocalModel(
                        fileName = ModelFileNames.GENERATOR,
                        displayName = ModelFileNames.GENERATOR,
                        isBundledDefault = true
                    )
                    aiSettingsRepository.selectLocalModel(ModelFileNames.GENERATOR)
                    refreshInstalledLocalModels()
                }
            }
            refreshModelAvailability()
        }
    }

    fun pauseGeneratorModelDownload() {
        generatorDownloadJob?.cancel()
        generatorDownloadJob = null
        modelDownloadScheduler.cancelGeneratorModelDownload()
        _localGeneratorDownloadProgress.value = ModelDownloadProgress.Paused
    }

    private val _localModelUploadState = MutableStateFlow<LocalModelUploadState>(LocalModelUploadState.Idle)
    val localModelUploadState: StateFlow<LocalModelUploadState> = _localModelUploadState.asStateFlow()

    private val _localModelUploadProgress = MutableStateFlow(0f)
    val localModelUploadProgress: StateFlow<Float> = _localModelUploadProgress.asStateFlow()

    private val _uploadedModelFinetuneWarning = MutableStateFlow<String?>(null)
    val uploadedModelFinetuneWarning: StateFlow<String?> = _uploadedModelFinetuneWarning.asStateFlow()

    fun dismissUploadedModelFinetuneWarning() {
        _uploadedModelFinetuneWarning.value = null
    }

    fun uploadGeneratorModel(pickedPath: String) {
        val unsupportedReason = localAiUnsupportedReason
        if (unsupportedReason != null) {
            _localModelUploadState.value = LocalModelUploadState.Failed(unsupportedReason)
            return
        }
        _localModelUploadState.value = LocalModelUploadState.Uploading
        _localModelUploadProgress.value = 0f
        viewModelScope.launch {
            val requestedName = (localModelUploadManager.resolveDisplayName(pickedPath) ?: displayNameFromPickedPath(pickedPath))
                .ifBlank { "custom-model.gguf" }
            val existingFileNames = aiSettingsRepository.getInstalledLocalModels()
                .map { it.fileName }
                .toSet() + ModelFileNames.EMBEDDER
            val storageFileName = uniqueStorageFileName(requestedName, existingFileNames)

            var succeeded = false
            localModelUploadManager.copyPickedFileToModelPath(pickedPath, storageFileName).collect { progress ->
                when (progress) {
                    is ModelDownloadProgress.Downloading -> _localModelUploadProgress.value = progress.fraction
                    ModelDownloadProgress.Completed -> {
                        succeeded = true
                        _localModelUploadState.value = LocalModelUploadState.Success
                    }
                    is ModelDownloadProgress.Failed -> _localModelUploadState.value = LocalModelUploadState.Failed(progress.message)
                    ModelDownloadProgress.Paused -> Unit
                }
            }

            if (succeeded) {
                aiSettingsRepository.registerLocalModel(
                    fileName = storageFileName,
                    displayName = requestedName,
                    isBundledDefault = false
                )
                aiSettingsRepository.selectLocalModel(storageFileName)
                refreshInstalledLocalModels()
                refreshModelAvailability()
                checkUploadedModelFinetuneType()
            }
        }
    }

    private fun displayNameFromPickedPath(pickedPath: String): String =
        pickedPath.substringAfterLast('/').substringAfterLast("%2F")

    private fun uniqueStorageFileName(requestedName: String, existingFileNames: Set<String>): String {
        if (requestedName !in existingFileNames) return requestedName

        val dotIndex = requestedName.lastIndexOf('.')
        val base = if (dotIndex > 0) requestedName.substring(0, dotIndex) else requestedName
        val extension = if (dotIndex > 0) requestedName.substring(dotIndex) else ""

        var index = 1
        var candidate = "$base($index)$extension"
        while (candidate in existingFileNames) {
            index++
            candidate = "$base($index)$extension"
        }
        return candidate
    }

    private suspend fun checkUploadedModelFinetuneType() {
        val finetuneType = try {
            ragRepository.checkLocalGeneratorFinetuneType()
        } catch (e: Exception) {
            null
        }
        if (finetuneType == null || finetuneType == "base") {
            _uploadedModelFinetuneWarning.value =
                "This model doesn't appear to be instruction/chat-tuned. It may produce poor or incoherent chat replies."
        }
    }

    fun resetLocalModelUploadState() {
        _localModelUploadState.value = LocalModelUploadState.Idle
    }

    fun deleteLocalModel(fileName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { softDeleteModelFile(fileName) }

            if (aiSettingsRepository.getSelectedLocalModelFileName() == fileName) {
                val fallback = aiSettingsRepository.getInstalledLocalModels()
                    .map { it.fileName }
                    .firstOrNull { it != fileName && !hasPendingModelDeletion(it) }
                    .orEmpty()
                aiSettingsRepository.selectLocalModel(fallback)
            }

            refreshInstalledLocalModels()
            refreshModelAvailability()
        }
    }

    fun restoreLocalModel(fileName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { restoreModelFile(fileName) }

            if (aiSettingsRepository.getSelectedLocalModelFileName().isBlank()) {
                aiSettingsRepository.selectLocalModel(fileName)
            }

            refreshInstalledLocalModels()
            refreshModelAvailability()
        }
    }

    fun selectKnowledgeMode(mode: KnowledgeMode) {
        viewModelScope.launch { aiSettingsRepository.selectKnowledgeMode(mode) }
    }

    fun selectMaxOutputTokens(tokens: Int) {
        viewModelScope.launch { aiSettingsRepository.selectMaxOutputTokens(tokens) }
    }

    fun selectLocalContextLength(tokens: Int) {
        viewModelScope.launch { aiSettingsRepository.selectLocalContextLength(tokens) }
    }

    fun saveExternalAiConfig(provider: ExternalAiProvider, config: ExternalAiProviderConfig) {
        viewModelScope.launch {
            aiSettingsRepository.saveProviderConfig(provider, config)
            aiSettingsRepository.selectExternalAi(provider)
            _isModelAvailable.value = ragRepository.isModelAvailable()
        }
    }

    fun deleteExternalAiConfig(provider: ExternalAiProvider) {
        viewModelScope.launch {
            aiSettingsRepository.deleteProviderConfig(provider)
            _isModelAvailable.value = ragRepository.isModelAvailable()
        }
    }

    fun submitQuery(query: String) {
        if (query.isBlank()) return
        if (currentSessionSpaceId == null) currentSessionSpaceId = activeSpaceStore.currentActiveSpaceId()

        val editingId = _editingMessageId.value
        if (editingId != null) {
            _editingMessageId.value = null
            val index = _messages.value.indexOfFirst { it.id == editingId }
            if (index != -1) {
                _messages.value = _messages.value.take(index)
            }
        }

        val conversationHistory = buildConversationHistory()

        _messages.value += ChatMessage(text = query, isUser = true)
        _messages.value += ChatMessage(text = "", isUser = false)

        _isLoading.value = true
        viewModelScope.launch { persistCurrentSession() }

        activeGenerationJob = viewModelScope.launch {
            val thisJob = coroutineContext[Job]
            try {
                ActiveEditorRegistry.forceSyncAndIndexAllForAi()
                ragRepository.queryAiStream(query, conversationHistory).collect { token ->
                    if (activeGenerationJob !== thisJob) return@collect
                    val list = _messages.value.toMutableList()
                    val last = list.last()
                    list[list.lastIndex] = last.copy(text = last.text + token)
                    _messages.value = list
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeGenerationJob === thisJob) {
                    val friendlyMessage = when (e) {
                        is LocalAiUnsupportedException -> e.message
                            ?: "Local AI cannot run on this hardware."
                        is ExternalAiException -> e.message ?: "Sorry, an error occurred."
                        else -> "Sorry, an error occurred: ${e.message}"
                    }
                    val list = _messages.value.toMutableList()
                    val last = list.last()
                    list[list.lastIndex] = last.copy(text = friendlyMessage)
                    _messages.value = list
                }
            } finally {
                if (activeGenerationJob === thisJob) {
                    _isLoading.value = false
                }
                persistCurrentSession()
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _isLoading.value = false

        val list = _messages.value.toMutableList()
        val last = list.lastOrNull()
        val lastIsInertCard = last?.pendingVaultWrite != null || last?.toolCallSummary != null
        if (last != null && !last.isUser && last.text.isEmpty() && !lastIsInertCard) {
            list.removeAt(list.lastIndex)
            _messages.value = list
        }

        viewModelScope.launch { persistCurrentSession() }
    }

    fun beginEditingMessage(messageId: String) {
        _editingMessageId.value = messageId
    }

    fun confirmPendingWrite(messageId: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        val write = message.pendingVaultWrite ?: return
        if (write.status != VaultPendingWriteStatus.PENDING) return

        viewModelScope.launch {
            val result = vaultToolRunner.applyPendingWrite(write)
            val updatedWrite = if (result is VaultToolResult.Failure) {
                write.copy(status = VaultPendingWriteStatus.FAILED, failureReason = result.reason)
            } else {
                write.copy(status = VaultPendingWriteStatus.APPLIED)
            }
            replacePendingWrite(messageId, updatedWrite)
            persistCurrentSession()
        }
    }

    fun rejectPendingWrite(messageId: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        val write = message.pendingVaultWrite ?: return
        if (write.status != VaultPendingWriteStatus.PENDING) return

        replacePendingWrite(messageId, write.copy(status = VaultPendingWriteStatus.REJECTED))
        viewModelScope.launch { persistCurrentSession() }
    }

    private fun replacePendingWrite(messageId: String, updatedWrite: VaultPendingWrite) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfFirst { it.id == messageId }
        if (index == -1) return
        list[index] = list[index].copy(pendingVaultWrite = updatedWrite)
        _messages.value = list
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            val session = chatSessionRepository.getSession(sessionId) ?: return@launch
            _messages.value = session.messages
            _currentSessionId.value = session.id
            currentSessionCreatedAt = session.createdAt
            currentSessionSpaceId = activeSpaceStore.currentActiveSpaceId()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun persistCurrentSession() {
        try {
            val currentMessages = _messages.value
            if (currentMessages.isEmpty()) return
            if (!activeSpaceStore.isActiveSpace(currentSessionSpaceId)) return

            val sessionId = _currentSessionId.value ?: Uuid.random().toString()
            _currentSessionId.value = sessionId

            val now = Clock.System.now().toEpochMilliseconds()
            val createdAt = currentSessionCreatedAt ?: now
            currentSessionCreatedAt = createdAt

            val title = currentMessages.firstOrNull { it.isUser }
                ?.text?.take(TITLE_MAX_CHARS)?.ifBlank { null }
                ?: DEFAULT_SESSION_TITLE

            chatSessionRepository.saveSession(
                ChatSession(
                    id = sessionId,
                    title = title,
                    messages = currentMessages,
                    createdAt = createdAt,
                    updatedAt = now
                )
            )
        } catch (e: Exception) {
            // Best-effort persistence; a save failure shouldn't interrupt the chat.
        }
    }

    private fun buildConversationHistory(): List<ChatTurn> {
        val turns = mutableListOf<ChatTurn>()
        var pendingUserMessage: String? = null

        _messages.value.forEach { message ->
            when {
                message.pendingVaultWrite != null || message.toolCallSummary != null -> Unit
                message.isUser -> pendingUserMessage = message.text
                else -> {
                    val userMessage = pendingUserMessage
                    if (userMessage != null && message.text.isNotBlank()) {
                        turns += ChatTurn(
                            userMessage = userMessage.take(MAX_HISTORY_MESSAGE_CHARS),
                            assistantMessage = message.text.take(MAX_HISTORY_MESSAGE_CHARS)
                        )
                    }
                    pendingUserMessage = null
                }
            }
        }

        val maxTurns = if (aiGenerationMode.value == AiGenerationMode.LOCAL) {
            MAX_HISTORY_TURNS_LOCAL
        } else {
            MAX_HISTORY_TURNS_EXTERNAL
        }

        return turns.takeLast(maxTurns)
    }

    fun clearChat() {
        _messages.value = emptyList()
        _currentSessionId.value = null
        currentSessionCreatedAt = null
        currentSessionSpaceId = null
    }

    fun renameSession(sessionId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { chatSessionRepository.renameSession(sessionId, newTitle.trim()) }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatSessionRepository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) clearChat()
        }
    }

    private companion object {
        const val MAX_HISTORY_TURNS_LOCAL = 20
        const val MAX_HISTORY_TURNS_EXTERNAL = 50
        const val MAX_HISTORY_MESSAGE_CHARS = 4000
        const val TITLE_MAX_CHARS = 60
        const val DEFAULT_SESSION_TITLE = "New Chat"
        const val DEFAULT_MAX_OUTPUT_TOKENS = 1024
        const val DEFAULT_LOCAL_CONTEXT_LENGTH = 4096
    }
}
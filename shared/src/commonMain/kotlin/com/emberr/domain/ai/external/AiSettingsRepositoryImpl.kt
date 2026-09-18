package com.emberr.domain.ai.external

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.dao.SelfHostDeletedApiConfigDao
import com.emberr.data.local.room.entity.SelfHostDeletedApiConfigEntity
import com.emberr.domain.ai.AiGenerationMode
import com.emberr.domain.ai.models.InstalledLocalModel
import com.emberr.domain.ai.KnowledgeMode
import com.emberr.domain.ai.models.hasPendingModelDeletion
import com.emberr.domain.ai.models.modelFileExists
import com.emberr.domain.ai.models.resolveModelPath
import com.emberr.domain.sync.AutoSyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class AiSettingsRepositoryImpl(
    private val settingsManager: SettingsManager,
    private val secureAiKeyStorage: SecureAiKeyStorage,
    private val selfHostDeletedApiConfigDao: SelfHostDeletedApiConfigDao
) : AiSettingsRepository {

    override val aiGenerationMode: Flow<AiGenerationMode> =
        settingsManager.aiGenerationModeFlow.map { raw ->
            AiGenerationMode.entries.find { it.name == raw } ?: AiGenerationMode.LOCAL
        }

    override val selectedExternalAiProvider: Flow<ExternalAiProvider> =
        settingsManager.selectedExternalAiProviderFlow.map { raw ->
            ExternalAiProvider.entries.find { it.name == raw } ?: ExternalAiProvider.OPENAI
        }

    override val knowledgeMode: Flow<KnowledgeMode> =
        settingsManager.knowledgeModeFlow.map { raw ->
            KnowledgeMode.entries.find { it.name == raw } ?: KnowledgeMode.DEFAULT
        }

    override val maxOutputTokens: Flow<Int> = settingsManager.maxOutputTokensFlow

    override val localContextLength: Flow<Int> = settingsManager.localContextLengthFlow

    override val externalAiReadOnly: Flow<Boolean> = settingsManager.externalAiReadOnlyFlow

    override suspend fun selectExternalAiReadOnly(readOnly: Boolean) {
        withContext(Dispatchers.IO) { settingsManager.saveExternalAiReadOnly(readOnly) }
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getProviderConfig(provider: ExternalAiProvider): ExternalAiProviderConfig? =
        withContext(Dispatchers.IO) { secureAiKeyStorage.getConfig(provider) }

    override suspend fun saveProviderConfig(provider: ExternalAiProvider, config: ExternalAiProviderConfig) {
        withContext(Dispatchers.IO) { secureAiKeyStorage.saveConfig(provider, config) }
        AutoSyncTrigger.requestSync()
    }

    override suspend fun deleteProviderConfig(provider: ExternalAiProvider) {
        withContext(Dispatchers.IO) {
            secureAiKeyStorage.clearConfig(provider)
            selfHostDeletedApiConfigDao.upsertTombstone(
                SelfHostDeletedApiConfigEntity(provider = provider.name, deletedAt = System.currentTimeMillis())
            )
            val wasActiveProvider = aiGenerationMode.first() == AiGenerationMode.EXTERNAL &&
                selectedExternalAiProvider.first() == provider
            if (wasActiveProvider) selectLocalAi()
        }
        AutoSyncTrigger.requestSync()
    }

    override suspend fun applyRemoteProviderConfigDeletion(provider: ExternalAiProvider, deletedAt: Long) {
        withContext(Dispatchers.IO) {
            secureAiKeyStorage.clearConfig(provider)
            selfHostDeletedApiConfigDao.upsertTombstone(
                SelfHostDeletedApiConfigEntity(provider = provider.name, deletedAt = deletedAt)
            )
            val wasActiveProvider = aiGenerationMode.first() == AiGenerationMode.EXTERNAL &&
                selectedExternalAiProvider.first() == provider
            if (wasActiveProvider) selectLocalAi()
        }
    }

    override suspend fun selectLocalAi() {
        withContext(Dispatchers.IO) { settingsManager.saveAiGenerationMode(AiGenerationMode.LOCAL.name) }
    }

    override suspend fun selectExternalAi(provider: ExternalAiProvider) {
        withContext(Dispatchers.IO) {
            settingsManager.saveSelectedExternalAiProvider(provider.name)
            settingsManager.saveAiGenerationMode(AiGenerationMode.EXTERNAL.name)
        }
    }

    override suspend fun selectKnowledgeMode(mode: KnowledgeMode) {
        withContext(Dispatchers.IO) { settingsManager.saveKnowledgeMode(mode.name) }
    }

    override suspend fun selectMaxOutputTokens(tokens: Int) {
        withContext(Dispatchers.IO) { settingsManager.saveMaxOutputTokens(tokens) }
    }

    override suspend fun selectLocalContextLength(tokens: Int) {
        withContext(Dispatchers.IO) { settingsManager.saveLocalContextLength(tokens) }
    }

    override fun getInstalledLocalModels(): List<InstalledLocalModel> {
        val stored = try {
            json.decodeFromString<List<InstalledLocalModel>>(settingsManager.getInstalledLocalModelsJson())
        } catch (cause: SerializationException) {
            emptyList()
        }

        val reconciled = stored.filter { model ->
            modelFileExists(resolveModelPath(model.fileName)) || hasPendingModelDeletion(model.fileName)
        }

        if (reconciled.size != stored.size) {
            settingsManager.saveInstalledLocalModelsJson(json.encodeToString(reconciled))
            if (reconciled.none { it.fileName == settingsManager.getSelectedLocalModelFileName() }) {
                settingsManager.saveSelectedLocalModelFileName(reconciled.firstOrNull()?.fileName.orEmpty())
            }
        }

        return reconciled
    }

    override fun getSelectedLocalModelFileName(): String = settingsManager.getSelectedLocalModelFileName()

    override suspend fun registerLocalModel(fileName: String, displayName: String, isBundledDefault: Boolean) {
        withContext(Dispatchers.IO) {
            val updated = getInstalledLocalModels()
                .filterNot { it.fileName == fileName } +
                InstalledLocalModel(
                    fileName = fileName,
                    displayName = displayName,
                    isBundledDefault = isBundledDefault,
                    installedAt = System.currentTimeMillis()
                )
            settingsManager.saveInstalledLocalModelsJson(json.encodeToString(updated))

            if (getSelectedLocalModelFileName().isBlank()) {
                settingsManager.saveSelectedLocalModelFileName(fileName)
            }
        }
    }

    override suspend fun removeLocalModel(fileName: String) {
        withContext(Dispatchers.IO) {
            val remaining = getInstalledLocalModels().filterNot { it.fileName == fileName }
            settingsManager.saveInstalledLocalModelsJson(json.encodeToString(remaining))

            if (getSelectedLocalModelFileName() == fileName) {
                settingsManager.saveSelectedLocalModelFileName(remaining.firstOrNull()?.fileName.orEmpty())
            }
        }
    }

    override suspend fun selectLocalModel(fileName: String) {
        withContext(Dispatchers.IO) { settingsManager.saveSelectedLocalModelFileName(fileName) }
    }
}

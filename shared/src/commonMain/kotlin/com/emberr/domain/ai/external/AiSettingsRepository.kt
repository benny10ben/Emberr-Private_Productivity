package com.emberr.domain.ai.external

import com.emberr.domain.ai.AiGenerationMode
import com.emberr.domain.ai.models.InstalledLocalModel
import com.emberr.domain.ai.KnowledgeMode
import kotlinx.coroutines.flow.Flow

interface AiSettingsRepository {
    val aiGenerationMode: Flow<AiGenerationMode>
    val selectedExternalAiProvider: Flow<ExternalAiProvider>
    val knowledgeMode: Flow<KnowledgeMode>
    val maxOutputTokens: Flow<Int>
    val localContextLength: Flow<Int>
    suspend fun getProviderConfig(provider: ExternalAiProvider): ExternalAiProviderConfig?
    suspend fun saveProviderConfig(provider: ExternalAiProvider, config: ExternalAiProviderConfig)
    suspend fun deleteProviderConfig(provider: ExternalAiProvider)
    suspend fun applyRemoteProviderConfigDeletion(provider: ExternalAiProvider, deletedAt: Long)
    suspend fun selectLocalAi()
    suspend fun selectExternalAi(provider: ExternalAiProvider)
    suspend fun selectKnowledgeMode(mode: KnowledgeMode)
    suspend fun selectMaxOutputTokens(tokens: Int)
    suspend fun selectLocalContextLength(tokens: Int)

    fun getInstalledLocalModels(): List<InstalledLocalModel>
    fun getSelectedLocalModelFileName(): String
    suspend fun registerLocalModel(fileName: String, displayName: String, isBundledDefault: Boolean)
    suspend fun removeLocalModel(fileName: String)
    suspend fun selectLocalModel(fileName: String)
}

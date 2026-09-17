package com.emberr.domain.ai

import com.emberr.domain.ai.chat.ChatTurn
import com.emberr.domain.ai.external.AiSettingsRepository
import com.emberr.domain.ai.external.ExternalAiEngine
import com.emberr.database.EmberrDatabase
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.util.eventbus.AiEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.sqrt

class RagRepository(
    private val database: EmberrDatabase,
    private val localAiEngine: LocalAiEngine,
    private val externalAiEngine: ExternalAiEngine,
    private val aiSettingsRepository: AiSettingsRepository,
    private val activeSpaceStore: ActiveSpaceStore
) {
    val localAiUnsupportedReason: String? get() = localAiEngine.unsupportedHardwareReason

    private val canSearchNoteKnowledge: Boolean get() = localAiUnsupportedReason == null

    fun queryAiStream(userQuestion: String, conversationHistory: List<ChatTurn> = emptyList()): Flow<String> = flow {
        val knowledgeMode = aiSettingsRepository.knowledgeMode.first()
        val generationMode = aiSettingsRepository.aiGenerationMode.first()
        val maxOutputTokens = aiSettingsRepository.maxOutputTokens.first()
        val totalContextTokens = when (generationMode) {
            AiGenerationMode.LOCAL -> aiSettingsRepository.localContextLength.first()
            AiGenerationMode.EXTERNAL -> AiContextWindows.EXTERNAL_TOKENS
        }

        if (knowledgeMode == KnowledgeMode.WORLD_ONLY || !canSearchNoteKnowledge) {
            val planned = PromptBudgetPlanner.plan(
                totalContextTokens = totalContextTokens,
                outputReservationTokens = maxOutputTokens,
                systemPrompt = WORLD_ONLY_SYSTEM_PROMPT,
                userQuestion = userQuestion,
                candidateHistory = conversationHistory,
                candidateChunks = emptyList()
            )
            activeGenerationEngine().generateResponseStream(
                systemPrompt = WORLD_ONLY_SYSTEM_PROMPT,
                userQuestion = userQuestion,
                contextBlock = "",
                conversationHistory = planned.history
            ).collect { token -> emit(token) }
            return@flow
        }

        val queryVector = localAiEngine.generateEmbedding(userQuestion)

        val allBlocks = database.vectorStoreQueries
            .getBlocksInSpace(activeSpaceStore.currentActiveSpaceId())
            .executeAsList()

        if (allBlocks.isEmpty()) {
            emit("I don't have any indexed notes yet. Try writing something first.")
            return@flow
        }

        val currentNoteId = AiEventBus.activeNoteId

        val lowerQuery = userQuestion.lowercase()
        val isSummarizationQuery = lowerQuery.containsAny(
            "summarize", "summary", "what did i write", "what have i written",
            "what's in", "whats in", "give me an overview", "overview of",
            "tell me about my", "recap"
        )
        val isCrossNoteQuery = lowerQuery.containsAny(
            "recent notes", "all notes", "across my notes", "my notes",
            "everything i wrote", "what have i been", "what am i working on"
        )
        val isCurrentNoteQuery = lowerQuery.containsAny(
            "this note", "current note", "summarize this", "what's in this note",
            "whats in this note"
        )

        val activeNoteChunks = if (currentNoteId != null) {
            allBlocks
                .filter { it.note_id == currentNoteId }
                .map { it.chunk_text }
        } else emptyList()

        // Semantic search across other notes
        val threshold = when {
            isSummarizationQuery || isCrossNoteQuery -> 0.15f
            else -> 0.25f
        }
        val resultCount = when {
            isCrossNoteQuery -> 20
            isSummarizationQuery -> 12
            else -> 6
        }

        val semanticChunks = allBlocks
            .filter { it.note_id != currentNoteId }
            .map { row ->
                val score = cosineSimilarity(queryVector, parseVector(row.embedding))
                Triple(row.chunk_text, row.note_id, score)
            }
            .filter { it.third > threshold }
            .sortedByDescending { it.third }
            .take(resultCount)
            .map { it.first }

        val namedNoteChunks = if (isSummarizationQuery && !isCurrentNoteQuery) {
            val nameHint = extractNoteNameHint(lowerQuery)
            if (nameHint != null) {
                allBlocks
                    .filter { it.note_id != currentNoteId }
                    .filter { it.chunk_text.lowercase().contains(nameHint) }
                    .map { it.chunk_text }
                    .take(8)
            } else emptyList()
        } else emptyList()

        val totalLimit = when {
            isCrossNoteQuery -> 30
            isSummarizationQuery -> 20
            else -> 15
        }

        val combinedChunks = (activeNoteChunks + namedNoteChunks + semanticChunks)
            .distinct()
            .take(totalLimit)

        if (combinedChunks.isEmpty()) {
            emit("I couldn't find any relevant information in your notes to answer that.")
            return@flow
        }

        val systemInstructions = if (knowledgeMode == KnowledgeMode.NOTES_ONLY) {
            NOTES_ONLY_SYSTEM_PROMPT
        } else {
            DEFAULT_SYSTEM_PROMPT
        }

        val planned = PromptBudgetPlanner.plan(
            totalContextTokens = totalContextTokens,
            outputReservationTokens = maxOutputTokens,
            systemPrompt = systemInstructions,
            userQuestion = userQuestion,
            candidateHistory = conversationHistory,
            candidateChunks = combinedChunks
        )

        val contextBlock = planned.contextChunks.joinToString("\n\n") { chunk ->
            "--- Note Fragment ---\n$chunk"
        }

        activeGenerationEngine().generateResponseStream(
            systemPrompt = systemInstructions,
            userQuestion = userQuestion,
            contextBlock = contextBlock,
            conversationHistory = planned.history
        ).collect { token -> emit(token) }

    }.flowOn(Dispatchers.Default)

    private fun extractNoteNameHint(query: String): String? {
        val patterns = listOf(
            Regex("summarize(?:\\s+my)?\\s+(\\w+)(?:\\s+note)?"),
            Regex("what(?:'s|\\s+is)\\s+in(?:\\s+the)?\\s+(\\w+)(?:\\s+note)?"),
            Regex("tell me about(?:\\s+my)?\\s+(\\w+)(?:\\s+note)?"),
            Regex("overview of(?:\\s+my)?\\s+(\\w+)(?:\\s+note)?"),
            Regex("about(?:\\s+my)?\\s+(\\w+)(?:\\s+note)")
        )
        for (pattern in patterns) {
            val match = pattern.find(query)
            val candidate = match?.groupValues?.getOrNull(1)
            if (candidate != null && candidate !in setOf(
                    "my", "the", "a", "this", "that", "recent", "all", "notes", "note"
                )) {
                return candidate
            }
        }
        return null
    }

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { this.contains(it) }

    private fun parseVector(jsonString: String): List<Float> {
        return jsonString
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().toFloat() }
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        var dot = 0f; var norm1 = 0f; var norm2 = 0f
        val size = minOf(v1.size, v2.size)
        for (i in 0 until size) {
            dot += v1[i] * v2[i]; norm1 += v1[i] * v1[i]; norm2 += v2[i] * v2[i]
        }
        if (norm1 == 0f || norm2 == 0f) return 0f
        return dot / (sqrt(norm1) * sqrt(norm2))
    }

    suspend fun isModelAvailable(): Boolean {
        return try {
            val needsEmbedder = canSearchNoteKnowledge &&
                    aiSettingsRepository.knowledgeMode.first() != KnowledgeMode.WORLD_ONLY
            if (needsEmbedder && !localAiEngine.isEmbeddingModelAvailable()) return false

            when (aiSettingsRepository.aiGenerationMode.first()) {
                AiGenerationMode.LOCAL -> localAiEngine.isGeneratorModelAvailable()
                AiGenerationMode.EXTERNAL -> externalAiEngine.isModelAvailable()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isLocalGeneratorAvailable(): Boolean = localAiEngine.isGeneratorModelAvailable()

    suspend fun checkLocalGeneratorFinetuneType(): String? = localAiEngine.checkGeneratorFinetuneType()

    fun isEmbeddingModelAvailable(): Boolean = localAiEngine.isEmbeddingModelAvailable()

    private suspend fun activeGenerationEngine(): AiGenerationEngine = when (aiSettingsRepository.aiGenerationMode.first()) {
        AiGenerationMode.LOCAL -> localAiEngine
        AiGenerationMode.EXTERNAL -> externalAiEngine
    }

    private companion object {
        val DEFAULT_SYSTEM_PROMPT = """
            You are Emberr's AI assistant embedded in a note-taking app, answering questions using the user's notes plus your own general knowledge.

            Guidelines:
            - Use the provided Note Fragments below as the source of truth for anything specific to the user's own notes, tasks, or plans.
            - You may use your general knowledge to interpret, classify, or add helpful context to what's in the fragments (e.g. recognizing whether a title is a movie or TV show, explaining a concept mentioned in a note) — but do not invent facts about the user's own notes that aren't actually there.
            - For summarization requests ("summarize", "what did I write about", "overview"),
              synthesize the key points from all provided fragments into a concise summary.
            - For "recent notes" or "what have I been working on", describe the main topics
              across all the fragments provided, organized by theme or note source if possible.
            - For "summarize [note name]" requests, focus on fragments from that specific note.
            - If the question is about the user's own notes and truly no relevant fragment exists,
              say so honestly rather than making something up about their notes.
            - Be concise. Do not mention "fragments" or "chunks" in your response — speak naturally.
        """.trimIndent()

        val NOTES_ONLY_SYSTEM_PROMPT = """
            You are Emberr's offline AI assistant embedded in a note-taking app.
            Answer the user's question using ONLY the provided Note Fragments below.

            Guidelines:
            - For specific questions, find the answer in the fragments and respond directly.
            - For summarization requests ("summarize", "what did I write about", "overview"),
              synthesize the key points from all provided fragments into a concise summary.
            - For "recent notes" or "what have I been working on", describe the main topics
              across all the fragments provided, organized by theme or note source if possible.
            - For "summarize [note name]" requests, focus on fragments from that specific note.
            - If truly no relevant information exists in the fragments, say:
              "I cannot find relevant information in your notes."
            - Be concise. Do not invent facts not present in the fragments.
            - Do not mention "fragments" or "chunks" in your response — speak naturally.
        """.trimIndent()

        val WORLD_ONLY_SYSTEM_PROMPT = """
            You are Emberr's AI assistant, acting as a general-purpose conversational assistant.
            Answer the user's question using your own general knowledge. You do not have access to the user's notes in this mode.
            Be concise and speak naturally.
        """.trimIndent()
    }
}
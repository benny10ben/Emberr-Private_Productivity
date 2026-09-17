package com.emberr.domain.space

import com.emberr.data.local.room.DEFAULT_SPACE_ID
import com.emberr.data.local.room.DEFAULT_SPACE_NAME
import com.emberr.data.local.room.PLACEHOLDER_SPACE_UPDATED_AT
import com.emberr.data.local.room.SpaceDao
import com.emberr.data.local.room.SpaceEntity
import com.emberr.domain.ai.chat.ChatSessionRepository
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.sync.AutoSyncTrigger
import com.emberr.domain.vault.VaultMirrorTrigger
import kotlinx.coroutines.flow.Flow
import java.util.UUID

private const val SHORT_SPACE_ID_LENGTH = 8

class SpaceRepository(
    private val spaceDao: SpaceDao,
    private val activeSpaceStore: ActiveSpaceStore,
    private val noteRepository: NoteRepository,
    private val chatSessionRepository: ChatSessionRepository
) {

    fun observeSpaces(): Flow<List<SpaceEntity>> = spaceDao.getAllSpaces()

    suspend fun getSpaces(): List<SpaceEntity> = spaceDao.getAllSpacesOnce()

    suspend fun getSpace(spaceId: String): SpaceEntity? = spaceDao.getSpaceById(spaceId)

    suspend fun prepareSpacesForLaunch() {
        val existingSpaces = spaceDao.getAllSpacesOnce()
        if (existingSpaces.isEmpty()) {
            val now = System.currentTimeMillis()
            spaceDao.insertOrUpdateSpace(
                SpaceEntity(
                    spaceId = DEFAULT_SPACE_ID,
                    displayName = DEFAULT_SPACE_NAME,
                    createdAt = now,
                    updatedAt = now
                )
            )
            activeSpaceStore.setActiveSpace(DEFAULT_SPACE_ID)
            return
        }

        val activeSpaceStillExists = existingSpaces.any { it.spaceId == activeSpaceStore.currentActiveSpaceId() }
        if (!activeSpaceStillExists) {
            activeSpaceStore.setActiveSpace(existingSpaces.first().spaceId)
        }
    }

    suspend fun createSpace(displayName: String): String {
        val cleanedName = displayName.trim()
        require(cleanedName.isNotEmpty()) { "A space needs a name." }

        val now = System.currentTimeMillis()
        val newSpaceId = UUID.randomUUID().toString()
        spaceDao.insertOrUpdateSpace(
            SpaceEntity(
                spaceId = newSpaceId,
                displayName = cleanedName,
                createdAt = now,
                sortOrder = spaceDao.countSpaces(),
                updatedAt = now
            )
        )
        AutoSyncTrigger.requestSync()
        VaultMirrorTrigger.requestFullRefresh()
        return newSpaceId
    }

    suspend fun applyRemoteSpace(space: SpaceEntity) {
        val local = spaceDao.getSpaceById(space.spaceId)
        if (local == null || space.updatedAt > local.updatedAt) {
            spaceDao.insertOrUpdateSpace(space)
            moveActiveSpaceIfItNoLongerExists()
            VaultMirrorTrigger.requestFullRefresh()
        }
    }

    suspend fun moveActiveSpaceIfItNoLongerExists() {
        val liveSpaces = spaceDao.getAllSpacesOnce()
        if (liveSpaces.any { it.spaceId == activeSpaceStore.currentActiveSpaceId() }) return

        prepareSpacesForLaunch()
    }

    suspend fun ensureSpaceExists(spaceId: String, displayName: String? = null) {
        if (spaceId.isBlank()) return
        if (spaceDao.getSpaceById(spaceId) != null) return

        spaceDao.insertOrUpdateSpace(
            SpaceEntity(
                spaceId = spaceId,
                displayName = displayName?.trim()?.ifEmpty { null }
                    ?: "Space ${spaceId.take(SHORT_SPACE_ID_LENGTH)}",
                createdAt = System.currentTimeMillis(),
                sortOrder = spaceDao.countSpaces(),
                updatedAt = PLACEHOLDER_SPACE_UPDATED_AT
            )
        )
        VaultMirrorTrigger.requestFullRefresh()
    }

    suspend fun renameSpace(spaceId: String, displayName: String) {
        val cleanedName = displayName.trim()
        require(cleanedName.isNotEmpty()) { "A space needs a name." }
        checkNotNull(spaceDao.getSpaceById(spaceId)) { "Cannot rename a space that does not exist: $spaceId" }

        spaceDao.renameSpace(
            spaceId = spaceId,
            displayName = cleanedName,
            updatedAt = System.currentTimeMillis()
        )
        AutoSyncTrigger.requestSync()
        VaultMirrorTrigger.requestFullRefresh()
    }

    suspend fun reorderSpaces(orderedSpaceIds: List<String>) {
        if (orderedSpaceIds.isEmpty()) return

        val now = System.currentTimeMillis()
        orderedSpaceIds.forEachIndexed { position, spaceId ->
            spaceDao.updateSpaceSortOrder(spaceId = spaceId, sortOrder = position, updatedAt = now)
        }
        AutoSyncTrigger.requestSync()
    }

    suspend fun deleteSpace(spaceId: String) {
        val remainingSpaces = spaceDao.getAllSpacesOnce().filter { it.spaceId != spaceId }
        check(remainingSpaces.isNotEmpty()) { "The last remaining space cannot be deleted." }

        if (activeSpaceStore.currentActiveSpaceId() == spaceId) {
            activeSpaceStore.setActiveSpace(remainingSpaces.first().spaceId)
        }

        noteRepository.deleteAllContentInSpace(spaceId)
        chatSessionRepository.deleteSessionsInSpace(spaceId)
        spaceDao.markSpaceDeleted(spaceId, System.currentTimeMillis())

        AutoSyncTrigger.requestSync()
        VaultMirrorTrigger.requestFullRefresh()
    }
}

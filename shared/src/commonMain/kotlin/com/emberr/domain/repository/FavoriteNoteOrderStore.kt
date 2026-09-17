package com.emberr.domain.repository

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.model.FavoriteNoteOrder
import com.emberr.domain.model.FavoriteNoteOrderBySpace
import com.emberr.domain.space.ActiveSpaceStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteNoteOrderStore(
    private val settingsManager: SettingsManager,
    private val activeSpaceStore: ActiveSpaceStore
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val orderedNoteIdsFlow: Flow<List<String>> = combine(
        settingsManager.favoriteNoteOrderJsonFlow,
        activeSpaceStore.activeSpaceId
    ) { rawJson, spaceId -> decodeAllOrders(rawJson).orderFor(spaceId).noteIds }

    fun getOrder(): FavoriteNoteOrder = getOrderInSpace(activeSpaceStore.currentActiveSpaceId())

    fun getOrderInSpace(spaceId: String): FavoriteNoteOrder = getAllOrders().orderFor(spaceId)

    fun getAllOrders(): FavoriteNoteOrderBySpace =
        decodeAllOrders(settingsManager.getFavoriteNoteOrderJson())

    fun saveOrder(noteIds: List<String>, updatedAt: Long = System.currentTimeMillis()) {
        val spaceId = activeSpaceStore.currentActiveSpaceId()
        val updated = FavoriteNoteOrder(noteIds = noteIds, updatedAt = updatedAt)
        persistAllOrders(
            FavoriteNoteOrderBySpace(getAllOrders().ordersBySpaceId + (spaceId to updated))
        )
    }

    fun applyRemoteOrders(remoteOrders: FavoriteNoteOrderBySpace): Boolean {
        val localOrders = getAllOrders().ordersBySpaceId
        val merged = localOrders.toMutableMap()
        var changedAnything = false

        for ((spaceId, remoteOrder) in remoteOrders.ordersBySpaceId) {
            val localOrder = localOrders[spaceId]
            if (localOrder == null || remoteOrder.updatedAt > localOrder.updatedAt) {
                merged[spaceId] = remoteOrder
                changedAnything = true
            }
        }

        if (changedAnything) persistAllOrders(FavoriteNoteOrderBySpace(merged))
        return changedAnything
    }

    private fun FavoriteNoteOrderBySpace.orderFor(spaceId: String): FavoriteNoteOrder =
        ordersBySpaceId[spaceId] ?: FavoriteNoteOrder()

    private fun persistAllOrders(orders: FavoriteNoteOrderBySpace) {
        settingsManager.saveFavoriteNoteOrderJson(json.encodeToString(orders))
    }

    private fun decodeAllOrders(rawJson: String): FavoriteNoteOrderBySpace {
        if (rawJson.isBlank()) return FavoriteNoteOrderBySpace()
        return try {
            json.decodeFromString<FavoriteNoteOrderBySpace>(rawJson)
        } catch (cause: Exception) {
            FavoriteNoteOrderBySpace()
        }
    }
}

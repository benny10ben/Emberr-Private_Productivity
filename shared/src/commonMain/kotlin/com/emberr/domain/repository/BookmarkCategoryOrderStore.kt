package com.emberr.domain.repository

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.model.BookmarkCategoryOrder
import com.emberr.domain.model.BookmarkCategoryOrderBySpace
import com.emberr.domain.space.ActiveSpaceStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkCategoryOrderStore(
    private val settingsManager: SettingsManager,
    private val activeSpaceStore: ActiveSpaceStore
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val orderFlow: Flow<BookmarkCategoryOrder> = combine(
        settingsManager.bookmarkCategoryOrderJsonFlow,
        activeSpaceStore.activeSpaceId
    ) { rawJson, spaceId -> decodeAllOrders(rawJson).orderFor(spaceId) }

    fun getOrder(): BookmarkCategoryOrder = getOrderInSpace(activeSpaceStore.currentActiveSpaceId())

    fun getOrderInSpace(spaceId: String): BookmarkCategoryOrder = getAllOrders().orderFor(spaceId)

    fun getAllOrders(): BookmarkCategoryOrderBySpace =
        decodeAllOrders(settingsManager.getBookmarkCategoryOrderJson())

    fun saveOrder(categories: List<String>, updatedAt: Long = System.currentTimeMillis()) {
        val spaceId = activeSpaceStore.currentActiveSpaceId()
        val updated = BookmarkCategoryOrder(categories = categories, updatedAt = updatedAt)
        persistAllOrders(
            BookmarkCategoryOrderBySpace(getAllOrders().ordersBySpaceId + (spaceId to updated))
        )
    }

    fun applyRemoteOrders(remoteOrders: BookmarkCategoryOrderBySpace): Boolean {
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

        if (changedAnything) persistAllOrders(BookmarkCategoryOrderBySpace(merged))
        return changedAnything
    }

    private fun BookmarkCategoryOrderBySpace.orderFor(spaceId: String): BookmarkCategoryOrder =
        ordersBySpaceId[spaceId] ?: BookmarkCategoryOrder()

    private fun persistAllOrders(orders: BookmarkCategoryOrderBySpace) {
        settingsManager.saveBookmarkCategoryOrderJson(json.encodeToString(orders))
    }

    private fun decodeAllOrders(rawJson: String): BookmarkCategoryOrderBySpace {
        if (rawJson.isBlank()) return BookmarkCategoryOrderBySpace()
        return try {
            json.decodeFromString<BookmarkCategoryOrderBySpace>(rawJson)
        } catch (cause: Exception) {
            BookmarkCategoryOrderBySpace()
        }
    }
}

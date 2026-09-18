package com.emberr.domain.selfhost.sync

import com.emberr.data.local.room.entity.DEFAULT_SPACE_ID
import kotlinx.serialization.Serializable

@Serializable
enum class SelfHostEntryType { NOTE, DAILY, MEDIA, CHAT_SESSION }

@Serializable
data class SelfHostManifestEntry(
    val entryId: String,
    val entryType: SelfHostEntryType,
    val spaceId: String = DEFAULT_SPACE_ID,
    val updatedAt: Long,
    val dateString: String? = null,
    val isDeleted: Boolean = false,
    val orphanedAt: Long? = null
)

@Serializable
data class SelfHostManifest(
    val schemaVersion: Int = 1,
    val entries: List<SelfHostManifestEntry> = emptyList()
)

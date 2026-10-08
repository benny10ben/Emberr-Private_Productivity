package com.emberr.domain.selfhost.sync

import com.emberr.data.local.room.entity.DEFAULT_SPACE_ID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Raise this by one in any release that adds a new kind of synced item or changes what is in the
// shared list. Devices on an older version then pause self-host sync and ask to be updated instead
// of failing to read the list or dropping what they don't understand when they write it back.
const val SELF_HOST_MANIFEST_SCHEMA_VERSION = 1

const val SELF_HOST_UPDATE_REQUIRED_MESSAGE =
    "Another device synced with a newer version of Emberr. Update Emberr on this device to resume self-host sync."

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
    val orphanedAt: Long? = null,
    val mediaFileNames: Set<String> = emptySet(),
    val trashedAt: Long? = null,
    val mediaSizeBytes: Long? = null
)

@Serializable
data class SelfHostManifest(
    val schemaVersion: Int = SELF_HOST_MANIFEST_SCHEMA_VERSION,
    val entries: List<SelfHostManifestEntry> = emptyList()
)

@Serializable
private data class SelfHostManifestVersion(val schemaVersion: Int = 1)

class SelfHostManifestTooNewException(val serverSchemaVersion: Int) : Exception(SELF_HOST_UPDATE_REQUIRED_MESSAGE)

private val manifestDecodingJson = Json { ignoreUnknownKeys = true }

fun decodeSelfHostManifest(rawJson: String): SelfHostManifest {
    val serverSchemaVersion = manifestDecodingJson
        .decodeFromString(SelfHostManifestVersion.serializer(), rawJson)
        .schemaVersion
    if (serverSchemaVersion > SELF_HOST_MANIFEST_SCHEMA_VERSION) {
        throw SelfHostManifestTooNewException(serverSchemaVersion)
    }
    return manifestDecodingJson.decodeFromString(SelfHostManifest.serializer(), rawJson)
}

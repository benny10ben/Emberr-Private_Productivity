// Resolves which directory inside the vault belongs to which space.

package com.emberr.domain.vault

import com.emberr.data.local.room.SpaceDao
import com.emberr.domain.space.ActiveSpaceStore
import java.io.File

data class ActiveVaultSpace(val spaceId: String, val directory: File)

class VaultSpaceDirectories(
    private val vaultRootDirectory: File,
    private val spaceDao: SpaceDao,
    private val activeSpaceStore: ActiveSpaceStore
) {

    suspend fun folderNamesBySpaceId(): Map<String, String> =
        VaultPaths.spaceFolderNamesBySpaceId(spaceDao.getAllSpacesOnce())

    suspend fun directoryFor(spaceId: String): File? =
        folderNamesBySpaceId()[spaceId]?.let { name -> File(vaultRootDirectory, name) }

    suspend fun spaceIdForFolderName(directoryName: String): String? =
        folderNamesBySpaceId().entries
            .firstOrNull { (_, name) -> name.equals(directoryName, ignoreCase = true) }
            ?.key

    fun spaceIdRecordedIn(directoryName: String): String? {
        val markerFile = File(File(vaultRootDirectory, directoryName), VaultPaths.SPACE_ID_FILE_NAME)
        return try {
            if (!markerFile.isFile) null else markerFile.readText().trim().takeIf { it.isNotBlank() }
        } catch (cause: Exception) {
            VaultLog.e("could not read the space id in $directoryName: ${cause.message}")
            null
        }
    }

    suspend fun activeSpace(): ActiveVaultSpace? {
        val activeSpaceId = activeSpaceStore.currentActiveSpaceId()
        val directory = directoryFor(activeSpaceId) ?: return null
        if (!directory.isDirectory) directory.mkdirs()
        return ActiveVaultSpace(spaceId = activeSpaceId, directory = directory)
    }
}

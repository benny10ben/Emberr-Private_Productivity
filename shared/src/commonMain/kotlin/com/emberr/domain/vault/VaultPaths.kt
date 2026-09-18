// Folder paths, safe file names and the naming rules the vault folder follows.

package com.emberr.domain.vault

import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.SpaceEntity

private const val MAX_FILE_NAME_LENGTH = 80
private val illegalFileNameCharacters = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

object VaultPaths {

    const val SPACE_FOLDER_SUFFIX = "(Space)"
    const val SPACE_ID_FILE_NAME = ".emberr-space"
    const val DAILY_FOLDER_NAME = "Daily"
    const val SUB_NOTE_FOLDER_NAME = "Subnotes"
    const val VAULT_RULES_FILE_NAME = "CLAUDE.md"
    const val MARKDOWN_EXTENSION = ".md"
    const val TEMPORARY_EXTENSION = ".tmp"
    const val CONFLICT_MARKDOWN_SUFFIX = ".conflict.md"

    // True for the note files we own, not the rules file, conflict copies or temp files.
    fun isImportableMarkdownFile(fileName: String): Boolean {
        if (fileName == VAULT_RULES_FILE_NAME) return false
        if (fileName.endsWith(CONFLICT_MARKDOWN_SUFFIX)) return false
        if (fileName.endsWith(TEMPORARY_EXTENSION)) return false
        return fileName.endsWith(MARKDOWN_EXTENSION)
    }

    fun sanitiseFileName(rawName: String): String {
        val withoutIllegalCharacters = buildString(rawName.length) {
            for (character in rawName) {
                if (character in illegalFileNameCharacters || character.code < 32) append(' ')
                else append(character)
            }
        }

        val collapsedWhitespace = withoutIllegalCharacters
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val trimmed = collapsedWhitespace
            .take(MAX_FILE_NAME_LENGTH)
            .trim()
            .trimEnd('.')
            .trim()

        return trimmed.ifEmpty { "Untitled" }
    }

    fun looksLikeSpaceFolderName(directoryName: String): Boolean =
        directoryName.endsWith(SPACE_FOLDER_SUFFIX) && directoryName.length > SPACE_FOLDER_SUFFIX.length

    fun displayNameFromSpaceFolderName(directoryName: String): String =
        sanitiseFileName(directoryName.removeSuffix(SPACE_FOLDER_SUFFIX))

    fun spaceFolderNamesBySpaceId(spaces: List<SpaceEntity>): Map<String, String> {
        val baseNames = spaces.associate { it.spaceId to sanitiseFileName(it.displayName) }
        val namesInUse = baseNames.values.groupingBy { it.lowercase() }.eachCount()

        return baseNames.mapValues { (spaceId, baseName) ->
            if (namesInUse.getValue(baseName.lowercase()) > 1) {
                "$baseName (${VaultBlockTags.shortTagFor(spaceId)})$SPACE_FOLDER_SUFFIX"
            } else {
                "$baseName$SPACE_FOLDER_SUFFIX"
            }
        }
    }

    fun folderSegmentsFor(folderId: String?, foldersById: Map<String, FolderEntity>): List<String> {
        if (folderId == null) return emptyList()

        val segments = ArrayDeque<String>()
        val visitedFolderIds = mutableSetOf<String>()
        var currentFolderId: String? = folderId

        while (currentFolderId != null && visitedFolderIds.add(currentFolderId)) {
            val folder = foldersById[currentFolderId] ?: break
            segments.addFirst(sanitiseFileName(folder.name))
            currentFolderId = folder.parentFolderId
        }
        return segments.toList()
    }

    fun mediaPathPrefixForDepth(folderDepth: Int): String =
        "../".repeat(folderDepth.coerceAtLeast(0) + 1) + "media/"
}

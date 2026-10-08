package com.emberr.domain.selfhost.webdav

import io.ktor.http.decodeURLPart

object WebDavSyncPaths {
    const val ROOT = "/emberr_sync"
    const val VAULT_FILE = "$ROOT/vault.txt"
    const val MANIFEST_FILE = "$ROOT/manifest.json"
    const val NOTES_DIR = "$ROOT/notes"
    const val DAILY_DIR = "$ROOT/daily"
    const val MEDIA_DIR = "$ROOT/media"
    const val MEDIA_TRASH_DIR = "$ROOT/media_trash"
    const val CHAT_SESSIONS_DIR = "$ROOT/chat_sessions"
    const val SPACES_FILE = "$ROOT/spaces.json"
    const val FOLDERS_FILE = "$ROOT/folders.json"
    const val CATEGORIES_FILE = "$ROOT/categories.json"
    const val PROPERTY_TAGS_FILE = "$ROOT/property_tags.json"
    const val CUSTOM_PROPERTIES_FILE = "$ROOT/custom_properties.json"
    const val EVENT_EXCEPTIONS_FILE = "$ROOT/event_exceptions.json"
    const val API_CONFIGS_FILE = "$ROOT/api_configs.json"
    const val BOOKMARK_CATEGORY_ORDER_FILE = "$ROOT/bookmark_category_order.json"
    const val FAVORITE_NOTE_ORDER_FILE = "$ROOT/favorite_note_order.json"

    fun notePath(noteId: String) = "$NOTES_DIR/note_$noteId.enc"
    fun dailyPath(spaceId: String, dateString: String) = "$DAILY_DIR/daily_${spaceId}_$dateString.enc"
    fun mediaFolderPath(mediaId: String) = "$MEDIA_DIR/$MEDIA_FOLDER_PREFIX$mediaId/"
    fun trashedMediaFolderPath(mediaId: String) = "$MEDIA_TRASH_DIR/$MEDIA_FOLDER_PREFIX$mediaId/"
    fun mediaPiecePath(mediaId: String, pieceIndex: Int) =
        "${mediaFolderPath(mediaId)}$MEDIA_PIECE_PREFIX${pieceIndex.toString().padStart(5, '0')}$MEDIA_PIECE_SUFFIX"
    fun chatSessionPath(sessionId: String) = "$CHAT_SESSIONS_DIR/chat_$sessionId.enc"
    fun encryptionLabel(remotePath: String) = "emberr-file:$remotePath".encodeToByteArray()

    fun lastPathSegment(href: String): String = href.trimEnd('/').substringAfterLast('/').decodeURLPart()

    fun mediaIdFromFolderName(folderName: String): String? =
        folderName.removePrefix(MEDIA_FOLDER_PREFIX).takeIf { folderName.startsWith(MEDIA_FOLDER_PREFIX) && it.isNotEmpty() }

    fun pieceIndexFromFileName(fileName: String): Int? =
        fileName.takeIf { it.startsWith(MEDIA_PIECE_PREFIX) && it.endsWith(MEDIA_PIECE_SUFFIX) }
            ?.removePrefix(MEDIA_PIECE_PREFIX)
            ?.removeSuffix(MEDIA_PIECE_SUFFIX)
            ?.toIntOrNull()

    private const val MEDIA_FOLDER_PREFIX = "img_"
    private const val MEDIA_PIECE_PREFIX = "piece_"
    private const val MEDIA_PIECE_SUFFIX = ".enc"
}
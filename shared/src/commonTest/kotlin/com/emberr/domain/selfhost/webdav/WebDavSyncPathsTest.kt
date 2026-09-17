package com.emberr.domain.selfhost.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebDavSyncPathsTest {

    @Test
    fun everyRemotePathSitsUnderTheSingleSyncRoot() {
        val allPaths = listOf(
            WebDavSyncPaths.SALT_FILE,
            WebDavSyncPaths.MANIFEST_FILE,
            WebDavSyncPaths.NOTES_DIR,
            WebDavSyncPaths.DAILY_DIR,
            WebDavSyncPaths.MEDIA_DIR,
            WebDavSyncPaths.CHAT_SESSIONS_DIR,
            WebDavSyncPaths.SPACES_FILE,
            WebDavSyncPaths.FOLDERS_FILE,
            WebDavSyncPaths.TAGS_FILE,
            WebDavSyncPaths.CATEGORIES_FILE,
            WebDavSyncPaths.API_CONFIGS_FILE,
            WebDavSyncPaths.notePath("note-1"),
            WebDavSyncPaths.dailyPath("space-1", "2026-01-01"),
            WebDavSyncPaths.mediaPath("media-1"),
            WebDavSyncPaths.chatSessionPath("chat-1")
        )

        allPaths.forEach { path ->
            assertTrue(path.startsWith("${WebDavSyncPaths.ROOT}/"), "$path escapes the sync root")
        }
    }

    @Test
    fun theRemoteLayoutIsExactlyWhatEarlierReleasesWrote() {
        assertEquals("/emberr_sync", WebDavSyncPaths.ROOT)
        assertEquals("/emberr_sync/salt.txt", WebDavSyncPaths.SALT_FILE)
        assertEquals("/emberr_sync/manifest.json", WebDavSyncPaths.MANIFEST_FILE)
        assertEquals("/emberr_sync/spaces.json", WebDavSyncPaths.SPACES_FILE)
        assertEquals("/emberr_sync/folders.json", WebDavSyncPaths.FOLDERS_FILE)
        assertEquals("/emberr_sync/tags.json", WebDavSyncPaths.TAGS_FILE)
        assertEquals("/emberr_sync/categories.json", WebDavSyncPaths.CATEGORIES_FILE)
        assertEquals("/emberr_sync/api_configs.json", WebDavSyncPaths.API_CONFIGS_FILE)
    }

    @Test
    fun eachKindOfRecordGetsItsOwnPrefixedEncryptedFile() {
        assertEquals("/emberr_sync/notes/note_note-1.enc", WebDavSyncPaths.notePath("note-1"))
        assertEquals(
            "/emberr_sync/daily/daily_space-1_2026-01-01.enc",
            WebDavSyncPaths.dailyPath("space-1", "2026-01-01")
        )
        assertEquals("/emberr_sync/media/img_media-1.enc", WebDavSyncPaths.mediaPath("media-1"))
        assertEquals("/emberr_sync/chat_sessions/chat_chat-1.enc", WebDavSyncPaths.chatSessionPath("chat-1"))
    }

    @Test
    fun theSameDateInTwoSpacesNeverSharesAPath() {
        assertEquals(
            2,
            setOf(
                WebDavSyncPaths.dailyPath("space-1", "2026-01-01"),
                WebDavSyncPaths.dailyPath("space-2", "2026-01-01")
            ).size
        )
    }

    @Test
    fun twoDifferentRecordsNeverShareAPath() {
        val paths = setOf(
            WebDavSyncPaths.notePath("same-id"),
            WebDavSyncPaths.dailyPath("space-1", "same-id"),
            WebDavSyncPaths.mediaPath("same-id"),
            WebDavSyncPaths.chatSessionPath("same-id")
        )

        assertEquals(4, paths.size)
    }
}

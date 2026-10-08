package com.emberr.domain.util.media

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaFileNameOnlyTest {

    @Test
    fun aPlainFileNameStaysTheSame() {
        assertEquals("media_1.png", mediaFileNameOnly("media_1.png"))
    }

    @Test
    fun forwardSlashFoldersAreRemoved() {
        assertEquals("media_1.png", mediaFileNameOnly("../../media/media_1.png"))
    }

    @Test
    fun windowsBackslashFoldersAreRemoved() {
        assertEquals("evil.bat", mediaFileNameOnly("..\\..\\AppData\\Startup\\evil.bat"))
    }

    @Test
    fun mixedSlashesAreAllRemoved() {
        assertEquals("c.png", mediaFileNameOnly("a/b\\c.png"))
        assertEquals("c.png", mediaFileNameOnly("a\\b/c.png"))
    }
}

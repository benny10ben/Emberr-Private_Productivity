package com.emberr.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AppUpdateManifestTest {

    @Test
    fun readsTheManifestTheReleaseWorkflowWrites() {
        val manifest = parseAppUpdateManifest(
            """
            {
              "version": "1.1.0",
              "appImage": {
                "url": "https://github.com/example/emberr/releases/download/v1.1.0/Emberr-x86_64.AppImage",
                "sha256": "abc123",
                "sizeBytes": 183500800
              }
            }
            """.trimIndent().encodeToByteArray()
        )

        assertEquals("1.1.0", manifest.version)
        assertEquals("abc123", manifest.appImage.sha256)
        assertEquals(183_500_800L, manifest.appImage.sizeBytes)
    }

    @Test
    fun ignoresFieldsAddedByLaterReleases() {
        val manifest = parseAppUpdateManifest(
            """
            {
              "version": "1.2.0",
              "appImage": { "url": "https://example.com/a", "sha256": "abc", "sizeBytes": 1 },
              "windowsInstaller": { "url": "https://example.com/b", "sha256": "def", "sizeBytes": 2 }
            }
            """.trimIndent().encodeToByteArray()
        )

        assertEquals("1.2.0", manifest.version)
    }

    @Test
    fun manifestWithoutAnAppImageFailsToParse() {
        assertFails {
            parseAppUpdateManifest("""{ "version": "1.2.0" }""".encodeToByteArray())
        }
    }
}

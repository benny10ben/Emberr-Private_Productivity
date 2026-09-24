package com.emberr.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

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
        assertEquals("abc123", manifest.appImage?.sha256)
        assertEquals(183_500_800L, manifest.appImage?.sizeBytes)
    }

    @Test
    fun ignoresFieldsAddedByLaterReleases() {
        val manifest = parseAppUpdateManifest(
            """
            {
              "version": "1.2.0",
              "appImage": { "url": "https://example.com/a", "sha256": "abc", "sizeBytes": 1 },
              "somePackageFromTheFuture": { "url": "https://example.com/b", "sha256": "def", "sizeBytes": 2 }
            }
            """.trimIndent().encodeToByteArray()
        )

        assertEquals("1.2.0", manifest.version)
    }

    @Test
    fun readsEveryPackageTypeEntry() {
        val manifest = parseAppUpdateManifest(
            """
            {
              "version": "1.0.2",
              "appImage": { "url": "https://example.com/a", "sha256": "abc", "sizeBytes": 1 },
              "tarball": { "url": "https://example.com/emberr-x86_64.tar.gz", "sha256": "def", "sizeBytes": 2 },
              "windowsInstaller": { "url": "https://example.com/Emberr-Setup-x86_64.exe", "sha256": "ghi", "sizeBytes": 3 },
              "androidApk": { "url": "https://example.com/Emberr-android.apk", "sha256": "jkl", "sizeBytes": 4 }
            }
            """.trimIndent().encodeToByteArray()
        )

        assertEquals("https://example.com/emberr-x86_64.tar.gz", manifest.tarball?.url)
        assertEquals("def", manifest.tarball?.sha256)
        assertEquals("https://example.com/Emberr-Setup-x86_64.exe", manifest.windowsInstaller?.url)
        assertEquals("https://example.com/Emberr-android.apk", manifest.androidApk?.url)
    }

    @Test
    fun manifestFromBeforeTarballUpdatesHasNoTarballEntry() {
        val manifest = parseAppUpdateManifest(
            """
            {
              "version": "1.0.1",
              "appImage": { "url": "https://example.com/a", "sha256": "abc", "sizeBytes": 1 }
            }
            """.trimIndent().encodeToByteArray()
        )

        assertNull(manifest.tarball)
        assertNull(manifest.windowsInstaller)
        assertNull(manifest.androidApk)
    }

    @Test
    fun manifestWithoutAVersionFailsToParse() {
        assertFails {
            parseAppUpdateManifest("""{ "appImage": { "url": "a", "sha256": "b", "sizeBytes": 1 } }""".encodeToByteArray())
        }
    }
}

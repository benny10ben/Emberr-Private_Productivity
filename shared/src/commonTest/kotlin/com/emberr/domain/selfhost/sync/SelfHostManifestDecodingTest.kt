package com.emberr.domain.selfhost.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SelfHostManifestDecodingTest {

    @Test
    fun aListFromANewerVersionWithAnUnknownKindOfItemAsksForAnUpdateInsteadOfFailingToRead() {
        val newerList = """
            {"schemaVersion":2,"entries":[{"entryId":"trip-plan","entryType":"WHITEBOARD","updatedAt":1000}]}
        """.trimIndent()

        val error = assertFailsWith<SelfHostManifestTooNewException> { decodeSelfHostManifest(newerList) }

        assertEquals(2, error.serverSchemaVersion)
        assertEquals(SELF_HOST_UPDATE_REQUIRED_MESSAGE, error.message)
    }

    @Test
    fun aListFromThisVersionIsReadAsBefore() {
        val currentList = """
            {"schemaVersion":1,"entries":[{"entryId":"groceries","entryType":"NOTE","updatedAt":1000,"mediaFileNames":["milk.jpg"]}]}
        """.trimIndent()

        val manifest = decodeSelfHostManifest(currentList)

        assertEquals(
            listOf(
                SelfHostManifestEntry(
                    entryId = "groceries",
                    entryType = SelfHostEntryType.NOTE,
                    updatedAt = 1000,
                    mediaFileNames = setOf("milk.jpg")
                )
            ),
            manifest.entries
        )
    }

    @Test
    fun aListWrittenBeforeTheVersionWasRecordedIsReadAsTheFirstVersion() {
        val listWithoutVersion = """{"entries":[{"entryId":"groceries","entryType":"NOTE","updatedAt":1000}]}"""

        val manifest = decodeSelfHostManifest(listWithoutVersion)

        assertEquals(1, manifest.schemaVersion)
        assertEquals(listOf("groceries"), manifest.entries.map { it.entryId })
    }

    @Test
    fun aNewListIsWrittenWithTheCurrentVersion() {
        assertEquals(SELF_HOST_MANIFEST_SCHEMA_VERSION, SelfHostManifest().schemaVersion)
    }
}

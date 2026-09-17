// Tests for the vault tool executor's read-only tools and their transparency events.

package com.emberr.domain.ai.tools

import com.emberr.domain.vault.ActiveVaultSpace
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val TEST_SPACE_ID = "test-space"

class VaultToolExecutorTest {

    private lateinit var vaultRootDirectory: File
    private lateinit var executor: VaultToolExecutor

    @BeforeTest
    fun setUp() {
        vaultRootDirectory = Files.createTempDirectory("vault-executor-test").toFile()
        executor = VaultToolExecutor(
            activeSpace = { ActiveVaultSpace(TEST_SPACE_ID, vaultRootDirectory) },
            vaultImporter = FakeVaultNoteImporter(),
            pendingWriteEvents = VaultPendingWriteEvents(),
            toolCallEvents = VaultToolCallEvents()
        )
    }

    @AfterTest
    fun tearDown() {
        vaultRootDirectory.deleteRecursively()
    }

    @Test
    fun listNotesOnAnEmptyVaultReturnsNoNotes() {
        val result = assertIs<VaultToolResult.Notes>(executor.listNotes(vaultRootDirectory))
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun listNotesOnlyReturnsRealNoteFiles() {
        File(vaultRootDirectory, "Groceries.md").writeText("- milk")
        File(vaultRootDirectory, "Recipe.md").writeText("- flour")
        File(vaultRootDirectory, "CLAUDE.md").writeText("vault rules")
        File(vaultRootDirectory, "Groceries.md.tmp").writeText("partial write")

        val result = assertIs<VaultToolResult.Notes>(executor.listNotes(vaultRootDirectory))
        assertEquals(listOf("Groceries.md", "Recipe.md"), result.notes.map { it.relativePath })
    }

    @Test
    fun listNotesOnANestedFolderReturnsOnlyThatFolder() {
        val dailyFolder = File(vaultRootDirectory, "Daily").apply { mkdirs() }
        File(dailyFolder, "2026-09-12.md").writeText("today")
        File(vaultRootDirectory, "Groceries.md").writeText("- milk")

        val result = assertIs<VaultToolResult.Notes>(executor.listNotes(vaultRootDirectory, "Daily"))
        assertEquals(listOf("Daily/2026-09-12.md"), result.notes.map { it.relativePath })
    }

    @Test
    fun listNotesOnAMissingFolderIsAFailure() {
        assertIs<VaultToolResult.Failure>(executor.listNotes(vaultRootDirectory, "Nonexistent"))
    }

    @Test
    fun readNoteReturnsTheFileContent() {
        File(vaultRootDirectory, "Groceries.md").writeText("- milk\n- eggs")

        val result = assertIs<VaultToolResult.NoteContent>(executor.readNote(vaultRootDirectory, "Groceries.md"))
        assertEquals("- milk\n- eggs", result.markdown)
    }

    @Test
    fun readNoteOnAMissingFileIsAFailure() {
        assertIs<VaultToolResult.Failure>(executor.readNote(vaultRootDirectory, "Nonexistent.md"))
    }

    @Test
    fun readNoteCannotEscapeTheVaultThroughATraversalPath() {
        val secretFile = File(vaultRootDirectory.parentFile, "secret.md")
        secretFile.writeText("outside the vault")

        try {
            assertIs<VaultToolResult.Failure>(executor.readNote(vaultRootDirectory, "../secret.md"))
        } finally {
            secretFile.delete()
        }
    }

    @Test
    fun searchNotesFindsACaseInsensitiveMatch() {
        val dailyFolder = File(vaultRootDirectory, "Daily").apply { mkdirs() }
        File(dailyFolder, "2026-09-12.md").writeText("Remember to water the PLANTS")
        File(vaultRootDirectory, "Groceries.md").writeText("- milk")

        val result = assertIs<VaultToolResult.Notes>(executor.searchNotes(vaultRootDirectory, "plants"))
        assertEquals(listOf("Daily/2026-09-12.md"), result.notes.map { it.relativePath })
    }

    @Test
    fun searchNotesWithNoMatchesReturnsAnEmptyList() {
        File(vaultRootDirectory, "Groceries.md").writeText("- milk")

        val result = assertIs<VaultToolResult.Notes>(executor.searchNotes(vaultRootDirectory, "plants"))
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun searchNotesWithABlankQueryIsAFailure() {
        assertIs<VaultToolResult.Failure>(executor.searchNotes(vaultRootDirectory, "  "))
    }

    @Test
    fun readingANoteThroughRunEmitsATransparencyEvent() = runTest {
        File(vaultRootDirectory, "Groceries.md").writeText("- milk")
        val toolCallEvents = VaultToolCallEvents()
        val executorWithEvents = VaultToolExecutor(
            activeSpace = { ActiveVaultSpace(TEST_SPACE_ID, vaultRootDirectory) },
            vaultImporter = FakeVaultNoteImporter(),
            pendingWriteEvents = VaultPendingWriteEvents(),
            toolCallEvents = toolCallEvents
        )

        val received = async { toolCallEvents.calls.first() }
        runCurrent()
        executorWithEvents.run(VaultTools.readNote.name, mapOf("path" to "Groceries.md"))

        val call = received.await()
        assertEquals(VaultTools.readNote.name, call.toolName)
        assertEquals("Read \"Groceries.md\"", call.description)
    }

    @Test
    fun proposingAWriteThroughRunDoesNotEmitAToolCallEvent() = runTest {
        val toolCallEvents = VaultToolCallEvents()
        val pendingWriteEvents = VaultPendingWriteEvents()
        val executorWithEvents = VaultToolExecutor(
            activeSpace = { ActiveVaultSpace(TEST_SPACE_ID, vaultRootDirectory) },
            vaultImporter = FakeVaultNoteImporter(),
            pendingWriteEvents = pendingWriteEvents,
            toolCallEvents = toolCallEvents
        )

        val observedToolCalls = mutableListOf<VaultToolCallSummary>()
        val toolCallCollectorJob = launch { toolCallEvents.calls.collect { observedToolCalls.add(it) } }
        val receivedPendingWrite = async { pendingWriteEvents.proposed.first() }
        runCurrent()

        executorWithEvents.run(VaultTools.createNote.name, mapOf("path" to "Idea.md", "content" to "hello"))

        assertEquals("Idea.md", receivedPendingWrite.await().relativePath)
        toolCallCollectorJob.cancel()
        assertTrue(observedToolCalls.isEmpty())
    }
}

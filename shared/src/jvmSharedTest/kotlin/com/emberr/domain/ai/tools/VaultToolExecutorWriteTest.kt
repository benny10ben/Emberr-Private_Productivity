// Tests for the vault tool executor's write-proposal and apply-on-confirm behavior.

package com.emberr.domain.ai.tools

import com.emberr.domain.vault.ActiveVaultSpace
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEST_SPACE_ID = "test-space"

class VaultToolExecutorWriteTest {

    private lateinit var vaultRootDirectory: File
    private lateinit var importer: FakeVaultNoteImporter
    private lateinit var pendingWriteEvents: VaultPendingWriteEvents
    private lateinit var executor: VaultToolExecutor

    @BeforeTest
    fun setUp() {
        vaultRootDirectory = Files.createTempDirectory("vault-executor-write-test").toFile()
        importer = FakeVaultNoteImporter()
        pendingWriteEvents = VaultPendingWriteEvents()
        executor = VaultToolExecutor(
            activeSpace = { ActiveVaultSpace(TEST_SPACE_ID, vaultRootDirectory) },
            vaultImporter = importer,
            pendingWriteEvents = pendingWriteEvents,
            toolCallEvents = VaultToolCallEvents()
        )
    }

    @AfterTest
    fun tearDown() {
        vaultRootDirectory.deleteRecursively()
    }

    @Test
    fun createNoteIsProposedNotWrittenImmediately() = runTest {
        val result = executor.run(VaultTools.createNote.name, mapOf("path" to "Idea.md", "content" to "hello"))

        val proposed = assertIs<VaultToolResult.Proposed>(result)
        assertEquals(VaultPendingWriteKind.CREATE, proposed.pendingWrite.kind)
        assertTrue(!File(vaultRootDirectory, "Idea.md").exists())
    }

    @Test
    fun createNoteFailsWhenFileAlreadyExists() = runTest {
        File(vaultRootDirectory, "Idea.md").writeText("already here")

        val result = executor.run(VaultTools.createNote.name, mapOf("path" to "Idea.md", "content" to "hello"))

        assertIs<VaultToolResult.Failure>(result)
    }

    @Test
    fun updateNoteFailsWhenNoteDoesNotExist() = runTest {
        val result = executor.run(VaultTools.updateNote.name, mapOf("path" to "Nonexistent.md", "content" to "hello"))

        assertIs<VaultToolResult.Failure>(result)
    }

    @Test
    fun writeToolsCannotEscapeTheVaultThroughATraversalPath() = runTest {
        val result = executor.run(VaultTools.deleteNote.name, mapOf("path" to "../outside.md"))

        assertIs<VaultToolResult.Failure>(result)
    }

    @Test
    fun applyCreateWritesTheFileAndImportsIt() = runTest {
        val write = VaultPendingWrite(
            spaceId = TEST_SPACE_ID,
            kind = VaultPendingWriteKind.CREATE,
            relativePath = "Idea.md",
            proposedContent = "A new idea"
        )

        val result = executor.applyPendingWrite(write)

        assertIs<VaultToolResult.NoteContent>(result)
        assertEquals("A new idea", File(vaultRootDirectory, "Idea.md").readText())
        assertEquals(File(vaultRootDirectory, "Idea.md").canonicalFile, importer.lastImportedFile)
    }

    @Test
    fun applyUpdatePreservesTheOriginalFrontMatter() = runTest {
        val noteFile = File(vaultRootDirectory, "Groceries.md")
        noteFile.writeText("---\nid: note-1\ntitle: Groceries\n---\n- milk")

        val write = VaultPendingWrite(
            spaceId = TEST_SPACE_ID,
            kind = VaultPendingWriteKind.UPDATE,
            relativePath = "Groceries.md",
            proposedContent = "- milk\n- eggs"
        )

        executor.applyPendingWrite(write)

        val updated = noteFile.readText()
        assertTrue(updated.startsWith("---\nid: note-1\ntitle: Groceries\n---\n"))
        assertTrue(updated.endsWith("- milk\n- eggs"))
    }

    @Test
    fun applyUpdateDoesNotDuplicateFrontMatterEchoedBackByTheModel() = runTest {
        val noteFile = File(vaultRootDirectory, "Groceries.md")
        noteFile.writeText("---\nid: note-1\ntitle: Groceries\n---\n- milk")

        val write = VaultPendingWrite(
            spaceId = TEST_SPACE_ID,
            kind = VaultPendingWriteKind.UPDATE,
            relativePath = "Groceries.md",
            proposedContent = "---\nid: note-1\ntitle: Groceries\n---\n- milk\n- eggs"
        )

        executor.applyPendingWrite(write)

        val updated = noteFile.readText()
        val separatorLineCount = Regex("^---$", RegexOption.MULTILINE).findAll(updated).count()
        assertEquals(2, separatorLineCount)
        assertTrue(updated.endsWith("- milk\n- eggs"))
    }

    @Test
    fun applyAppendAddsToTheEndAndKeepsFrontMatter() = runTest {
        val noteFile = File(vaultRootDirectory, "Groceries.md")
        noteFile.writeText("---\nid: note-1\ntitle: Groceries\n---\n- milk")

        val write = VaultPendingWrite(
            spaceId = TEST_SPACE_ID,
            kind = VaultPendingWriteKind.APPEND,
            relativePath = "Groceries.md",
            proposedContent = "- eggs"
        )

        executor.applyPendingWrite(write)

        val updated = noteFile.readText()
        assertTrue(updated.startsWith("---\nid: note-1\ntitle: Groceries\n---\n"))
        assertTrue(updated.trimEnd().endsWith("- milk\n\n- eggs"))
    }

    @Test
    fun applyDeleteRemovesTheFileAndImportsTheRemoval() = runTest {
        val noteFile = File(vaultRootDirectory, "Groceries.md")
        noteFile.writeText("---\nid: note-1\ntitle: Groceries\n---\n- milk")

        val write = VaultPendingWrite(spaceId = TEST_SPACE_ID, kind = VaultPendingWriteKind.DELETE, relativePath = "Groceries.md")
        val result = executor.applyPendingWrite(write)

        assertIs<VaultToolResult.NoteContent>(result)
        assertTrue(!noteFile.exists())
        assertEquals(noteFile.canonicalFile, importer.lastImportedFile)
    }

    @Test
    fun applyDeleteFailsWhenNoteIsAlreadyGone() = runTest {
        val write = VaultPendingWrite(spaceId = TEST_SPACE_ID, kind = VaultPendingWriteKind.DELETE, relativePath = "Nonexistent.md")

        val result = executor.applyPendingWrite(write)

        assertIs<VaultToolResult.Failure>(result)
        assertNull(importer.lastImportedFile)
    }

    @Test
    fun applyIsRefusedWhenTheWriteWasProposedInAnotherSpace() = runTest {
        val noteFile = File(vaultRootDirectory, "Groceries.md")
        noteFile.writeText("---\nid: note-1\ntitle: Groceries\n---\n- milk")

        val write = VaultPendingWrite(
            spaceId = "a-different-space",
            kind = VaultPendingWriteKind.UPDATE,
            relativePath = "Groceries.md",
            proposedContent = "- rewritten by the wrong space"
        )

        val result = executor.applyPendingWrite(write)

        assertIs<VaultToolResult.Failure>(result)
        assertEquals("---\nid: note-1\ntitle: Groceries\n---\n- milk", noteFile.readText())
        assertNull(importer.lastImportedFile)
    }

    @Test
    fun applyIsRefusedWhenTheWriteRecordsNoSpace() = runTest {
        val noteFile = File(vaultRootDirectory, "Groceries.md")
        noteFile.writeText("---\nid: note-1\ntitle: Groceries\n---\n- milk")

        val write = VaultPendingWrite(
            kind = VaultPendingWriteKind.UPDATE,
            relativePath = "Groceries.md",
            proposedContent = "- rewritten from an older session"
        )

        val result = executor.applyPendingWrite(write)

        assertIs<VaultToolResult.Failure>(result)
        assertEquals("---\nid: note-1\ntitle: Groceries\n---\n- milk", noteFile.readText())
        assertNull(importer.lastImportedFile)
    }
}

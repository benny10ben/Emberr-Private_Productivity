// Runs vault tool calls for real - reads hit the filesystem directly, writes are staged and only applied on confirmation.

package com.emberr.domain.ai.tools

import com.emberr.domain.vault.VaultImportOutcome
import com.emberr.domain.vault.VaultLog
import com.emberr.domain.vault.VaultMarkdownScanner
import com.emberr.domain.vault.VaultNoteImporter
import com.emberr.domain.vault.ActiveVaultSpace
import com.emberr.domain.vault.VaultPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class VaultToolExecutor(
    private val activeSpace: suspend () -> ActiveVaultSpace?,
    private val vaultImporter: VaultNoteImporter,
    private val pendingWriteEvents: VaultPendingWriteEvents,
    private val toolCallEvents: VaultToolCallEvents
) : VaultToolRunner {

    override suspend fun run(toolName: String, arguments: Map<String, String>): VaultToolResult =
        withContext(Dispatchers.IO) {
            VaultLog.d("vault tool call: ${describeCallForLog(toolName, arguments)}")

            val currentSpace = activeSpace()

            val result = if (currentSpace == null) {
                VaultToolResult.Failure("The active space has no vault folder, so vault tools are unavailable")
            } else when (toolName) {
                VaultTools.listNotes.name -> listNotes(currentSpace.directory, arguments["folder_path"].orEmpty())
                VaultTools.readNote.name -> readNote(currentSpace.directory, arguments["path"].orEmpty())
                VaultTools.searchNotes.name -> searchNotes(currentSpace.directory, arguments["query"].orEmpty())
                VaultTools.createNote.name ->
                    proposeCreate(currentSpace, arguments["path"].orEmpty(), arguments["content"].orEmpty())
                VaultTools.updateNote.name ->
                    proposeUpdate(currentSpace, arguments["path"].orEmpty(), arguments["content"].orEmpty())
                VaultTools.appendToNote.name ->
                    proposeAppend(currentSpace, arguments["path"].orEmpty(), arguments["content"].orEmpty())
                VaultTools.deleteNote.name -> proposeDelete(currentSpace, arguments["path"].orEmpty())
                else -> VaultToolResult.Failure("Unknown or unavailable tool: $toolName")
            }

            if (result is VaultToolResult.Failure) {
                VaultLog.e("vault tool call failed: ${describeCallForLog(toolName, arguments)} - ${result.reason}")
            }

            when {
                result is VaultToolResult.Proposed -> pendingWriteEvents.notifyProposed(result.pendingWrite)
                isReadOnlyTool(toolName) ->
                    toolCallEvents.notifyCalled(VaultToolCallSummary(toolName, describeReadCall(toolName, arguments)))
            }

            result
        }

    private fun isReadOnlyTool(toolName: String): Boolean =
        toolName == VaultTools.listNotes.name ||
            toolName == VaultTools.readNote.name ||
            toolName == VaultTools.searchNotes.name

    private fun describeReadCall(toolName: String, arguments: Map<String, String>): String = when (toolName) {
        VaultTools.listNotes.name -> {
            val folderPath = arguments["folder_path"].orEmpty()
            if (folderPath.isBlank()) "Looked at notes in the vault" else "Looked at notes in \"$folderPath\""
        }

        VaultTools.readNote.name -> "Read \"${arguments["path"].orEmpty()}\""
        VaultTools.searchNotes.name -> "Searched notes for \"${arguments["query"].orEmpty()}\""
        else -> toolName
    }

    private fun describeCallForLog(toolName: String, arguments: Map<String, String>): String = when (toolName) {
        VaultTools.listNotes.name, VaultTools.readNote.name, VaultTools.searchNotes.name ->
            describeReadCall(toolName, arguments)

        else -> "$toolName(path=\"${arguments["path"].orEmpty()}\")"
    }

    override suspend fun applyPendingWrite(write: VaultPendingWrite): VaultToolResult =
        withContext(Dispatchers.IO) {
            VaultLog.d("applying ${write.kind} to \"${write.relativePath}\"")

            val result = applyPendingWriteInternal(write)

            if (result is VaultToolResult.Failure) {
                VaultLog.e("failed to apply ${write.kind} to \"${write.relativePath}\": ${result.reason}")
            }

            result
        }

    private suspend fun applyPendingWriteInternal(write: VaultPendingWrite): VaultToolResult {
        val currentSpace = activeSpace()
            ?: return VaultToolResult.Failure("The active space has no vault folder, so vault tools are unavailable")

        val proposedInTheActiveSpace = write.spaceId.isNotBlank() && write.spaceId == currentSpace.spaceId
        if (!proposedInTheActiveSpace) {
            return VaultToolResult.Failure(
                "This change was proposed in a different space, so it was not applied. " +
                    "Ask again in the space you want to change."
            )
        }

        val sandbox = VaultPathSandbox(currentSpace.directory)
        val file = when (val resolution = sandbox.resolve(write.relativePath)) {
            is VaultPathResolution.Rejected -> return VaultToolResult.Failure(resolution.reason)
            is VaultPathResolution.Allowed -> resolution.file
        }

        val writeFailure = try {
            when (write.kind) {
                VaultPendingWriteKind.CREATE -> applyCreate(file, write)
                VaultPendingWriteKind.UPDATE -> applyUpdate(file, write)
                VaultPendingWriteKind.APPEND -> applyAppend(file, write)
                VaultPendingWriteKind.DELETE -> applyDelete(file, write)
            }
        } catch (cause: IOException) {
            VaultToolResult.Failure(cause.message ?: "Could not write \"${write.relativePath}\"")
        }
        if (writeFailure != null) return writeFailure

        val importReport = vaultImporter.importFile(file)
        return if (importReport.outcome == VaultImportOutcome.FAILED) {
            VaultToolResult.Failure(importReport.detail ?: "Could not save the change")
        } else {
            VaultToolResult.NoteContent(
                relativePath = write.relativePath,
                markdown = file.takeIf { it.isFile }?.readText().orEmpty()
            )
        }
    }

    private fun applyCreate(file: File, write: VaultPendingWrite): VaultToolResult.Failure? {
        if (file.exists()) return VaultToolResult.Failure("A note already exists at \"${write.relativePath}\"")
        file.parentFile?.let { parent -> if (!parent.isDirectory) parent.mkdirs() }
        file.writeText(write.proposedContent.orEmpty())
        return null
    }

    private fun applyUpdate(file: File, write: VaultPendingWrite): VaultToolResult.Failure? {
        if (!file.isFile) return VaultToolResult.Failure("No note at \"${write.relativePath}\" in the vault")
        val proposedBody = bodyOnly(write.proposedContent.orEmpty())
        file.writeText(rebuildWithFrontMatter(file.readText(), proposedBody))
        return null
    }

    private fun applyAppend(file: File, write: VaultPendingWrite): VaultToolResult.Failure? {
        if (!file.isFile) return VaultToolResult.Failure("No note at \"${write.relativePath}\" in the vault")
        val currentText = file.readText()
        val currentBody = bodyOnly(currentText)
        val appendedBody = bodyOnly(write.proposedContent.orEmpty())
        val newBody = currentBody.trimEnd() + "\n\n" + appendedBody.trim()
        file.writeText(rebuildWithFrontMatter(currentText, newBody))
        return null
    }

    private fun bodyOnly(markdown: String): String = VaultMarkdownScanner.splitFrontMatter(markdown).second

    private fun applyDelete(file: File, write: VaultPendingWrite): VaultToolResult.Failure? {
        if (!file.isFile) return VaultToolResult.Failure("No note at \"${write.relativePath}\" in the vault")
        if (!file.delete()) return VaultToolResult.Failure("Could not delete \"${write.relativePath}\"")
        return null
    }

    private fun rebuildWithFrontMatter(originalText: String, newBody: String): String {
        val lines = originalText.replace("\r\n", "\n").split('\n')
        if (lines.firstOrNull()?.trim() != "---") return newBody

        val closingIndex = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: return newBody
        val frontMatterBlock = lines.subList(0, closingIndex + 1).joinToString("\n")
        return "$frontMatterBlock\n$newBody"
    }

    private fun proposeCreate(space: ActiveVaultSpace, path: String, content: String): VaultToolResult {
        val file = when (val resolution = VaultPathSandbox(space.directory).resolve(path)) {
            is VaultPathResolution.Rejected -> return VaultToolResult.Failure(resolution.reason)
            is VaultPathResolution.Allowed -> resolution.file
        }
        if (file.exists()) return VaultToolResult.Failure("A note already exists at \"$path\"")

        return VaultToolResult.Proposed(
            VaultPendingWrite(spaceId = space.spaceId, kind = VaultPendingWriteKind.CREATE, relativePath = path, proposedContent = content)
        )
    }

    private fun proposeUpdate(space: ActiveVaultSpace, path: String, content: String): VaultToolResult {
        val file = when (val resolution = VaultPathSandbox(space.directory).resolve(path)) {
            is VaultPathResolution.Rejected -> return VaultToolResult.Failure(resolution.reason)
            is VaultPathResolution.Allowed -> resolution.file
        }
        if (!file.isFile || !VaultPaths.isImportableMarkdownFile(file.name)) {
            return VaultToolResult.Failure("No note at \"$path\" in the vault")
        }

        return VaultToolResult.Proposed(
            VaultPendingWrite(
                spaceId = space.spaceId,
                kind = VaultPendingWriteKind.UPDATE,
                relativePath = path,
                previousContent = file.readText(),
                proposedContent = content
            )
        )
    }

    private fun proposeAppend(space: ActiveVaultSpace, path: String, content: String): VaultToolResult {
        val file = when (val resolution = VaultPathSandbox(space.directory).resolve(path)) {
            is VaultPathResolution.Rejected -> return VaultToolResult.Failure(resolution.reason)
            is VaultPathResolution.Allowed -> resolution.file
        }
        if (!file.isFile || !VaultPaths.isImportableMarkdownFile(file.name)) {
            return VaultToolResult.Failure("No note at \"$path\" in the vault")
        }

        return VaultToolResult.Proposed(
            VaultPendingWrite(
                spaceId = space.spaceId,
                kind = VaultPendingWriteKind.APPEND,
                relativePath = path,
                previousContent = file.readText(),
                proposedContent = content
            )
        )
    }

    private fun proposeDelete(space: ActiveVaultSpace, path: String): VaultToolResult {
        val file = when (val resolution = VaultPathSandbox(space.directory).resolve(path)) {
            is VaultPathResolution.Rejected -> return VaultToolResult.Failure(resolution.reason)
            is VaultPathResolution.Allowed -> resolution.file
        }
        if (!file.isFile || !VaultPaths.isImportableMarkdownFile(file.name)) {
            return VaultToolResult.Failure("No note at \"$path\" in the vault")
        }

        return VaultToolResult.Proposed(
            VaultPendingWrite(spaceId = space.spaceId, kind = VaultPendingWriteKind.DELETE, relativePath = path, previousContent = file.readText())
        )
    }

    fun listNotes(spaceRoot: File, folderPath: String = ""): VaultToolResult {
        val directory = when (val resolution = VaultPathSandbox(spaceRoot).resolve(folderPath)) {
            is VaultPathResolution.Rejected -> return VaultToolResult.Failure(resolution.reason)
            is VaultPathResolution.Allowed -> resolution.file
        }

        if (!directory.isDirectory) {
            return VaultToolResult.Failure("No folder at \"$folderPath\" in the vault")
        }

        val allNotes = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && VaultPaths.isImportableMarkdownFile(it.name) }
            .map { it.toNoteSummary(spaceRoot) }
            .sortedBy { it.title.lowercase() }

        return VaultToolResult.Notes(
            notes = allNotes.take(MAX_LIST_RESULTS),
            truncated = allNotes.size > MAX_LIST_RESULTS
        )
    }

    fun readNote(spaceRoot: File, path: String): VaultToolResult {
        val file = when (val resolution = VaultPathSandbox(spaceRoot).resolve(path)) {
            is VaultPathResolution.Rejected -> return VaultToolResult.Failure(resolution.reason)
            is VaultPathResolution.Allowed -> resolution.file
        }

        if (!file.isFile || !VaultPaths.isImportableMarkdownFile(file.name)) {
            return VaultToolResult.Failure("No note at \"$path\" in the vault")
        }

        val markdown = file.readText()
        val limited = if (markdown.length > MAX_NOTE_CONTENT_CHARS) {
            markdown.take(MAX_NOTE_CONTENT_CHARS) +
                "\n\n[Note truncated - ${markdown.length} characters total]"
        } else {
            markdown
        }

        return VaultToolResult.NoteContent(relativePath = path, markdown = limited)
    }

    fun searchNotes(spaceRoot: File, query: String): VaultToolResult {
        if (query.isBlank()) {
            return VaultToolResult.Failure("Search query cannot be blank")
        }

        val allMatches = spaceRoot.walkTopDown()
            .filter { it.isFile && VaultPaths.isImportableMarkdownFile(it.name) }
            .filter { it.readText().contains(query, ignoreCase = true) }
            .map { it.toNoteSummary(spaceRoot) }
            .sortedBy { it.title.lowercase() }
            .toList()

        return VaultToolResult.Notes(
            notes = allMatches.take(MAX_LIST_RESULTS),
            truncated = allMatches.size > MAX_LIST_RESULTS
        )
    }

    private fun File.toNoteSummary(spaceRoot: File) = VaultNoteSummary(
        relativePath = relativeTo(spaceRoot).path,
        title = nameWithoutExtension
    )

    private companion object {
        const val MAX_NOTE_CONTENT_CHARS = 20_000
        const val MAX_LIST_RESULTS = 200
    }
}

package com.emberr.domain.util.export

import com.emberr.domain.backup.manual.DesktopManualBackupExporter
import com.emberr.domain.backup.manual.DesktopManualBackupImporter
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.util.system.restartApplication
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

fun handleExportMarkdown(window: Frame, fileName: String, content: String) {
    try {
        val dialog = FileDialog(window, "Export Markdown", FileDialog.SAVE)
        dialog.file = fileName
        dialog.isVisible = true

        val chosenFileStr = dialog.file ?: return
        val chosenDirStr = dialog.directory

        var saveFile = if (chosenDirStr != null) java.io.File(chosenDirStr, chosenFileStr)
        else java.io.File(chosenFileStr)

        if (saveFile.name.endsWith(".txt", ignoreCase = true)) {
            saveFile = java.io.File(saveFile.absolutePath.removeSuffix(".txt").removeSuffix(".TXT"))
        }
        if (!saveFile.name.endsWith(".md", ignoreCase = true)) {
            saveFile = java.io.File(saveFile.absolutePath + ".md")
        }

        saveFile.writeText(content)

        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Markdown saved successfully:\n${saveFile.name}", "Success", JOptionPane.INFORMATION_MESSAGE)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Error saving Markdown:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun handleExportPdf(window: Frame, fileName: String, title: String, blocks: List<NoteBlock>) {
    try {
        val dialog = FileDialog(window, "Export PDF", FileDialog.SAVE)
        dialog.file = fileName
        dialog.isVisible = true

        val chosenFileStr = dialog.file ?: return
        val chosenDirStr = dialog.directory

        var saveFile = if (chosenDirStr != null) java.io.File(chosenDirStr, chosenFileStr)
        else java.io.File(chosenFileStr)

        if (saveFile.name.endsWith(".txt", ignoreCase = true)) {
            saveFile = java.io.File(saveFile.absolutePath.removeSuffix(".txt").removeSuffix(".TXT"))
        }
        if (!saveFile.name.endsWith(".pdf", ignoreCase = true)) {
            saveFile = java.io.File(saveFile.absolutePath + ".pdf")
        }

        generateDesktopPdf(saveFile, title, blocks)

        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "PDF saved successfully:\n${saveFile.name}", "Success", JOptionPane.INFORMATION_MESSAGE)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Error saving PDF:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun handleExportBackup(window: Frame) {
    try {
        val fileName = "EmberrBackup_${System.currentTimeMillis()}.emberr"
        val dialog = FileDialog(window, "Export Emberr Backup", FileDialog.SAVE)
        dialog.file = fileName
        dialog.isVisible = true

        val chosenFileStr = dialog.file ?: return
        val chosenDirStr = dialog.directory
        val saveFile = if (chosenDirStr != null) java.io.File(chosenDirStr, chosenFileStr)
        else java.io.File(chosenFileStr)

        val exporter = GlobalContext.get().get<DesktopManualBackupExporter>()
        runBlocking { exporter.exportToZip(saveFile) }

        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Backup saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Export failed:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun handleImportBackup(window: Frame) {
    try {
        val dialog = FileDialog(window, "Import Emberr Backup", FileDialog.LOAD)
        dialog.file = "*.emberr"
        dialog.isVisible = true

        val chosenFileStr = dialog.file ?: return
        val chosenDirStr = dialog.directory
        val sourceFile = if (chosenDirStr != null) java.io.File(chosenDirStr, chosenFileStr)
        else java.io.File(chosenFileStr)

        val importer = GlobalContext.get().get<DesktopManualBackupImporter>()
        runBlocking { importer.importFromZip(sourceFile) }

        SwingUtilities.invokeLater {
            ActiveEditorRegistry.discardAllPendingWrites()
            JOptionPane.showMessageDialog(
                window,
                "Backup restored successfully!\n\nEmberr will now restart.",
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            )
            restartApplication()
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(window, "Import failed:\n${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}
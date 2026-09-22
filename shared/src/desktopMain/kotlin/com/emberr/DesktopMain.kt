package com.emberr

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.emberr.core.security.secrets.DesktopSecretStore
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import com.emberr.di.desktopModule
import com.emberr.di.sharedModule
import com.emberr.domain.media.LocalMediaGarbageCollector
import com.emberr.domain.media.LocalMediaGcTrigger
import com.emberr.domain.selfhost.sync.ForegroundSyncPoller
import com.emberr.domain.selfhost.crypto.SecureSyncKeyStorage
import com.emberr.domain.selfhost.sync.SelfHostSyncLog
import com.emberr.domain.selfhost.sync.SelfHostSyncScheduler
import com.emberr.domain.sync.SyncRepository
import com.emberr.domain.vault.VaultLog
import com.emberr.domain.vault.VaultMirrorService
import com.emberr.presentation.EmberrApp
import com.emberr.presentation.settings.PlainTextSecretWarningDialog
import com.emberr.presentation.desktop.DesktopSearchShortcutBus
import com.emberr.presentation.desktop.DesktopRestartBus
import com.emberr.presentation.desktop.EmberrSystemTray
import com.emberr.presentation.desktop.TrayMenuAction
import com.emberr.presentation.desktop.window.CustomWindowFrameSupport
import com.emberr.presentation.desktop.window.DesktopAppRelauncher
import com.emberr.presentation.desktop.window.EmberrWindowFrame
import com.emberr.presentation.desktop.window.MatchWindowsTitleBarToAppTheme
import com.emberr.presentation.desktop.window.WindowFrameSize
import com.emberr.presentation.mobile.home.note.NoteScreen
import com.emberr.presentation.shared.StickyNoteWindowBus
import com.emberr.domain.sync.startSyncServer
import com.emberr.domain.theme.resolveLinuxSystemIsDark
import com.emberr.ui.theme.FontSizePreference
import com.emberr.ui.theme.FontStylePreference
import com.emberr.ui.theme.ThemePreference
import com.emberr.ui.theme.EmberrTheme
import com.emberr.domain.util.export.handleExportBackup
import com.emberr.domain.util.export.handleExportMarkdown
import com.emberr.domain.util.export.handleExportPdf
import com.emberr.domain.util.export.handleImportBackup
import com.emberr.presentation.navigation.Screen
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.FlowPreview
import com.emberr.presentation.reminders.ReminderClickBus
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.awt.Frame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

private const val DESKTOP_IMAGE_CACHE_BYTES = 48L * 1024 * 1024

fun main() = application {

    remember {
        startKoin {
            modules(sharedModule, desktopModule)
        }
        Unit
    }

    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(DESKTOP_IMAGE_CACHE_BYTES)
                    .build()
            }
            .components {
                add(KtorNetworkFetcherFactory())
            }
            .build()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            GlobalContext.get().get<com.emberr.domain.space.SpaceRepository>().prepareSpacesForLaunch()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            GlobalContext.get().get<DesktopSecretStore>().initialise()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val koin = GlobalContext.get()
            koin.get<DesktopSecretStore>().awaitReadyState()
            val settingsManager = koin.get<SettingsManager>()
            val syncRepository = koin.get<SyncRepository>()
            val hmacSigner = koin.get<com.emberr.core.security.SyncHmacSigner>()
            val syncEncryptionManager = koin.get<com.emberr.core.security.SyncEncryptionManager>()
            val pairingState = koin.get<com.emberr.domain.sync.SyncPairingState>()

            val serverAvailability = koin.get<com.emberr.domain.sync.SyncServerAvailability>()
            val isSyncServerRunning = startSyncServer(
                settingsManager,
                syncRepository,
                hmacSigner,
                syncEncryptionManager,
                pairingState,
                serverAvailability
            )

            if (isSyncServerRunning) {
                val discoveryManager = koin.get<com.emberr.domain.sync.discovery.SyncDiscoveryManager>()
                val port = settingsManager.getSyncPort()
                    .let { if (it <= 0) com.emberr.data.local.prefs.SyncConstants.DEFAULT_PORT else it }
                discoveryManager.startBroadcasting(port, "Emberr Desktop")
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val koin = GlobalContext.get()
            koin.get<DesktopSecretStore>().awaitReadyState()
            val secureSyncKeyStorage = koin.get<SecureSyncKeyStorage>()
            val selfHostSyncScheduler = koin.get<SelfHostSyncScheduler>()
            val foregroundSyncPoller = koin.get<ForegroundSyncPoller>()

            val isVaultConfigured = secureSyncKeyStorage.getServerCredentials() != null &&
                    secureSyncKeyStorage.getEncryptionKey() != null

            if (isVaultConfigured) {
                SelfHostSyncLog.d("DesktopMain: vault already configured, arming background sync schedules on launch")
                selfHostSyncScheduler.scheduleDailySync()
                selfHostSyncScheduler.scheduleMediaSync()
                foregroundSyncPoller.start()
            } else {
                SelfHostSyncLog.d("DesktopMain: no self-host vault configured, skipping background sync schedules")
            }
        }
    }

    LaunchedEffect(Unit) {
        val vaultMirrorService = withContext(Dispatchers.IO) {
            VaultLog.keepErrorsIn(java.io.File(System.getProperty("user.home"), ".emberr/vault-errors.txt"))
            GlobalContext.get().get<VaultMirrorService>()
        }
        vaultMirrorService.startWatching(this)
        vaultMirrorService.refreshEverythingNow()
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        val localMediaGarbageCollector = withContext(Dispatchers.IO) {
            GlobalContext.get().get<LocalMediaGarbageCollector>()
        }
        LocalMediaGcTrigger.cleanupRequests
            .debounce(2000L.milliseconds)
            .collect {
                localMediaGarbageCollector.deleteExpiredTempFiles()
            }
    }

    var isMainWindowOpen by remember { mutableStateOf(true) }
    val mainWindowState = rememberWindowState(width = 1200.dp, height = 800.dp)

    val showMainWindow = {
        isMainWindowOpen = true
        mainWindowState.isMinimized = false
    }
    val isMainWindowOnScreen = isMainWindowOpen && !mainWindowState.isMinimized

    LaunchedEffect(Unit) {
        ReminderClickBus.pendingBlockId.filterNotNull().collect { showMainWindow() }
    }

    EmberrSystemTray(
        iconResourcePath = "app_icon.png",
        tooltip = "Emberr",
        onIconClick = {
            if (isMainWindowOnScreen) isMainWindowOpen = false else showMainWindow()
        },
        actions = listOf(
            TrayMenuAction("Open Emberr") { showMainWindow() },
            TrayMenuAction("Close Window") { isMainWindowOpen = false },
            TrayMenuAction("Quit") { exitApplication() }
        )
    )

    LaunchedEffect(DesktopRestartBus.isRestartRequested) {
        if (!DesktopRestartBus.isRestartRequested) return@LaunchedEffect
        withContext(NonCancellable) { ActiveEditorRegistry.flushAllPending() }
        DesktopAppRelauncher.startNewInstance()
        exitApplication()
    }

    val mainSettingsManager = remember { GlobalContext.get().get<SettingsManager>() }
    val useCustomWindowFrame = remember {
        CustomWindowFrameSupport.isSupportedByDesktop &&
            mainSettingsManager.isCustomWindowFrameEnabled()
    }
    val autoHideTitleBar by mainSettingsManager.autoHideTitleBarFlow
        .collectAsState(initial = SyncConstants.DEFAULT_AUTO_HIDE_TITLE_BAR)

    if (isMainWindowOpen) {
    Window(
        onCloseRequest = { isMainWindowOpen = false },
        title = "Emberr",
        state = mainWindowState,
        undecorated = useCustomWindowFrame,
        transparent = useCustomWindowFrame,
        icon = painterResource("app_icon.png"),
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.F && (event.isCtrlPressed || event.isMetaPressed)) {
                DesktopSearchShortcutBus.requestOpen()
                true
            } else {
                false
            }
        }
    ) {
        val currentWindow = this.window as Frame

        LaunchedEffect(Unit) {
            ReminderClickBus.pendingBlockId.filterNotNull().collect {
                currentWindow.toFront()
                currentWindow.requestFocus()
            }
        }

        val settingsManager = remember { GlobalContext.get().get<SettingsManager>() }
        val fontSizePreferenceName by settingsManager.fontSizePreferenceFlow.collectAsState(
            initial = SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
        )
        val fontSizePreference = runCatching { FontSizePreference.valueOf(fontSizePreferenceName) }
            .getOrDefault(FontSizePreference.DEFAULT)

        val fontStylePreferenceName by settingsManager.fontStylePreferenceFlow.collectAsState(
            initial = SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
        )
        val fontStylePreference = runCatching { FontStylePreference.valueOf(fontStylePreferenceName) }
            .getOrDefault(FontStylePreference.POPPINS)

        val themePreferenceName by settingsManager.themePreferenceFlow.collectAsState(
            initial = SyncConstants.DEFAULT_THEME_PREFERENCE
        )
        val themePreference = runCatching { ThemePreference.valueOf(themePreferenceName) }
            .getOrDefault(ThemePreference.SYSTEM)
        val linuxSystemIsDark = remember { resolveLinuxSystemIsDark() }
        val systemIsDark = isSystemInDarkTheme()
        val darkTheme = when (themePreference) {
            ThemePreference.LIGHT -> false
            ThemePreference.DARK -> true
            ThemePreference.SYSTEM -> linuxSystemIsDark ?: systemIsDark
        }

        EmberrTheme(darkTheme = darkTheme, fontSizePreference = fontSizePreference, fontStylePreference = fontStylePreference) {
            MatchWindowsTitleBarToAppTheme(currentWindow)

            EmberrWindowFrame(
                windowState = mainWindowState,
                isEnabled = useCustomWindowFrame,
                windowTitle = "Emberr",
                autoHideTitleBar = autoHideTitleBar,
                onCloseRequest = { isMainWindowOpen = false }
            ) {
                EmberrApp(
                    startRoute = Screen.Splash.route,
                    onPickImage = { onPathSelected ->
                        val dialog = java.awt.FileDialog(currentWindow, "Select Image", java.awt.FileDialog.LOAD)
                        dialog.file = "*.png;*.jpg;*.jpeg;*.webp"
                        dialog.isVisible = true
                        dialog.files.firstOrNull()?.let { file -> onPathSelected(file.absolutePath) }
                    },
                    onPickDocument = { onPathSelected ->
                        val dialog = java.awt.FileDialog(currentWindow, "Select Document", java.awt.FileDialog.LOAD)
                        dialog.isVisible = true
                        dialog.files.firstOrNull()?.let { file -> onPathSelected(file.absolutePath) }
                    },
                    onOpenFile = { path, _ ->
                        try {
                            val cleanPath = path.removePrefix("file://")
                            val originalFile = if (cleanPath.contains("/") || cleanPath.contains("\\")) {
                                java.io.File(cleanPath)
                            } else {
                                java.io.File(System.getProperty("user.home"), ".emberr/media/$cleanPath")
                            }

                            if (!originalFile.exists()) {
                                SwingUtilities.invokeLater {
                                    JOptionPane.showMessageDialog(
                                        currentWindow,
                                        "This file is no longer available on this device.",
                                        "File Not Found",
                                        JOptionPane.WARNING_MESSAGE
                                    )
                                }
                            } else {
                                val tmpDir = java.io.File(System.getProperty("java.io.tmpdir"), "emberr_view").apply { mkdirs() }
                                val viewFile = java.io.File(tmpDir, originalFile.name)

                                if (!viewFile.exists() || viewFile.length() != originalFile.length()) {
                                    originalFile.copyTo(viewFile, overwrite = true)
                                }

                                java.awt.Desktop.getDesktop().open(viewFile)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            SwingUtilities.invokeLater {
                                JOptionPane.showMessageDialog(
                                    currentWindow,
                                    "Failed to open file: ${e.message}",
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                        }
                    },
                    onExportMarkdown = { fileName, content ->
                        Thread { handleExportMarkdown(currentWindow, fileName, content) }.start()
                    },
                    onExportPdf = { fileName, title, blocks ->
                        Thread { handleExportPdf(currentWindow, fileName, title, blocks) }.start()
                    },
                    onExportBackup = {
                        Thread { handleExportBackup(currentWindow) }.start()
                    },
                    onImportBackupClick = {
                        Thread { handleImportBackup(currentWindow) }.start()
                    },
                    onRequestBackupFolder = {
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(
                                currentWindow,
                                "Automated background backups are currently only supported on the Android app. You can still use the manual 'Export Backup' button!",
                                "Desktop Feature",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        }
                    }
                )

                PlainTextSecretWarningDialog()
            }
        }
    }
    }

    StickyNoteWindowBus.openNoteIds.forEach { stickyNoteId ->
        key(stickyNoteId) {
            val stickyNoteWindowState = rememberWindowState(width = 320.dp, height = 360.dp)

            Window(
                onCloseRequest = { StickyNoteWindowBus.close(stickyNoteId) },
                title = "Sticky Note",
                state = stickyNoteWindowState,
                undecorated = useCustomWindowFrame,
                transparent = useCustomWindowFrame,
                icon = painterResource("app_icon.png")
            ) {
                val settingsManager = remember { GlobalContext.get().get<SettingsManager>() }
                val fontSizePreferenceName by settingsManager.fontSizePreferenceFlow.collectAsState(
                    initial = SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
                )
                val fontSizePreference = runCatching { FontSizePreference.valueOf(fontSizePreferenceName) }
                    .getOrDefault(FontSizePreference.DEFAULT)

                val fontStylePreferenceName by settingsManager.fontStylePreferenceFlow.collectAsState(
                    initial = SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
                )
                val fontStylePreference = runCatching { FontStylePreference.valueOf(fontStylePreferenceName) }
                    .getOrDefault(FontStylePreference.POPPINS)

                val themePreferenceName by settingsManager.themePreferenceFlow.collectAsState(
                    initial = SyncConstants.DEFAULT_THEME_PREFERENCE
                )
                val themePreference = runCatching { ThemePreference.valueOf(themePreferenceName) }
                    .getOrDefault(ThemePreference.SYSTEM)
                val linuxSystemIsDark = remember { resolveLinuxSystemIsDark() }
                val systemIsDark = isSystemInDarkTheme()
                val darkTheme = when (themePreference) {
                    ThemePreference.LIGHT -> false
                    ThemePreference.DARK -> true
                    ThemePreference.SYSTEM -> linuxSystemIsDark ?: systemIsDark
                }

                val stickyWindow = this.window as Frame

                EmberrTheme(darkTheme = darkTheme, fontSizePreference = fontSizePreference, fontStylePreference = fontStylePreference) {
                    MatchWindowsTitleBarToAppTheme(stickyWindow)

                    EmberrWindowFrame(
                        windowState = stickyNoteWindowState,
                        isEnabled = useCustomWindowFrame,
                        windowTitle = "Sticky Note",
                        frameSize = WindowFrameSize.Compact,
                        autoHideTitleBar = autoHideTitleBar,
                        onCloseRequest = { StickyNoteWindowBus.close(stickyNoteId) }
                    ) {
                    NoteScreen(
                        noteId = stickyNoteId,
                        isStickyNote = true,
                        showBackButton = false,
                        onNavigateBack = {},
                        onPickImage = { onPathSelected ->
                            val dialog = java.awt.FileDialog(stickyWindow, "Select Image", java.awt.FileDialog.LOAD)
                            dialog.file = "*.png;*.jpg;*.jpeg;*.webp"
                            dialog.isVisible = true
                            dialog.files.firstOrNull()?.let { file -> onPathSelected(file.absolutePath) }
                        },
                        onPickDocument = { onPathSelected ->
                            val dialog = java.awt.FileDialog(stickyWindow, "Select Document", java.awt.FileDialog.LOAD)
                            dialog.isVisible = true
                            dialog.files.firstOrNull()?.let { file -> onPathSelected(file.absolutePath) }
                        },
                        onOpenFile = { path, _ ->
                            try {
                                val cleanPath = path.removePrefix("file://")
                                val originalFile = if (cleanPath.contains("/") || cleanPath.contains("\\")) {
                                    java.io.File(cleanPath)
                                } else {
                                    java.io.File(System.getProperty("user.home"), ".emberr/media/$cleanPath")
                                }

                                if (!originalFile.exists()) {
                                    SwingUtilities.invokeLater {
                                        JOptionPane.showMessageDialog(
                                            stickyWindow,
                                            "This file is no longer available on this device.",
                                            "File Not Found",
                                            JOptionPane.WARNING_MESSAGE
                                        )
                                    }
                                } else {
                                    val tmpDir = java.io.File(System.getProperty("java.io.tmpdir"), "emberr_view").apply { mkdirs() }
                                    val viewFile = java.io.File(tmpDir, originalFile.name)

                                    if (!viewFile.exists() || viewFile.length() != originalFile.length()) {
                                        originalFile.copyTo(viewFile, overwrite = true)
                                    }

                                    java.awt.Desktop.getDesktop().open(viewFile)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                SwingUtilities.invokeLater {
                                    JOptionPane.showMessageDialog(
                                        stickyWindow,
                                        "Failed to open file: ${e.message}",
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE
                                    )
                                }
                            }
                        },
                        onExportMarkdown = { fileName, content ->
                            Thread { handleExportMarkdown(stickyWindow, fileName, content) }.start()
                        },
                        onExportPdf = { fileName, title, blocks ->
                            Thread { handleExportPdf(stickyWindow, fileName, title, blocks) }.start()
                        }
                    )
                    }
                }
            }
        }
    }
}
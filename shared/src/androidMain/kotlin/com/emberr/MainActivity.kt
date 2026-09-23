package com.emberr

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.emberr.domain.model.PendingShare
import com.emberr.domain.util.eventbus.WidgetComposeRequest
import com.emberr.domain.vault.VaultMirrorService
import com.emberr.domain.util.eventbus.WidgetComposeRequestBus
import com.emberr.domain.util.eventbus.WidgetCalendarDateBus
import com.emberr.domain.util.eventbus.WidgetCalendarEventBus
import com.emberr.domain.util.eventbus.WidgetNavigationBus
import com.emberr.domain.util.eventbus.ShareEventBus
import com.emberr.presentation.shared.FirstContentRenderSignal
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import com.emberr.presentation.widget.calendar.refreshCalendarWidgets
import com.emberr.presentation.widget.calendaragenda.refreshCalendarAgendaWidgets
import com.emberr.presentation.widget.note.refreshNoteWidgets
import com.emberr.presentation.widget.notelist.refreshNoteListWidgets
import com.emberr.presentation.widget.noteshortcut.refreshNoteShortcutWidgets
import com.emberr.presentation.widget.todaytasks.refreshTodayTasksWidgets
import com.emberr.presentation.widget.tasks.refreshTaskWidgets
import com.emberr.presentation.widget.calendarDateUriScheme
import com.emberr.presentation.widget.calendarEventUriScheme
import com.emberr.presentation.widget.widgetDailyDateExtra
import com.emberr.presentation.widget.widgetDailyScreenExtra
import com.emberr.presentation.widget.widgetCalendarScreenExtra
import com.emberr.presentation.widget.widgetHomeScreenExtra
import com.emberr.presentation.widget.widgetNewEventExtra
import com.emberr.presentation.widget.widgetNewNoteExtra
import com.emberr.presentation.widget.widgetNewTaskExtra
import com.emberr.presentation.widget.widgetNoteIdExtra
import com.emberr.presentation.widget.widgetSpaceIdExtra
import com.emberr.presentation.widget.widgetTasksScreenExtra
import com.emberr.presentation.EmberrApp
import com.emberr.ui.theme.EmberrTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import java.util.UUID
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.emberr.domain.media.LocalMediaGarbageCollector
import com.emberr.domain.media.LocalMediaGcTrigger
import com.emberr.domain.space.DeletedSpaceTrigger
import com.emberr.domain.sync.AutoSyncTrigger
import com.emberr.presentation.widget.clearWidgetPinsForSpace
import com.emberr.domain.sync.discovery.SyncDiscoveryManager
import com.emberr.presentation.sync.SyncViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.emberr.ui.theme.FontSizePreference
import com.emberr.ui.theme.FontStylePreference
import com.emberr.ui.theme.ThemePreference
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.util.export.generateAndSaveAndroidPdf
import com.emberr.presentation.navigation.Screen
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.IntentCompat
import android.provider.OpenableColumns


private val aiWarmUpFallbackDelay = 8000L.milliseconds

class MainActivity : ComponentActivity() {

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> imagePickerCallback?.invoke(uri?.toString() ?: "") }

    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> documentPickerCallback?.invoke(uri?.toString() ?: "") }

    private var imagePickerCallback: ((String) -> Unit)? = null
    private var documentPickerCallback: ((String) -> Unit)? = null
    private var takePhotoCallback: ((String) -> Unit)? = null
    private var currentPhotoUri: Uri? = null

    private val mediaStorageHelper: com.emberr.domain.util.media.MediaStorageHelper by inject()
    private val manualBackupExporter: com.emberr.domain.backup.manual.AndroidManualBackupExporter by inject()
    private val manualBackupImporter: com.emberr.domain.backup.manual.AndroidManualBackupImporter by inject()

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            takePhotoCallback?.invoke(currentPhotoUri.toString())
        }
    }

    private val settingsViewModel: com.emberr.presentation.settings.SettingsViewModel by inject()
    private val activeSpaceStore: com.emberr.domain.space.ActiveSpaceStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !EmberrApplication.isReady }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        enableEdgeToEdge()

        lifecycleScope.launch {
            withTimeoutOrNull(aiWarmUpFallbackDelay) { FirstContentRenderSignal.awaitFirstContent() }
            (application as? EmberrApplication)?.warmUpAiEngineOnce()
        }

        val routeForThisLaunch = consumeWidgetRoute(intent) ?: Screen.Daily.route

        handleIntent(intent)

        val syncViewModel: SyncViewModel by inject()
        val discoveryManager: SyncDiscoveryManager by inject()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    syncViewModel.triggerAutoSync(discoveryManager)
                    syncViewModel.startForegroundWatchdog()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    syncViewModel.stopForegroundWatchdog()
                    lifecycleScope.launch(Dispatchers.Default) {
                        ActiveEditorRegistry.flushAllPending()
                        refreshNoteWidgets(this@MainActivity)
                        refreshTaskWidgets(this@MainActivity)
                        refreshNoteListWidgets(this@MainActivity)
                        refreshNoteShortcutWidgets(this@MainActivity)
                        refreshTodayTasksWidgets(this@MainActivity)
                        refreshCalendarWidgets(this@MainActivity)
                        refreshCalendarAgendaWidgets(this@MainActivity)
                    }
                }
                else -> {}
            }
        })

        @OptIn(FlowPreview::class)
        lifecycleScope.launch {
            AutoSyncTrigger.syncRequests
                .debounce(1500L.milliseconds)
                .collect {
                    syncViewModel.triggerFastSync()
                }
        }

        lifecycleScope.launch {
            DeletedSpaceTrigger.deletedSpaceIds.collect { deletedSpaceId ->
                clearWidgetPinsForSpace(this@MainActivity, deletedSpaceId)
            }
        }

        val localMediaGarbageCollector: LocalMediaGarbageCollector by inject()
        @OptIn(FlowPreview::class)
        lifecycleScope.launch {
            LocalMediaGcTrigger.cleanupRequests
                .debounce(2000L.milliseconds)
                .collect {
                    localMediaGarbageCollector.deleteExpiredTempFiles()
                }
        }

        // Watching holds a thread and wakes on a timer, so it only runs while the app is on screen.
        val vaultMirrorService: VaultMirrorService by inject()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vaultMirrorService.startImportingFileChanges(this)
            }
        }

        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components {
                        add(KtorNetworkFetcherFactory(HttpClient(OkHttp)))
                    }
                    .crossfade(true)
                    .build()
            }

            val fontSizePreferenceName by settingsViewModel.fontSizePreference.collectAsState()
            val fontSizePreference = runCatching { FontSizePreference.valueOf(fontSizePreferenceName) }
                .getOrDefault(FontSizePreference.DEFAULT)

            val fontStylePreferenceName by settingsViewModel.fontStylePreference.collectAsState()
            val fontStylePreference = runCatching { FontStylePreference.valueOf(fontStylePreferenceName) }
                .getOrDefault(FontStylePreference.POPPINS)

            val themePreferenceName by settingsViewModel.themePreference.collectAsState()
            val themePreference = runCatching { ThemePreference.valueOf(themePreferenceName) }
                .getOrDefault(ThemePreference.SYSTEM)
            val darkTheme = when (themePreference) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            EmberrTheme(darkTheme = darkTheme, fontSizePreference = fontSizePreference, fontStylePreference = fontStylePreference) {
                Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current

                    val backupFolderPickerLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        uri?.let {
                            try {
                                // Take persistable permission so the background worker can use it forever
                                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                context.contentResolver.takePersistableUriPermission(it, takeFlags)

                                // Save the URI and turn on the toggle in the ViewModel
                                settingsViewModel.setBackupDirectory(it.toString())
                                settingsViewModel.setAutoBackupEnabled(true)

                                Toast.makeText(context, "Backup folder linked!", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Failed to link folder.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // State to hold payloads while the OS file picker is open
                    var pendingMarkdownContent by remember { mutableStateOf("") }
                    var pendingPdfTitle by remember { mutableStateOf("") }
                    var pendingPdfBlocks by remember { mutableStateOf(emptyList<NoteBlock>()) }

                    // Markdown Saver
                    val exportMarkdownLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("text/markdown")
                    ) { uri ->
                        uri?.let {
                            try {
                                context.contentResolver.openOutputStream(it)?.use { stream ->
                                    stream.write(pendingMarkdownContent.toByteArray())
                                }
                                Toast.makeText(context, "Markdown saved", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // PDF Saver
                    val exportPdfLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/pdf")
                    ) { uri ->
                        uri?.let { generateAndSaveAndroidPdf(context, it, pendingPdfTitle, pendingPdfBlocks, mediaStorageHelper) }
                    }

                    val exportBackupLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/zip")
                    ) { uri ->
                        uri?.let { destinationUri ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    manualBackupExporter.exportToZip(destinationUri)

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Backup saved!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }

                    // BACKUP IMPORT LAUNCHER
                    val importBackupLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let { sourceUri ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    manualBackupImporter.importFromZip(sourceUri)

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }

                    EmberrApp(
                        startRoute = routeForThisLaunch,
                        onExitApp = { finish() },
                        onPickImage = { callback ->
                            imagePickerCallback = callback
                            pickImage.launch("image/*")
                        },
                        onTakePhoto = { callback ->
                            takePhotoCallback = callback

                            val photoFile = java.io.File(this@MainActivity.filesDir, "camera_${UUID.randomUUID()}.jpg")
                            if (!photoFile.exists()) {
                                photoFile.createNewFile()
                            }

                            currentPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                                this@MainActivity,
                                "${applicationContext.packageName}.fileprovider",
                                photoFile
                            )
                            takePhoto.launch(currentPhotoUri!!)
                        },
                        onPickDocument = { callback ->
                            documentPickerCallback = callback
                            pickDocument.launch("*/*")
                        },
                        onOpenFile = { filePath, mimeType ->
                            try {
                                // Use our smart helper to perfectly locate the file!
                                val absolutePath = mediaStorageHelper.getAbsoluteMediaPath(filePath)
                                val file = java.io.File(absolutePath)

                                if (!file.exists()) {
                                    Toast.makeText(this@MainActivity, "This file is no longer available on this device.", Toast.LENGTH_LONG).show()
                                } else {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        this@MainActivity,
                                        "${applicationContext.packageName}.fileprovider",
                                        file
                                    )

                                    var finalMimeType = mimeType
                                    if (finalMimeType == "*/*" || finalMimeType.isBlank()) {
                                        val extension = file.extension.lowercase()
                                        finalMimeType = android.webkit.MimeTypeMap.getSingleton()
                                            .getMimeTypeFromExtension(extension) ?: "*/*"
                                    }

                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, finalMimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(viewIntent)
                                }
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(this@MainActivity, "No app found to open this document type.", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Failed to open file: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onExportMarkdown = { fileName, content ->
                            pendingMarkdownContent = content
                            exportMarkdownLauncher.launch(fileName)
                        },
                        onExportPdf = { fileName, title, blocks ->
                            pendingPdfTitle = title
                            pendingPdfBlocks = blocks
                            exportPdfLauncher.launch(fileName)
                        },
                        onExportBackup = {
                            val fileName = "EmberrBackup_${System.currentTimeMillis()}.emberr"
                            exportBackupLauncher.launch(fileName)
                        },
                        onImportBackupClick = {
                            importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                        },
                        onRequestBackupFolder = {
                            backupFolderPickerLauncher.launch(null)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeWidgetRoute(intent)?.let { route -> WidgetNavigationBus.requestRoute(route) }
        handleIntent(intent)
    }

    private fun switchToWidgetSpaceIfRequested(intent: Intent) {
        val pinnedSpaceId = intent.getStringExtra(widgetSpaceIdExtra)?.takeIf { it.isNotBlank() }
            ?: return
        intent.removeExtra(widgetSpaceIdExtra)
        runCatching { activeSpaceStore.setActiveSpace(pinnedSpaceId) }
    }

    private fun consumeWidgetRoute(intent: Intent?): String? {
        intent ?: return null

        val noteId = intent.getStringExtra(widgetNoteIdExtra)?.takeIf { it.isNotBlank() }
        if (noteId != null) {
            intent.removeExtra(widgetNoteIdExtra)
            return Screen.Note.createRoute(noteId)
        }

        switchToWidgetSpaceIfRequested(intent)

        if (intent.getBooleanExtra(widgetTasksScreenExtra, false)) {
            intent.removeExtra(widgetTasksScreenExtra)
            if (intent.getBooleanExtra(widgetNewTaskExtra, false)) {
                intent.removeExtra(widgetNewTaskExtra)
                WidgetComposeRequestBus.request(WidgetComposeRequest.NEW_TASK)
            }
            return Screen.Reminders.route
        }

        val calendarEvent = intent.data
            ?.takeIf { uri -> uri.scheme == calendarEventUriScheme }
            ?.lastPathSegment
            ?.takeIf { it.isNotBlank() }

        if (calendarEvent != null) {
            WidgetCalendarEventBus.requestEvent(calendarEvent)
            return Screen.Calendar.route
        }

        val calendarDate = intent.data
            ?.takeIf { uri -> uri.scheme == calendarDateUriScheme }
            ?.lastPathSegment
            ?.takeIf { it.isNotBlank() }

        if (calendarDate != null) {
            WidgetCalendarDateBus.requestDate(calendarDate)
            return Screen.Calendar.route
        }

        if (intent.getBooleanExtra(widgetCalendarScreenExtra, false)) {
            intent.removeExtra(widgetCalendarScreenExtra)
            if (intent.getBooleanExtra(widgetNewEventExtra, false)) {
                intent.removeExtra(widgetNewEventExtra)
                WidgetComposeRequestBus.request(WidgetComposeRequest.NEW_EVENT)
            }
            return Screen.Calendar.route
        }

        if (intent.getBooleanExtra(widgetDailyScreenExtra, false)) {
            intent.removeExtra(widgetDailyScreenExtra)
            val dateString = intent.getStringExtra(widgetDailyDateExtra)
            intent.removeExtra(widgetDailyDateExtra)
            return Screen.Daily.createRoute(dateString)
        }

        if (intent.getBooleanExtra(widgetHomeScreenExtra, false)) {
            intent.removeExtra(widgetHomeScreenExtra)
            if (intent.getBooleanExtra(widgetNewNoteExtra, false)) {
                intent.removeExtra(widgetNewNoteExtra)
                WidgetComposeRequestBus.request(WidgetComposeRequest.NEW_NOTE)
            }
            return Screen.Home.route
        }

        return null
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return

        when (intent.action) {
            Intent.ACTION_SEND -> handleSingleShare(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(intent)
        }
    }

    private fun handleSingleShare(intent: Intent) {
        when {
            intent.type == "text/plain" -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    val extractedUrl = android.util.Patterns.WEB_URL.matcher(sharedText).let {
                        if (it.find()) it.group() else sharedText
                    }.trim()
                    ShareEventBus.postPendingShare(PendingShare.Link(extractedUrl))
                }
            }
            else -> {
                val uri = extractSingleUri(intent) ?: return
                val mimeType = resolveMimeType(uri, intent.type)
                if (mimeType.startsWith("image/")) {
                    ShareEventBus.postPendingShare(PendingShare.Image(uri.toString()))
                } else {
                    val fileName = queryDisplayName(uri) ?: "Shared file"
                    ShareEventBus.postPendingShare(PendingShare.Document(uri.toString(), mimeType, fileName))
                }
            }
        }
    }

    private fun handleMultipleShare(intent: Intent) {
        val uris = extractMultipleUris(intent)
        if (uris.isEmpty()) return

        val items: List<PendingShare> = uris.map { uri ->
            val mimeType = resolveMimeType(uri, intent.type)
            if (mimeType.startsWith("image/")) {
                PendingShare.Image(uri.toString())
            } else {
                val fileName = queryDisplayName(uri) ?: "Shared file"
                PendingShare.Document(uri.toString(), mimeType, fileName)
            }
        }

        ShareEventBus.postPendingShare(PendingShare.Multiple(items))
    }

    private fun extractSingleUri(intent: Intent): Uri? {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { return it }
        return intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun extractMultipleUris(intent: Intent): List<Uri> {
        val streamUris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        if (streamUris.isNotEmpty()) return streamUris

        val clipData = intent.clipData ?: return emptyList()
        return (0 until clipData.itemCount).mapNotNull { index -> clipData.getItemAt(index)?.uri }
    }

    private fun resolveMimeType(uri: Uri, fallback: String?): String {
        return try {
            contentResolver.getType(uri) ?: fallback ?: "*/*"
        } catch (e: Exception) {
            e.printStackTrace()
            fallback ?: "*/*"
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

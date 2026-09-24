package com.emberr.di

import android.content.Context
import android.content.SharedPreferences
import app.cash.sqldelight.db.SqlDriver
import com.emberr.core.security.AesGcmEncryptionManager
import com.emberr.core.security.AndroidSecretCipher
import com.emberr.core.security.EncryptionManager
import com.emberr.core.security.SqlCipherRuntime
import com.emberr.core.security.TinkSecretStore
import com.emberr.core.security.SyncEncryptionManager
import com.emberr.data.local.prefs.AndroidSettingsManager
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.AppDatabase
import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.BookmarkBlockDao
import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.data.local.room.dao.DatabaseTemplateDao
import com.emberr.data.local.room.dao.DocumentBlockDao
import com.emberr.data.local.room.dao.FolderDao
import com.emberr.data.local.room.dao.ImageBlockDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.dao.SelfHostDeletedNoteDao
import com.emberr.data.local.room.dao.TagDao
import com.emberr.domain.sync.SyncRepositoryImpl
import com.emberr.domain.backup.automatic.AndroidBackupRescheduler
import com.emberr.domain.backup.automatic.BackupNotifier
import com.emberr.domain.backup.automatic.BackupScheduler
import com.emberr.domain.backup.automatic.BackupSnapshotExporter
import com.emberr.domain.backup.automatic.BackupWorker
import com.emberr.domain.backup.automatic.BackupRescheduler
import com.emberr.domain.backup.manual.AndroidManualBackupExporter
import com.emberr.domain.backup.manual.AndroidManualBackupImporter
import com.emberr.database.DatabaseDriverFactory
import com.emberr.domain.ai.RagRepository
import com.emberr.domain.selfhost.crypto.KeyDerivationManager
import com.emberr.domain.selfhost.crypto.Pbkdf2KeyDerivationManager
import com.emberr.domain.selfhost.crypto.SecureSyncKeyStorage
import com.emberr.domain.selfhost.sync.SelfHostSyncScheduler
import com.emberr.domain.selfhost.sync.SelfHostSyncWorker
import com.emberr.domain.sync.SyncRepository
import com.emberr.domain.ai.tools.VaultToolExecutor
import com.emberr.domain.ai.tools.VaultToolRunner
import com.emberr.domain.vault.VaultExporter
import com.emberr.domain.vault.VaultFileLedger
import com.emberr.domain.vault.VaultFolderWatcher
import com.emberr.domain.vault.VaultImporter
import com.emberr.domain.vault.VaultMirrorService
import com.emberr.domain.vault.VaultPathMemory
import com.emberr.domain.vault.VaultStartupReconciler
import com.emberr.domain.util.voice.AndroidAudioRecorder
import com.emberr.domain.util.media.AndroidImageDownloader
import com.emberr.domain.util.media.AndroidMediaStorageHelper
import com.emberr.domain.util.voice.AudioRecorder
import com.emberr.domain.util.media.ImageDownloader
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.voice.NativeVoiceRecognizer
import com.emberr.domain.util.voice.VoiceRecognizer
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.reminders.AndroidReminderScheduler
import com.emberr.presentation.reminders.ReminderScheduler
import com.emberr.presentation.sync.SyncViewModel
import com.emberr.domain.sync.discovery.AndroidDiscoveryManager
import com.emberr.domain.sync.discovery.SyncDiscoveryManager
import com.emberr.database.EmberrDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {

    // Platform implementations
    single<MediaStorageHelper> { AndroidMediaStorageHelper(androidContext()) }
    single<ImageDownloader> { AndroidImageDownloader(androidContext()) }
    single<VoiceRecognizer> { NativeVoiceRecognizer(androidContext()) }
    single<ReminderScheduler> { AndroidReminderScheduler(androidContext()) }
    single<AudioRecorder> { AndroidAudioRecorder(androidContext()) }

    single { AndroidSecretCipher(androidContext()) }

    single<SharedPreferences> {
        androidContext().getSharedPreferences(SETTINGS_STORE_FILE_NAME, Context.MODE_PRIVATE)
    }

    single<TinkSecretStore> {
        TinkSecretStore(
            applicationContext = androidContext(),
            storeFileName = SETTINGS_SECRET_STORE_FILE_NAME,
            secretCipher = get()
        )
    }

    single<SettingsManager> {
        AndroidSettingsManager(sharedPreferences = get(), secretStore = get())
    }

    // Room
    single<ByteArray> { EncryptionManager.getDatabasePassphrase(androidContext()) }

    single<AppDatabase> {
        val passphrase = get<ByteArray>()
        SqlCipherRuntime.loadNativeLibraryAndLimitConnections()
        val supportFactory = SupportOpenHelperFactory(SqlCipherRuntime.asRawKey(passphrase))

        val builder = com.emberr.data.local.room.getDatabaseBuilder(androidContext())
        builder.openHelperFactory(supportFactory)

        com.emberr.data.local.room.getRoomDatabase(builder)
    }

    single<com.emberr.data.local.room.dao.SpaceDao> { get<AppDatabase>().spaceDao() }
    single<NoteDao> { get<AppDatabase>().noteDao() }
    single<FolderDao> { get<AppDatabase>().folderDao() }
    single<TagDao> { get<AppDatabase>().tagDao() }
    single<BlockDao> { get<AppDatabase>().blockDao() }

    single {
        com.emberr.presentation.widget.note.WidgetContentReader(
            noteDao = get(),
            blockDao = get(),
            noteRepository = get()
        )
    }

    single { com.emberr.presentation.widget.WidgetNoteSource(noteDao = get(), spaceDao = get()) }

    single {
        com.emberr.presentation.widget.calendaragenda.CalendarAgendaWidgetContentReader(
            calendarTaskDao = get(),
            categoryDao = get()
        )
    }

    single {
        com.emberr.presentation.widget.calendaragenda.CalendarAgendaWidgetCoordinator(
            context = androidContext(),
            calendarTaskDao = get()
        )
    }

    single {
        com.emberr.presentation.widget.calendar.CalendarWidgetContentReader(
            calendarTaskDao = get(),
            categoryDao = get()
        )
    }

    single {
        com.emberr.presentation.widget.calendar.CalendarWidgetCoordinator(
            context = androidContext(),
            calendarTaskDao = get()
        )
    }

    single {
        com.emberr.presentation.widget.todaytasks.TodayTasksWidgetContentReader(
            calendarTaskDao = get()
        )
    }

    single {
        com.emberr.presentation.widget.todaytasks.TodayTasksWidgetCoordinator(
            context = androidContext(),
            calendarTaskDao = get()
        )
    }

    single {
        com.emberr.presentation.widget.upcomingevents.UpcomingEventsWidgetContentReader(
            calendarTaskDao = get(),
            categoryDao = get()
        )
    }

    single {
        com.emberr.presentation.widget.upcomingevents.UpcomingEventsWidgetCoordinator(
            context = androidContext(),
            calendarTaskDao = get()
        )
    }

    single { com.emberr.presentation.widget.notelist.NoteListWidgetContentReader(noteDao = get()) }

    single { com.emberr.presentation.widget.noteshortcut.NoteShortcutWidgetContentReader(noteDao = get()) }

    single {
        com.emberr.presentation.widget.noteshortcut.NoteShortcutWidgetCoordinator(
            context = androidContext(),
            noteSource = get()
        )
    }

    single {
        com.emberr.presentation.widget.notelist.NoteListWidgetCoordinator(
            context = androidContext(),
            contentReader = get()
        )
    }

    single {
        com.emberr.presentation.widget.tasks.TasksWidgetContentReader(
            calendarTaskDao = get(),
            noteDao = get()
        )
    }

    single {
        com.emberr.presentation.widget.tasks.TasksWidgetCoordinator(
            context = androidContext(),
            contentReader = get()
        )
    }

    single {
        com.emberr.presentation.widget.note.NoteWidgetCoordinator(
            context = androidContext(),
            noteRepository = get(),
            contentReader = get()
        )
    }
    single<CalendarTaskDao> { get<AppDatabase>().calendarTaskDao() }
    single<com.emberr.data.local.room.dao.CalendarEventExceptionDao> { get<AppDatabase>().calendarEventExceptionDao() }
    single<ImageBlockDao> { get<AppDatabase>().imageBlockDao() }
    single<DocumentBlockDao> { get<AppDatabase>().documentBlockDao() }
    single<BookmarkBlockDao> { get<AppDatabase>().bookmarkBlockDao() }
    single<DatabaseTemplateDao> { get<AppDatabase>().databaseTemplateDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<SelfHostDeletedNoteDao> { get<AppDatabase>().selfHostDeletedNoteDao() }
    single<com.emberr.data.local.room.dao.ChatSessionDao> { get<AppDatabase>().chatSessionDao() }
    single<com.emberr.data.local.room.dao.SelfHostDeletedApiConfigDao> { get<AppDatabase>().selfHostDeletedApiConfigDao() }
    single<com.emberr.data.local.room.dao.MediaReferenceDao> { get<AppDatabase>().mediaReferenceDao() }

    // SQLDelight
    single<SqlDriver> { DatabaseDriverFactory(androidContext(), get<ByteArray>()).createDriver() }
    single { EmberrDatabase(get()) }

    // AI
    single { com.emberr.domain.ai.LocalAiEngine(aiSettingsRepository = get()) }
    single {
        RagRepository(
            database = get(),
            localAiEngine = get(),
            externalAiEngine = get(),
            aiSettingsRepository = get(),
            activeSpaceStore = get()
        )
    }
    single<com.emberr.domain.ai.external.SecureAiKeyStorage> {
        com.emberr.domain.ai.external.SecureAiKeyStorage(androidContext(), get())
    }
    single { com.emberr.domain.ai.models.LocalModelUploadManager(androidContext()) }
    single { com.emberr.domain.ai.models.ModelDownloadScheduler(androidContext()) }
    worker {
        com.emberr.domain.ai.models.ModelDownloadWorker(
            appContext = get(),
            workerParams = get(),
            modelDownloadManager = get()
        )
    }
    viewModel {
        RagViewModel(
            ragRepository = get(),
            aiSettingsRepository = get(),
            chatSessionRepository = get(),
            modelDownloadScheduler = get(),
            reindexAllNotesUseCase = get(),
            localModelUploadManager = get(),
            vaultToolRunner = get(),
            vaultPendingWriteEvents = get(),
            vaultToolCallEvents = get(),
            activeSpaceStore = get()
        )
    }

    // Self-hosted WebDAV sync
    single<KeyDerivationManager> { Pbkdf2KeyDerivationManager() }
    single<SecureSyncKeyStorage> { SecureSyncKeyStorage(androidContext(), get()) }
    single { SelfHostSyncScheduler(androidContext(), get(), get()) }
    worker {
        SelfHostSyncWorker(
            appContext = get(),
            workerParams = get(),
            selfHostSyncEngine = get()
        )
    }

    // Sync
    single<SyncEncryptionManager> { AesGcmEncryptionManager() }
    single<com.emberr.core.security.SyncHmacSigner> { com.emberr.core.security.HmacSha256Signer() }
    single<SyncDiscoveryManager> { AndroidDiscoveryManager(androidContext()) }
    single<com.emberr.domain.sync.SyncClient> { com.emberr.domain.sync.SyncClient(get(), get(), get()) }
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<com.emberr.domain.sync.LanSyncServerController> {
        com.emberr.domain.sync.AndroidLanSyncServerController()
    }
    viewModel { SyncViewModel(get(), get(), get(), get(), get(), get()) }

    // Manual export/import (unrelated to automatic backups below)
    single {
        AndroidManualBackupExporter(
            context = androidContext(),
            appDatabase = get(),
            settingsManager = get()
        )
    }
    single {
        AndroidManualBackupImporter(
            context = androidContext(),
            settingsManager = get(),
            backupRepository = get(),
            noteRepository = get(),
            spaceRepository = get(),
            backupRescheduler = get()
        )
    }

    // Automatic backups
    single { BackupSnapshotExporter(context = androidContext(), appDatabase = get(), settingsManager = get()) }
    worker {
        BackupWorker(
            appContext = get(),
            workerParams = get(),
            settingsManager = get(),
            backupNotifier = get(),
            backupExporter = get(),
            backupScheduler = get()
        )
    }
    single { BackupScheduler(context = get(), settingsManager = get()) }
    single { BackupNotifier(context = get()) }
    single<BackupRescheduler> { AndroidBackupRescheduler(backupScheduler = get()) }

    // Vault mirror. The folder is app-private, so it needs no storage permission and no other app
    // can reach it.
    single { VaultFileLedger() }
    single { VaultPathMemory(androidContext().filesDir) }
    single {
        VaultExporter(
            vaultRootDirectory = java.io.File(androidContext().filesDir, VAULT_FOLDER_NAME),
            noteDao = get(),
            folderDao = get(),
            spaceDao = get(),
            categoryDao = get(),
            noteRepository = get(),
            fileLedger = get(),
            pathMemory = get()
        )
    }
    single {
        com.emberr.domain.vault.VaultSpaceDirectories(
            vaultRootDirectory = get<VaultExporter>().vaultRootDirectory,
            spaceDao = get(),
            activeSpaceStore = get()
        )
    }
    single {
        VaultImporter(
            noteDao = get(),
            folderDao = get(),
            categoryDao = get(),
            noteRepository = get(),
            spaceRepository = get(),
            activeSpaceStore = get(),
            spaceDirectories = get(),
            fileLedger = get(),
            vaultExporter = get()
        )
    }
    single { VaultFolderWatcher(vaultRootDirectory = get<VaultExporter>().vaultRootDirectory) }
    single<VaultToolRunner> {
        val spaceDirectories = get<com.emberr.domain.vault.VaultSpaceDirectories>()
        VaultToolExecutor(
            activeSpace = { spaceDirectories.activeSpace() },
            vaultImporter = get<VaultImporter>(),
            pendingWriteEvents = get(),
            toolCallEvents = get()
        )
    }
    single {
        VaultStartupReconciler(
            noteDao = get(),
            noteRepository = get(),
            vaultImporter = get(),
            vaultExporter = get()
        )
    }
    single {
        VaultMirrorService(
            vaultExporter = get(),
            vaultImporter = get(),
            folderWatcher = get(),
            startupReconciler = get(),
            pathMemory = get()
        )
    }
}

private const val VAULT_FOLDER_NAME = "vault"
private const val SETTINGS_STORE_FILE_NAME = "emberr_settings"
private const val SETTINGS_SECRET_STORE_FILE_NAME = "emberr_settings_secrets"

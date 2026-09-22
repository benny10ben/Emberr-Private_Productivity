package com.emberr.di

import com.emberr.core.security.AesGcmEncryptionManager
import com.emberr.core.security.secrets.DesktopSecretStore
import com.emberr.core.security.secrets.PlaintextSecretBackend
import com.emberr.core.security.secrets.SecretBackendProbe
import com.emberr.core.security.secrets.SecretBackendSelector
import com.emberr.core.security.SyncEncryptionManager
import com.emberr.data.local.prefs.DesktopSettingsManager
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
import com.emberr.domain.backup.automatic.DesktopBackupRescheduler
import com.emberr.domain.backup.automatic.BackupRescheduler
import com.emberr.domain.backup.manual.DesktopManualBackupExporter
import com.emberr.domain.backup.manual.DesktopManualBackupImporter
import com.emberr.database.DatabaseDriverFactory
import com.emberr.domain.ai.LocalAiEngine
import com.emberr.domain.ai.RagRepository
import com.emberr.domain.ai.tools.VaultToolExecutor
import com.emberr.domain.ai.tools.VaultToolRunner
import com.emberr.domain.selfhost.crypto.KeyDerivationManager
import com.emberr.domain.selfhost.crypto.Pbkdf2KeyDerivationManager
import com.emberr.domain.selfhost.crypto.SecureSyncKeyStorage
import com.emberr.domain.selfhost.sync.SelfHostSyncScheduler
import com.emberr.domain.sync.SyncRepository
import com.emberr.domain.util.voice.AudioRecorder
import com.emberr.domain.util.voice.DesktopAudioRecorder
import com.emberr.domain.util.media.DesktopImageDownloader
import com.emberr.domain.util.media.DesktopMediaStorageHelper
import com.emberr.domain.util.voice.DesktopVoiceRecognizer
import com.emberr.domain.util.media.ImageDownloader
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.voice.VoiceRecognizer
import com.emberr.domain.vault.VaultExporter
import com.emberr.domain.vault.VaultFileLedger
import com.emberr.domain.vault.VaultFolderWatcher
import com.emberr.domain.vault.VaultImporter
import com.emberr.domain.vault.VaultMirrorService
import com.emberr.domain.vault.VaultPathMemory
import com.emberr.domain.vault.VaultStartupReconciler
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.reminders.DesktopReminderScheduler
import com.emberr.presentation.reminders.ReminderScheduler
import com.emberr.presentation.sync.SyncViewModel
import com.emberr.domain.sync.discovery.DesktopDiscoveryManager
import com.emberr.domain.sync.discovery.SyncDiscoveryManager
import com.emberr.database.EmberrDatabase
import org.koin.dsl.module

// Everything Emberr keeps on disk lives under this one folder.
private val emberrDirectory = java.io.File(System.getProperty("user.home"), ".emberr")

val desktopModule = module {

    // Room
    single<AppDatabase> {
        val builder = com.emberr.data.local.room.getDatabaseBuilder()
        builder.fallbackToDestructiveMigration(dropAllTables = true)
        com.emberr.data.local.room.getRoomDatabase(builder)
    }
    single<com.emberr.data.local.room.dao.SpaceDao> { get<AppDatabase>().spaceDao() }
    single<NoteDao> { get<AppDatabase>().noteDao() }
    single<FolderDao> { get<AppDatabase>().folderDao() }
    single<TagDao> { get<AppDatabase>().tagDao() }
    single<BlockDao> { get<AppDatabase>().blockDao() }
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
    single<VoiceRecognizer> { DesktopVoiceRecognizer() }

    // SQLDelight
    single { DatabaseDriverFactory().createDriver() }
    single { EmberrDatabase(get()) }

    // AI
    single { LocalAiEngine(aiSettingsRepository = get()) }
    single { RagRepository(get(), get(), get(), get(), get()) }
    single<com.emberr.domain.ai.external.SecureAiKeyStorage> {
        com.emberr.domain.ai.external.SecureAiKeyStorage(get())
    }
    single { com.emberr.domain.ai.models.LocalModelUploadManager() }
    single { com.emberr.domain.ai.models.ModelDownloadScheduler(modelDownloadManager = get()) }
    factory { RagViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Secret storage
    single { SecretBackendProbe() }
    single { PlaintextSecretBackend(java.io.File(System.getProperty("user.home"), ".emberr")) }
    single { SecretBackendSelector(probe = get(), plaintextBackend = get()) }
    single { DesktopSecretStore(backendSelector = get(), plaintextBackend = get()) }

    // Platform implementations
    single<SettingsManager> { DesktopSettingsManager(get()) }
    single<ReminderScheduler> { DesktopReminderScheduler() }
    single<MediaStorageHelper> { DesktopMediaStorageHelper() }
    single<ImageDownloader> { DesktopImageDownloader() }
    single<AudioRecorder> { DesktopAudioRecorder() }

    // Self-hosted WebDAV sync
    single<KeyDerivationManager> { Pbkdf2KeyDerivationManager() }
    single<SecureSyncKeyStorage> { SecureSyncKeyStorage(get()) }
    single { SelfHostSyncScheduler(selfHostSyncEngine = get()) }

    // Sync
    single<SyncEncryptionManager> { AesGcmEncryptionManager() }
    single<com.emberr.core.security.SyncHmacSigner> { com.emberr.core.security.HmacSha256Signer() }
    single<SyncDiscoveryManager> { DesktopDiscoveryManager() }
    single { com.emberr.domain.sync.SyncServerAvailability() }
    single<com.emberr.domain.sync.SyncClient> { com.emberr.domain.sync.SyncClient(get(), get(), get()) }
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<com.emberr.domain.sync.LanSyncServerController> {
        com.emberr.domain.sync.DesktopLanSyncServerController(
            settingsManager = get(),
            syncRepository = get(),
            hmacSigner = get(),
            syncEncryptionManager = get(),
            pairingState = get(),
            serverAvailability = get(),
            discoveryManager = get()
        )
    }
    factory { SyncViewModel(get(), get(), get(), get(), get(), get(), get<com.emberr.domain.sync.SyncServerAvailability>().status) }

    // Automatic Backup
    single<BackupRescheduler> { DesktopBackupRescheduler() }

    // Vault mirror
    single { VaultFileLedger() }
    single { VaultPathMemory(emberrDirectory) }
    single {
        VaultExporter(
            vaultRootDirectory = java.io.File(emberrDirectory, "vault"),
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

    // Manual export/import
    single { DesktopManualBackupExporter(appDatabase = get(), settingsManager = get()) }
    single {
        DesktopManualBackupImporter(
            settingsManager = get(),
            backupRepository = get(),
            noteRepository = get(),
            spaceRepository = get(),
            backupRescheduler = get()
        )
    }
}
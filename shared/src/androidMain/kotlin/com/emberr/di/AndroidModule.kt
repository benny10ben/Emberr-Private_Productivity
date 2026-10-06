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
import com.emberr.data.local.room.dao.DocumentBlockDao
import com.emberr.data.local.room.dao.FolderDao
import com.emberr.data.local.room.dao.ImageBlockDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.dao.SelfHostDeletedNoteDao
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
import com.emberr.domain.update.AndroidUpdateChecker
import com.emberr.domain.util.system.appVersionName
import com.emberr.domain.util.voice.AndroidAudioRecorder
import com.emberr.domain.util.media.AndroidImageDownloader
import com.emberr.domain.util.media.AndroidMediaStorageHelper
import com.emberr.domain.util.voice.AudioRecorder
import com.emberr.domain.util.media.ImageDownloader
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.voice.NativeVoiceRecognizer
import com.emberr.domain.util.voice.VoiceRecognizer
import com.emberr.presentation.ai.RagViewModel
import com.emberr.domain.reminders.AndroidReminderScheduler
import com.emberr.domain.reminders.ReminderScheduler
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
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<com.emberr.data.local.room.dao.PropertyTagDao> { get<AppDatabase>().propertyTagDao() }
    single<com.emberr.data.local.room.dao.CustomPropertyDao> { get<AppDatabase>().customPropertyDao() }
    single<SelfHostDeletedNoteDao> { get<AppDatabase>().selfHostDeletedNoteDao() }
    single<com.emberr.data.local.room.dao.ChatSessionDao> { get<AppDatabase>().chatSessionDao() }
    single<com.emberr.data.local.room.dao.SelfHostDeletedApiConfigDao> { get<AppDatabase>().selfHostDeletedApiConfigDao() }
    single<com.emberr.data.local.room.dao.MediaReferenceDao> { get<AppDatabase>().mediaReferenceDao() }
    single<com.emberr.data.local.room.dao.CanvasDao> { get<AppDatabase>().canvasDao() }
    single<com.emberr.data.local.room.dao.UnappliedSyncChangeDao> { get<AppDatabase>().unappliedSyncChangeDao() }

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
            noteToolCallEvents = get(),
            activeSpaceStore = get()
        )
    }

    // Self-hosted WebDAV sync
    single<KeyDerivationManager> { Pbkdf2KeyDerivationManager() }
    single<SecureSyncKeyStorage> { SecureSyncKeyStorage(androidContext(), get()) }
    single { SelfHostSyncScheduler(androidContext(), get(), get(), get()) }
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
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<com.emberr.domain.sync.LanSyncServerController> {
        com.emberr.domain.sync.AndroidLanSyncServerController()
    }
    viewModel { SyncViewModel(get(), get(), get(), get(), get(), get(), get()) }

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
            backupExporter = get(),
            backupRestorer = get()
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
    single {
        AndroidUpdateChecker(
            context = get(),
            settingsManager = get(),
            installedVersion = appVersionName
        )
    }
    single<BackupRescheduler> { AndroidBackupRescheduler(backupScheduler = get()) }
}

private const val SETTINGS_STORE_FILE_NAME = "emberr_settings"
private const val SETTINGS_SECRET_STORE_FILE_NAME = "emberr_settings_secrets"

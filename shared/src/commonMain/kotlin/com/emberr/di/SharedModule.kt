package com.emberr.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.repository.NoteRepositoryImpl
import com.emberr.domain.ai.NoteIndexer
import com.emberr.domain.selfhost.sync.ForegroundSyncPoller
import com.emberr.domain.selfhost.sync.SelfHostSyncEngine
import com.emberr.domain.selfhost.webdav.WebDavSyncClient
import com.emberr.domain.util.task.HeuristicTaskExtractor
import com.emberr.domain.util.task.TaskExtractor
import com.emberr.presentation.settings.selfhost.SelfHostSetupViewModel
import com.emberr.presentation.mobile.daily.DailyEditorViewModel
import com.emberr.presentation.search.SearchViewModel
import com.emberr.presentation.trash.TrashViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

val sharedModule = module {

    single<CoroutineScope>(named("AppScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { com.emberr.domain.space.ActiveSpaceStore(settingsManager = get()) }

    single {
        com.emberr.domain.space.SpaceRepository(
            spaceDao = get(),
            activeSpaceStore = get(),
            noteRepository = get(),
            chatSessionRepository = get()
        )
    }

    single {
        NoteIndexer(
            database = get(),
            aiEngine = get(),
            settingsManager = get()
        )
    }

    single {
        com.emberr.domain.ai.DisableAiFeaturesUseCase(
            database = get(),
            settingsManager = get(),
            aiSettingsRepository = get(),
            secureAiKeyStorage = get(),
            localAiEngine = get(),
            modelDownloadScheduler = get()
        )
    }

    single { com.emberr.domain.ai.models.ModelDownloadManager() }

    single {
        com.emberr.domain.repository.BookmarkCategoryOrderStore(
            settingsManager = get(),
            activeSpaceStore = get()
        )
    }
    single {
        com.emberr.domain.repository.FavoriteNoteOrderStore(
            settingsManager = get(),
            activeSpaceStore = get()
        )
    }

    single {
        com.emberr.domain.ai.ReindexAllNotesUseCase(
            noteRepository = get()
        )
    }

    single<NoteRepository> {
        NoteRepositoryImpl(
            activeSpaceStore = get(),
            noteDao = get(),
            folderDao = get(),
            tagDao = get(),
            blockDao = get(),
            noteIndexer = get(),
            calendarTaskDao = get(),
            calendarEventExceptionDao = get(),
            imageBlockDao = get(),
            documentBlockDao = get(),
            bookmarkBlockDao = get(),
            databaseTemplateDao = get(),
            categoryDao = get(),
            selfHostDeletedNoteDao = get(),
            mediaReferenceDao = get()
        )
    }

    single {
        com.emberr.domain.template.DefaultTemplateSeeder(
            repository = get(),
            activeSpaceStore = get()
        )
    }

    single {
        com.emberr.domain.sample.SampleDailyNoteSeeder(
            repository = get(),
            settingsManager = get()
        )
    }

    single {
        com.emberr.domain.sample.SampleNotesSeeder(
            repository = get(),
            settingsManager = get()
        )
    }

    single {
        com.emberr.presentation.reminders.ReminderRescheduler(
            calendarTaskDao = get(),
            calendarEventExceptionDao = get(),
            noteDao = get(),
            reminderScheduler = get()
        )
    }

    single {
        com.emberr.domain.media.MediaReferenceIndex(
            noteRepository = get(),
            noteDao = get(),
            mediaReferenceDao = get(),
            settingsManager = get()
        )
    }

    single {
        com.emberr.domain.media.LocalMediaGarbageCollector(
            mediaReferenceIndex = get(),
            mediaStorageHelper = get()
        )
    }

    single<com.emberr.domain.ai.external.AiSettingsRepository> {
        com.emberr.domain.ai.external.AiSettingsRepositoryImpl(
            settingsManager = get(),
            secureAiKeyStorage = get(),
            selfHostDeletedApiConfigDao = get()
        )
    }

    single { com.emberr.domain.ai.tools.VaultPendingWriteEvents() }
    single { com.emberr.domain.ai.tools.VaultToolCallEvents() }

    single {
        com.emberr.domain.ai.external.ExternalAiEngine(
            aiSettingsRepository = get(),
            vaultToolRunner = get()
        )
    }

    single<com.emberr.domain.ai.chat.ChatSessionRepository> {
        com.emberr.domain.ai.chat.ChatSessionRepositoryImpl(
            chatSessionDao = get(),
            activeSpaceStore = get()
        )
    }

    single<com.emberr.domain.backup.manual.BackupRepository> {
        com.emberr.domain.backup.manual.BackupRepositoryImpl(
            noteDao = get(),
            folderDao = get(),
            tagDao = get(),
            blockDao = get(),
            calendarTaskDao = get(),
            categoryDao = get(),
            imageBlockDao = get(),
            documentBlockDao = get(),
            bookmarkBlockDao = get(),
            mediaReferenceDao = get(),
            spaceDao = get(),
            chatSessionDao = get(),
            databaseTemplateDao = get(),
            calendarEventExceptionDao = get(),
            selfHostDeletedNoteDao = get(),
            settingsManager = get()
        )
    }

    viewModel {
        com.emberr.presentation.settings.SettingsViewModel(
            settingsManager = get(),
            backupRescheduler = get(),
            disableAiFeaturesUseCase = get(),
            appScope = get(named("AppScope"))
        )
    }

    viewModel {
        com.emberr.presentation.onboarding.OnboardingViewModel(
            settingsManager = get(),
            noteRepository = get(),
            mediaStorageHelper = get()
        )
    }

    viewModel {
        com.emberr.presentation.mobile.home.HomeViewModel(
            repository = get(),
            settingsManager = get(),
            reminderScheduler = get(),
            taskExtractor = get(),
            voiceRecognizer = get(),
            templateSeeder = get(),
            sampleNotesSeeder = get(),
            localMediaGarbageCollector = get(),
            favoriteNoteOrderStore = get(),
            activeSpaceStore = get()
        )
    }
    viewModel {
        com.emberr.presentation.mobile.home.overview.tasks.TasksViewModel(
            repository = get(),
            reminderScheduler = get(),
            activeSpaceStore = get()
        )
    }
    viewModel {
        com.emberr.presentation.mobile.home.overview.images.ImagesViewModel(
            repository = get(),
            mediaStorageHelper = get()
        )
    }
    viewModel {
        com.emberr.presentation.mobile.home.overview.documents.DocumentsViewModel(
            repository = get(),
            mediaStorageHelper = get()
        )
    }
    viewModel {
        com.emberr.presentation.mobile.home.overview.bookmarks.BookmarksViewModel(
            repository = get(),
            bookmarkCategoryOrderStore = get()
        )
    }
    viewModel {
        com.emberr.presentation.share.ShareViewModel(
            repository = get(),
            mediaStorageHelper = get(),
            appScope = get(named("AppScope"))
        )
    }
    viewModel {
        com.emberr.presentation.mobile.home.note.NoteEditorViewModel(
            repository = get(),
            mediaStorageHelper = get(),
            reminderScheduler = get(),
            audioRecorder = get(),
            appScope = get(named("AppScope"))
        )
    }
    viewModel {
        DailyEditorViewModel(
            repository = get(),
            mediaStorageHelper = get(),
            reminderScheduler = get(),
            audioRecorder = get(),
            appScope = get(named("AppScope")),
            sampleDailyNoteSeeder = get(),
            activeSpaceStore = get()
        )
    }
    viewModel { TrashViewModel(repository = get()) }
    viewModel { SearchViewModel(repository = get(), activeSpaceStore = get()) }
    viewModel {
        com.emberr.presentation.calendar.CalendarViewModel(
            repository = get(),
            reminderScheduler = get(),
            settingsManager = get(),
            activeSpaceStore = get()
        )
    }
    single<TaskExtractor> { HeuristicTaskExtractor() }

    single { com.emberr.domain.sync.SyncPairingState(settingsManager = get()) }

    single {
        WebDavSyncClient(
            secureSyncKeyStorage = get(),
            syncEncryptionManager = get()
        )
    }

    single {
        SelfHostSyncEngine(
            webDavSyncClient = get(),
            noteDao = get(),
            blockDao = get(),
            folderDao = get(),
            tagDao = get(),
            categoryDao = get(),
            calendarEventExceptionDao = get(),
            spaceDao = get(),
            spaceRepository = get(),
            settingsManager = get(),
            mediaStorageHelper = get(),
            noteRepository = get(),
            selfHostDeletedNoteDao = get(),
            chatSessionDao = get(),
            selfHostDeletedApiConfigDao = get(),
            aiSettingsRepository = get(),
            database = get(),
            bookmarkCategoryOrderStore = get(),
            favoriteNoteOrderStore = get(),
            mediaReferenceIndex = get()
        )
    }
    single {
        com.emberr.domain.sync.MediaRetryCoordinator(
            syncRepository = get(),
            selfHostSyncEngine = get()
        )
    }

    single {
        ForegroundSyncPoller(
            webDavSyncClient = get(),
            selfHostSyncEngine = get(),
            settingsManager = get()
        )
    }

    viewModel {
        SelfHostSetupViewModel(
            webDavSyncClient = get(),
            secureSyncKeyStorage = get(),
            keyDerivationManager = get(),
            selfHostSyncEngine = get(),
            selfHostSyncScheduler = get(),
            settingsManager = get(),
            foregroundSyncPoller = get()
        )
    }
}
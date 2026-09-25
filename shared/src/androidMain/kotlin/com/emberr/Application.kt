package com.emberr

import android.app.Application
import android.content.SharedPreferences
import com.emberr.data.local.room.AppDatabase
import com.emberr.di.androidModule
import com.emberr.di.sharedModule
import com.emberr.domain.ai.LocalAiEngine
import com.emberr.domain.backup.automatic.BackupScheduler
import com.emberr.domain.selfhost.sync.SelfHostSyncScheduler
import com.emberr.domain.vault.VaultMirrorService
import com.emberr.domain.reminders.ReminderRescheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import java.util.concurrent.atomic.AtomicBoolean

class EmberrApplication : Application() {
    companion object {
        @Volatile
        var isReady = false
    }

    private val hasStartedAiWarmUp = AtomicBoolean(false)

    // Notes can change with no screen on, so the vault export listens for as long as the process
    // is alive rather than for as long as a window is.
    private val vaultScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@EmberrApplication)
            workManagerFactory()
            modules(sharedModule, androidModule)
        }

        CoroutineScope(Dispatchers.IO).launch {
            getKoin().get<AppDatabase>()
            getKoin().get<SharedPreferences>()
            getKoin().get<com.emberr.domain.space.SpaceRepository>().prepareSpacesForLaunch()
            isReady = true
            getKoin().get<com.emberr.presentation.widget.note.NoteWidgetCoordinator>().start()
            getKoin().get<com.emberr.presentation.widget.tasks.TasksWidgetCoordinator>().start()
            getKoin().get<com.emberr.presentation.widget.notelist.NoteListWidgetCoordinator>().start()
            getKoin().get<com.emberr.presentation.widget.noteshortcut.NoteShortcutWidgetCoordinator>().start()
            getKoin().get<com.emberr.presentation.widget.todaytasks.TodayTasksWidgetCoordinator>().start()
            getKoin().get<com.emberr.presentation.widget.calendar.CalendarWidgetCoordinator>().start()
            getKoin().get<com.emberr.presentation.widget.calendaragenda.CalendarAgendaWidgetCoordinator>().start()
            getKoin().get<com.emberr.presentation.widget.upcomingevents.UpcomingEventsWidgetCoordinator>().start()
            getKoin().get<ReminderRescheduler>().rescheduleUpcomingReminders()
            getKoin().get<BackupScheduler>()
            getKoin().get<SelfHostSyncScheduler>()

            val vaultMirrorService = getKoin().get<VaultMirrorService>()
            vaultMirrorService.startExportingAppChanges(vaultScope)
            vaultMirrorService.refreshEverythingNow()
        }

    }

    fun warmUpAiEngineOnce() {
        if (!hasStartedAiWarmUp.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (getKoin().get<com.emberr.data.local.prefs.SettingsManager>().isAiFeaturesDisabled()) return@launch
                getKoin().get<LocalAiEngine>().warmUpGenerator()
            } catch (e: Exception) {
                // Silent — if pre-warm fails, first query just pays the load cost
            }
        }
    }
}
package com.emberr.domain.backup.manual

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.database.EmberrDatabase
import com.emberr.domain.ai.ReindexAllNotesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun resetLanSyncProgressIfRestoreUnfinished(settingsManager: SettingsManager) {
    if (!settingsManager.isRestoreUnfinished()) return
    settingsManager.saveLastPushedTimestamp(0L)
    settingsManager.saveLastFetchedTimestamp(0L)
}

class UnfinishedRestoreFinisher(
    private val settingsManager: SettingsManager,
    private val vectorDatabase: EmberrDatabase,
    private val reindexAllNotesUseCase: ReindexAllNotesUseCase,
    private val appScope: CoroutineScope
) {

    fun finishIfNeeded() {
        if (!settingsManager.isRestoreUnfinished()) return

        appScope.launch(Dispatchers.IO) {
            try {
                vectorDatabase.vectorStoreQueries.deleteAllBlocks()
                reindexAllNotesUseCase.execute().collect()
                settingsManager.saveRestoreUnfinished(false)
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                cause.printStackTrace()
            }
        }
    }
}

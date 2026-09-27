package com.emberr.domain.database

import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.domain.canvas.EmbeddedCanvasCleanup.DELETED_BLOCK_GRACE_PERIOD_MILLIS
import com.emberr.domain.media.LocalMediaGcLog
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object DatabaseRowCleanup {
    const val DATABASE_BLOCK_JSON_MARKER = "\"type\":\"database\""
    const val DELETED_BLOCK_GRACE_PERIOD_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun databasesToDeleteAtLaunch(
        databaseIdsWithRows: Set<String>,
        databaseBlocks: List<DatabaseBlock>,
        now: Long
    ): Set<String> {
        val blocksByDatabaseId = databaseBlocks.groupBy { it.databaseId }
        return databaseIdsWithRows.filterTo(HashSet()) { databaseId ->
            val blocksShowingDatabase = blocksByDatabaseId[databaseId].orEmpty()
            blocksShowingDatabase.isNotEmpty() && blocksShowingDatabase.all { block ->
                block.isDeleted && now - block.updatedAt >= DELETED_BLOCK_GRACE_PERIOD_MILLIS
            }
        }
    }

    fun databasesNoLiveBlockUses(candidateDatabaseIds: Set<String>, databaseBlocks: List<DatabaseBlock>): Set<String> {
        val usedDatabaseIds = databaseBlocks.filter { !it.isDeleted }.mapTo(HashSet()) { it.databaseId }
        return candidateDatabaseIds - usedDatabaseIds
    }
}

class DatabaseRowCleaner(
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val noteRepository: NoteRepository
) {
    private val blockJson = Json { ignoreUnknownKeys = true }

    suspend fun deleteRowsOfRemovedDatabases() = withContext(Dispatchers.IO) {
        try {
            val databaseIdsWithRows = noteDao.getDatabaseIdsThatHaveRows().toSet()
            if (databaseIdsWithRows.isEmpty()) return@withContext

            val databaseBlocks = blockDao.findBlocksContainingIncludingDeleted(DatabaseRowCleanup.DATABASE_BLOCK_JSON_MARKER)
                .mapNotNull { entity ->
                    runCatching { blockJson.decodeFromString<NoteBlock>(entity.blockDataJson) }.getOrNull() as? DatabaseBlock
                }
            val databaseIdsToDelete = DatabaseRowCleanup.databasesToDeleteAtLaunch(
                databaseIdsWithRows = databaseIdsWithRows,
                databaseBlocks = databaseBlocks,
                now = System.currentTimeMillis()
            )

            var deletedRowCount = 0
            databaseIdsToDelete.forEach { databaseId ->
                noteDao.getAllRowNotesIncludingTrashed(databaseId).forEach { rowNote ->
                    noteRepository.deleteNote(rowNote.noteId, "")
                    deletedRowCount++
                }
            }
            LocalMediaGcLog.d("deleteRowsOfRemovedDatabases: deleted $deletedRowCount row(s) of ${databaseIdsToDelete.size} database(s)")
        } catch (e: Exception) {
            LocalMediaGcLog.e("deleteRowsOfRemovedDatabases: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }
}

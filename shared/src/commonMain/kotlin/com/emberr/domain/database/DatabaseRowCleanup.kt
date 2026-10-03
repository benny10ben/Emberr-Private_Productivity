package com.emberr.domain.database

import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.domain.media.LocalMediaGcLog
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object DatabaseRowCleanup {
    const val DATABASE_BLOCK_JSON_MARKER = "\"type\":\"database\""
    const val DELETED_BLOCK_GRACE_PERIOD_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun databasesToDeleteAtLaunch(
        knownDatabaseIds: Set<String>,
        databaseBlocks: List<DatabaseBlock>,
        now: Long
    ): Set<String> {
        val blocksByDatabaseId = databaseBlocks.filterNot { it.holdsSharedSettings }.groupBy { it.databaseId }
        return knownDatabaseIds.filterTo(HashSet()) { databaseId ->
            val blocksShowingDatabase = blocksByDatabaseId[databaseId].orEmpty()
            blocksShowingDatabase.isNotEmpty() && blocksShowingDatabase.all { block ->
                block.isDeleted && now - block.updatedAt >= DELETED_BLOCK_GRACE_PERIOD_MILLIS
            }
        }
    }

    fun databasesNoBlockMentions(candidateDatabaseIds: Set<String>, databaseBlocks: List<DatabaseBlock>): Set<String> =
        candidateDatabaseIds - databaseBlocks.filterNot { it.holdsSharedSettings }.mapTo(HashSet()) { it.databaseId }

    fun isUntouchedLongEnoughToDeleteWhenEmpty(lastEditedAt: Long, now: Long): Boolean =
        now - lastEditedAt >= DELETED_BLOCK_GRACE_PERIOD_MILLIS

    fun databasesNoLiveBlockUses(candidateDatabaseIds: Set<String>, databaseBlocks: List<DatabaseBlock>): Set<String> {
        val usedDatabaseIds = databaseBlocks.filter { !it.isDeleted && !it.holdsSharedSettings }.mapTo(HashSet()) { it.databaseId }
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
            val databaseBlocks = blockDao.findBlocksContainingIncludingDeleted(DatabaseRowCleanup.DATABASE_BLOCK_JSON_MARKER)
                .mapNotNull { entity ->
                    runCatching { blockJson.decodeFromString<NoteBlock>(entity.blockDataJson) }.getOrNull() as? DatabaseBlock
                }
            val databaseIdsWithSettings = databaseBlocks.filter { it.holdsSharedSettings }.mapTo(HashSet()) { it.databaseId }
            val databaseIdsWithRows = noteDao.getDatabaseIdsThatHaveRows().toSet()
            val knownDatabaseIds = databaseIdsWithRows + databaseIdsWithSettings
            if (knownDatabaseIds.isEmpty()) return@withContext
            val now = System.currentTimeMillis()

            val databaseIdsToDelete = DatabaseRowCleanup.databasesToDeleteAtLaunch(
                knownDatabaseIds = knownDatabaseIds,
                databaseBlocks = databaseBlocks,
                now = now
            )

            var deletedRowCount = 0
            databaseIdsToDelete.forEach { databaseId ->
                noteDao.getAllRowNotesIncludingTrashed(databaseId).forEach { rowNote ->
                    noteRepository.deleteNote(rowNote.noteId, "")
                    deletedRowCount++
                }
                if (noteDao.getNoteById(databaseNoteId(databaseId)) != null) noteRepository.deleteNote(databaseNoteId(databaseId), "")
            }

            val abandonedEmptyDatabaseIds = DatabaseRowCleanup.databasesNoBlockMentions(databaseIdsWithSettings - databaseIdsWithRows, databaseBlocks)
                .filter { databaseId ->
                    val databaseNote = noteDao.getNoteById(databaseNoteId(databaseId))
                    databaseNote != null && DatabaseRowCleanup.isUntouchedLongEnoughToDeleteWhenEmpty(databaseNote.updatedAt, now)
                }
            abandonedEmptyDatabaseIds.forEach { databaseId -> noteRepository.deleteNote(databaseNoteId(databaseId), "") }
            LocalMediaGcLog.d(
                "deleteRowsOfRemovedDatabases: deleted $deletedRowCount row(s) of ${databaseIdsToDelete.size} database(s) " +
                    "and ${abandonedEmptyDatabaseIds.size} abandoned empty database(s)"
            )
        } catch (e: Exception) {
            LocalMediaGcLog.e("deleteRowsOfRemovedDatabases: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }
}

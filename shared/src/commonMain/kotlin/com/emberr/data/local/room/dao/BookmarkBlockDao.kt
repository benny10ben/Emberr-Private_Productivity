package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.BookmarkBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkBlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmarks(bookmarks: List<BookmarkBlockEntity>)

    @Query("DELETE FROM bookmark_blocks WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("SELECT * FROM bookmark_blocks WHERE noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId) ORDER BY noteUpdatedAt DESC")
    fun getAllBookmarksFlow(spaceId: String): Flow<List<BookmarkBlockEntity>>

    @Query("SELECT COUNT(*) FROM bookmark_blocks WHERE noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId)")
    fun getBookmarksCount(spaceId: String): Flow<Int>

    @Query("SELECT * FROM bookmark_blocks ORDER BY noteUpdatedAt DESC")
    fun getAllBookmarksAcrossSpacesFlow(): Flow<List<BookmarkBlockEntity>>
}

package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseCopiesTest {

    @Test
    fun aCopiedNoteGetsNewDatabasesWithTheSameSettings() {
        val text = TextBlock(id = "t1", text = "Reading plan")
        val books = DatabaseBlock(id = "b1", databaseId = "books", title = "Books", columns = listOf(DatabaseColumnTarget.Property(PropertyType.STATUS)))
        val booksAgain = DatabaseBlock(id = "b2", databaseId = "books", title = "Books again")
        val films = DatabaseBlock(id = "b3", databaseId = "films")
        var nextId = 0

        val copy = NoteContent(blocks = listOf(text, books, booksAgain, films)).withFreshDatabaseIds { "fresh-${nextId++}" }

        assertEquals(
            listOf(
                text,
                books.copy(databaseId = "fresh-0"),
                booksAgain.copy(databaseId = "fresh-0"),
                films.copy(databaseId = "fresh-1")
            ),
            copy.blocks
        )
    }
}

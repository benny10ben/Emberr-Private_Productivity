package com.emberr.domain.database

import com.emberr.domain.model.DEFAULT_VIEW_ID
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCardSize
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DatabaseViewsTest {

    private val database = DatabaseBlock(id = "block", databaseId = "books")
    private val gallery = DatabaseView(id = "gallery-id", name = "Gallery", type = DatabaseViewType.GALLERY)

    @Test
    fun aDatabaseWithoutSavedViewsShowsOneTableView() {
        assertEquals(listOf(DEFAULT_VIEW_ID), database.allViews().map { it.id })
        assertEquals(DatabaseViewType.TABLE, database.activeView().type)
    }

    @Test
    fun addingAViewKeepsTheTableViewAndOpensTheNewOne() {
        val result = database.withViewAdded(gallery)

        assertEquals(listOf(DEFAULT_VIEW_ID, "gallery-id"), result.allViews().map { it.id })
        assertEquals(gallery, result.activeView())
    }

    @Test
    fun anUnknownActiveViewFallsBackToTheFirstView() {
        val result = database.withViewAdded(gallery).copy(activeViewId = "deleted-elsewhere")

        assertEquals(DEFAULT_VIEW_ID, result.activeView().id)
    }

    @Test
    fun deletingTheOpenViewOpensTheFirstRemainingOne() {
        val result = database.withViewAdded(gallery).withViewDeleted("gallery-id")

        assertEquals(listOf(DEFAULT_VIEW_ID), result.allViews().map { it.id })
        assertEquals(DEFAULT_VIEW_ID, result.activeView().id)
    }

    @Test
    fun theLastViewCannotBeDeleted() {
        assertSame(database, database.withViewDeleted(DEFAULT_VIEW_ID))
    }

    @Test
    fun changingTheDefaultTableViewSavesItWithItsChange() {
        val result = database.withViewChanged(DEFAULT_VIEW_ID) { it.copy(showsIcon = false) }

        assertEquals(listOf(false), result.views.map { it.showsIcon })
        assertEquals(DEFAULT_VIEW_ID, result.views.single().id)
    }

    @Test
    fun aNewViewGetsTheFirstFreeNumberedName() {
        val withGallery = database.withViewAdded(gallery)

        assertEquals("Gallery 2", withGallery.newViewName(DatabaseViewType.GALLERY))
        assertEquals("Table 2", withGallery.newViewName(DatabaseViewType.TABLE))
        assertEquals("Gallery", database.newViewName(DatabaseViewType.GALLERY))
    }

    @Test
    fun choosingTheViewThatIsAlreadyOpenChangesNothing() {
        assertSame(database, database.withActiveView(DEFAULT_VIEW_ID, now = 500L))
    }

    @Test
    fun choosingAnotherViewRemembersItAndStampsTheChoiceForSync() {
        val withGallery = database.withViewAdded(gallery).copy(activeViewId = DEFAULT_VIEW_ID)

        val result = withGallery.withActiveView("gallery-id", now = 500L)

        assertEquals("gallery-id", result.activeViewId)
        assertEquals(500L, result.settingTimes["active_view"]?.updatedAt)
    }

    @Test
    fun aViewSavedBeforeCardSizesExistedOpensWithMediumCards() {
        val view = Json.decodeFromString<DatabaseView>("""{"id":"gallery-id","name":"Gallery","type":"GALLERY"}""")

        assertEquals(DatabaseCardSize.MEDIUM, view.cardSize)
    }
}

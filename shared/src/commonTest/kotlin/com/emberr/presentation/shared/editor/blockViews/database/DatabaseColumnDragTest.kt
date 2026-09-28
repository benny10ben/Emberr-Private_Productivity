package com.emberr.presentation.shared.editor.blockViews.database

import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.PropertyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseColumnDragTest {

    private val notesColumn = DatabaseColumnTarget.NotesTitle
    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val dateColumn = DatabaseColumnTarget.Property(PropertyType.DATE)
    private val tagsColumn = DatabaseColumnTarget.Property(PropertyType.TAGS)
    private val columns = listOf(notesColumn, statusColumn, dateColumn, tagsColumn)
    private val headerSpans = mapOf(
        notesColumn to HeaderSpan(left = 0f, right = 240f),
        statusColumn to HeaderSpan(left = 240f, right = 420f),
        dateColumn to HeaderSpan(left = 420f, right = 600f),
        tagsColumn to HeaderSpan(left = 600f, right = 780f)
    )

    @Test
    fun theDropGoesBeforeTheFirstColumnWhoseMiddleIsRightOfThePointer() {
        assertEquals(notesColumn, columnToDropBefore(columns, headerSpans, pointerX = 20f))
        assertEquals(statusColumn, columnToDropBefore(columns, headerSpans, pointerX = 250f))
        assertEquals(dateColumn, columnToDropBefore(columns, headerSpans, pointerX = 400f))
        assertEquals(tagsColumn, columnToDropBefore(columns, headerSpans, pointerX = 689f))
    }

    @Test
    fun droppingPastTheMiddleOfTheLastColumnGoesToTheEnd() {
        assertNull(columnToDropBefore(columns, headerSpans, pointerX = 700f))
        assertNull(columnToDropBefore(columns, headerSpans, pointerX = 5000f))
    }

    @Test
    fun droppingNextToItselfLeavesTheColumnWhereItIs() {
        assertTrue(dropKeepsColumnInPlace(columns, draggedColumn = dateColumn, beforeColumn = dateColumn))
        assertTrue(dropKeepsColumnInPlace(columns, draggedColumn = dateColumn, beforeColumn = tagsColumn))
        assertTrue(dropKeepsColumnInPlace(columns, draggedColumn = tagsColumn, beforeColumn = null))
    }

    @Test
    fun droppingAnywhereElseMovesTheColumn() {
        assertFalse(dropKeepsColumnInPlace(columns, draggedColumn = dateColumn, beforeColumn = statusColumn))
        assertFalse(dropKeepsColumnInPlace(columns, draggedColumn = dateColumn, beforeColumn = null))
        assertFalse(dropKeepsColumnInPlace(columns, draggedColumn = statusColumn, beforeColumn = tagsColumn))
        assertFalse(dropKeepsColumnInPlace(columns, draggedColumn = notesColumn, beforeColumn = tagsColumn))
    }

    @Test
    fun theDraggedColumnFollowsWherePointerIsNotHowFarItHasMoved() {
        val dragState = DatabaseColumnDragState()
        dragState.headerSpans.putAll(headerSpans)

        dragState.start(dateColumn, grabXInHeader = 30f)
        dragState.moveTo(dateColumn, pointerXInHeader = 130f)
        assertEquals(100f, dragState.offsetOf(dateColumn))
        assertEquals(550f, dragState.pointerX)

        dragState.moveTo(dateColumn, pointerXInHeader = 30f)
        assertEquals(0f, dragState.offsetOf(dateColumn))
        assertEquals(0f, dragState.offsetOf(statusColumn))
    }

    @Test
    fun movesForAColumnThatIsNotBeingDraggedAreIgnored() {
        val dragState = DatabaseColumnDragState()
        dragState.headerSpans.putAll(headerSpans)

        dragState.start(dateColumn, grabXInHeader = 30f)
        dragState.moveTo(statusColumn, pointerXInHeader = 500f)
        assertEquals(450f, dragState.pointerX)

        dragState.reset()
        dragState.moveTo(dateColumn, pointerXInHeader = 500f)
        assertEquals(450f, dragState.pointerX)
        assertNull(dragState.draggedColumn)
    }

    @Test
    fun autoScrollOnlyHappensNearAnEdgeAndSpeedsUpTowardsIt() {
        assertEquals(0f, columnAutoScrollStep(pointerInViewport = 500f, viewportWidth = 1000f, edgeWidth = 50f, maxStep = 10f))
        assertEquals(-5f, columnAutoScrollStep(pointerInViewport = 25f, viewportWidth = 1000f, edgeWidth = 50f, maxStep = 10f))
        assertEquals(5f, columnAutoScrollStep(pointerInViewport = 975f, viewportWidth = 1000f, edgeWidth = 50f, maxStep = 10f))
    }

    @Test
    fun autoScrollNeverGoesFasterThanTheMaximumStep() {
        assertEquals(-10f, columnAutoScrollStep(pointerInViewport = -300f, viewportWidth = 1000f, edgeWidth = 50f, maxStep = 10f))
        assertEquals(10f, columnAutoScrollStep(pointerInViewport = 1600f, viewportWidth = 1000f, edgeWidth = 50f, maxStep = 10f))
        assertEquals(0f, columnAutoScrollStep(pointerInViewport = 10f, viewportWidth = 0f, edgeWidth = 50f, maxStep = 10f))
    }
}

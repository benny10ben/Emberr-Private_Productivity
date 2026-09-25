package com.emberr.domain.canvas

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasContentCopyTest {

    private fun node(nodeId: String, text: String, isDeleted: Boolean = false) = CanvasNodeEntity(
        nodeId = nodeId,
        noteId = "template-canvas",
        x = 10f,
        y = 20f,
        width = 200f,
        height = 100f,
        text = text,
        createdAt = 5L,
        updatedAt = 6L,
        isDeleted = isDeleted
    )

    private fun edge(edgeId: String, fromNodeId: String, toNodeId: String, isDeleted: Boolean = false) = CanvasEdgeEntity(
        edgeId = edgeId,
        noteId = "template-canvas",
        fromNodeId = fromNodeId,
        fromSide = CanvasSide.RIGHT,
        toNodeId = toNodeId,
        toSide = CanvasSide.LEFT,
        createdAt = 5L,
        updatedAt = 6L,
        isDeleted = isDeleted
    )

    private fun copyOf(content: CanvasContent): CanvasContent {
        var generatedIdCount = 0
        return content.copiedForNote(noteId = "new-canvas", now = 900L) { "copy-${generatedIdCount++}" }
    }

    @Test
    fun theCopyBelongsToTheNewCanvasWithFreshIdsAndKeepsTheBoxContents() {
        val original = CanvasContent(nodes = listOf(node("agenda", "Agenda")))

        val copy = copyOf(original)

        assertEquals(
            listOf(node("agenda", "Agenda").copy(nodeId = "copy-0", noteId = "new-canvas", updatedAt = 900L)),
            copy.nodes
        )
    }

    @Test
    fun arrowsStayConnectedToTheCopiedBoxes() {
        val original = CanvasContent(
            nodes = listOf(node("agenda", "Agenda"), node("notes", "Notes")),
            edges = listOf(edge("arrow", fromNodeId = "agenda", toNodeId = "notes"))
        )

        val copy = copyOf(original)

        val copiedAgendaId = copy.nodes.single { it.text == "Agenda" }.nodeId
        val copiedNotesId = copy.nodes.single { it.text == "Notes" }.nodeId
        val copiedArrow = copy.edges.single()
        assertEquals(copiedAgendaId, copiedArrow.fromNodeId)
        assertEquals(copiedNotesId, copiedArrow.toNodeId)
        assertEquals("new-canvas", copiedArrow.noteId)
        assertTrue(copiedArrow.edgeId !in setOf("arrow", copiedAgendaId, copiedNotesId))
    }

    @Test
    fun deletedBoxesAndArrowsAreNotCopied() {
        val original = CanvasContent(
            nodes = listOf(node("agenda", "Agenda"), node("removed", "Removed", isDeleted = true)),
            edges = listOf(
                edge("removed-arrow", fromNodeId = "agenda", toNodeId = "agenda", isDeleted = true),
                edge("arrow-to-removed-box", fromNodeId = "agenda", toNodeId = "removed")
            )
        )

        val copy = copyOf(original)

        assertEquals(listOf("Agenda"), copy.nodes.map { it.text })
        assertTrue(copy.edges.isEmpty())
    }
}

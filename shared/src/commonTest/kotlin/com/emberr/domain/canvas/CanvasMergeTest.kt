package com.emberr.domain.canvas

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeShape
import com.emberr.data.local.room.entity.CanvasNodeType
import com.emberr.data.local.room.entity.CanvasSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasMergeTest {

    private fun node(nodeId: String, updatedAt: Long, text: String = "", isDeleted: Boolean = false) = CanvasNodeEntity(
        nodeId = nodeId,
        noteId = "canvas-1",
        x = 0f,
        y = 0f,
        width = 200f,
        height = 100f,
        text = text,
        createdAt = 1L,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun edge(edgeId: String, fromNodeId: String, toNodeId: String, updatedAt: Long, isDeleted: Boolean = false) =
        CanvasEdgeEntity(
            edgeId = edgeId,
            noteId = "canvas-1",
            fromNodeId = fromNodeId,
            fromSide = CanvasSide.RIGHT,
            toNodeId = toNodeId,
            toSide = CanvasSide.LEFT,
            createdAt = 1L,
            updatedAt = updatedAt,
            isDeleted = isDeleted
        )

    @Test
    fun aBoxEditedLaterOnTheOtherDeviceReplacesTheLocalBox() {
        val local = CanvasContent(nodes = listOf(node("a", updatedAt = 100L, text = "old")))
        val remote = CanvasContent(nodes = listOf(node("a", updatedAt = 200L, text = "new")))

        val winners = CanvasMerge.remoteItemsNewerThanLocal(local, remote)

        assertEquals(listOf("new"), winners.nodes.map { it.text })
    }

    @Test
    fun aBoxEditedAtTheSameMomentOnBothDevicesKeepsTheLocalVersion() {
        val local = CanvasContent(nodes = listOf(node("a", updatedAt = 100L, text = "local")))
        val remote = CanvasContent(nodes = listOf(node("a", updatedAt = 100L, text = "remote")))

        assertTrue(CanvasMerge.remoteItemsNewerThanLocal(local, remote).isEmpty())
    }

    @Test
    fun anOlderRemoteBoxNeverOverwritesANewerLocalEdit() {
        val local = CanvasContent(nodes = listOf(node("a", updatedAt = 300L)))
        val remote = CanvasContent(nodes = listOf(node("a", updatedAt = 200L)))

        assertTrue(CanvasMerge.remoteItemsNewerThanLocal(local, remote).isEmpty())
    }

    @Test
    fun boxesAndArrowsThatOnlyExistRemotelyAreAdded() {
        val remote = CanvasContent(
            nodes = listOf(node("a", updatedAt = 100L), node("b", updatedAt = 100L)),
            edges = listOf(edge("e", fromNodeId = "a", toNodeId = "b", updatedAt = 100L))
        )

        val winners = CanvasMerge.remoteItemsNewerThanLocal(CanvasContent(), remote)

        assertEquals(remote, winners)
    }

    @Test
    fun aBoxDeletedLaterOnTheOtherDeviceIsDeletedHereToo() {
        val local = CanvasContent(nodes = listOf(node("a", updatedAt = 100L)))
        val remote = CanvasContent(nodes = listOf(node("a", updatedAt = 200L, isDeleted = true)))

        val winners = CanvasMerge.remoteItemsNewerThanLocal(local, remote)

        assertTrue(winners.nodes.single().isDeleted)
    }

    @Test
    fun anEditMadeAfterTheOtherDeviceDeletedTheBoxBringsItBack() {
        val local = CanvasContent(nodes = listOf(node("a", updatedAt = 100L, isDeleted = true)))
        val remote = CanvasContent(nodes = listOf(node("a", updatedAt = 200L, text = "still wanted")))

        val winners = CanvasMerge.remoteItemsNewerThanLocal(local, remote)

        assertEquals(false, winners.nodes.single().isDeleted)
    }

    @Test
    fun aGroupStaysAGroupWhenAnOlderAppSendsItBackWithoutItsType() {
        val local = CanvasContent(nodes = listOf(node("g", updatedAt = 100L).copy(type = CanvasNodeType.GROUP)))
        val remoteFromOlderApp = CanvasContent(nodes = listOf(node("g", updatedAt = 200L, text = "Moved")))

        val winner = CanvasMerge.remoteItemsNewerThanLocal(local, remoteFromOlderApp).nodes.single()

        assertEquals(CanvasNodeType.GROUP, winner.type)
        assertEquals("Moved", winner.text)
    }

    @Test
    fun aShapeKeepsItsShapeWhenAnOlderAppSendsItBackWithoutOne() {
        val local = CanvasContent(nodes = listOf(node("s", updatedAt = 100L).copy(shape = CanvasNodeShape.DATABASE)))
        val remoteFromOlderApp = CanvasContent(nodes = listOf(node("s", updatedAt = 200L, text = "Users")))

        val winner = CanvasMerge.remoteItemsNewerThanLocal(local, remoteFromOlderApp).nodes.single()

        assertEquals(CanvasNodeShape.DATABASE, winner.shape)
        assertEquals("Users", winner.text)
    }

    @Test
    fun onlyBoxesFullyInsideAGroupCountAsItsMembers() {
        val group = node("g", 1L).copy(x = 0f, y = 0f, width = 500f, height = 300f, type = CanvasNodeType.GROUP)
        val inside = node("inside", 1L).copy(x = 20f, y = 20f, width = 200f, height = 100f)
        val halfOut = node("half-out", 1L).copy(x = 400f, y = 20f, width = 200f, height = 100f)
        val outside = node("outside", 1L).copy(x = 900f, y = 900f)
        val canvas = CanvasContent(nodes = listOf(group, inside, halfOut, outside))

        assertEquals(listOf("inside"), canvas.membersOf(group).map { it.nodeId })
        assertTrue(group.isGroup)
    }

    @Test
    fun liveOnlyHidesDeletedItemsAndArrowsPointingAtDeletedBoxes() {
        val canvas = CanvasContent(
            nodes = listOf(node("a", 1L), node("b", 1L), node("gone", 1L, isDeleted = true)),
            edges = listOf(
                edge("kept", fromNodeId = "a", toNodeId = "b", updatedAt = 1L),
                edge("to-deleted-box", fromNodeId = "a", toNodeId = "gone", updatedAt = 1L),
                edge("deleted", fromNodeId = "b", toNodeId = "a", updatedAt = 1L, isDeleted = true)
            )
        )

        val live = canvas.liveOnly()

        assertEquals(listOf("a", "b"), live.nodes.map { it.nodeId })
        assertEquals(listOf("kept"), live.edges.map { it.edgeId })
    }

    @Test
    fun withNoteIdMovesEveryItemOntoTheGivenCanvas() {
        val canvas = CanvasContent(
            nodes = listOf(node("a", 1L)),
            edges = listOf(edge("e", fromNodeId = "a", toNodeId = "a", updatedAt = 1L))
        )

        val moved = canvas.withNoteId("canvas-2")

        assertTrue(moved.nodes.all { it.noteId == "canvas-2" })
        assertTrue(moved.edges.all { it.noteId == "canvas-2" })
    }
}

package com.emberr.domain.canvas

import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeType
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasImageFileNamesTest {

    private fun image(nodeId: String, imagePath: String?, isDeleted: Boolean = false) = CanvasNodeEntity(
        nodeId = nodeId,
        noteId = "canvas-1",
        x = 0f,
        y = 0f,
        width = 320f,
        height = 240f,
        text = "",
        createdAt = 1L,
        updatedAt = 1L,
        isDeleted = isDeleted,
        type = CanvasNodeType.IMAGE,
        imagePath = imagePath
    )

    @Test
    fun onlyImagesThatAreStillOnTheBoardAreListed() {
        val canvas = CanvasContent(
            nodes = listOf(
                image("a", "media_kept.jpg"),
                image("b", "media_deleted.jpg", isDeleted = true),
                image("c", null)
            )
        )

        assertEquals(setOf("media_kept.jpg"), canvas.liveImageFileNames())
    }

    @Test
    fun aStoredFolderIsDroppedSoOnlyTheFileNameIsCompared() {
        val canvas = CanvasContent(nodes = listOf(image("a", "/old/device/media/media_1.png"), image("b", "media_1.png")))

        assertEquals(setOf("media_1.png"), canvas.liveImageFileNames())
    }
}

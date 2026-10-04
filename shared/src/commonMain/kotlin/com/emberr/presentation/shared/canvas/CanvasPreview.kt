package com.emberr.presentation.shared.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import com.emberr.data.local.room.entity.CanvasStrokeTool
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.canvas.CanvasRepository
import com.emberr.domain.canvas.isFreeText
import com.emberr.domain.canvas.isGroup
import com.emberr.domain.canvas.isImage
import com.emberr.ui.theme.LocalAppIsDark
import com.emberr.ui.theme.highlightBackgroundFor
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

@Composable
fun CanvasPreview(canvasNoteId: String, lastUpdatedAt: Long, modifier: Modifier = Modifier) {
    val canvasRepository = koinInject<CanvasRepository>()
    val canvas by produceState(CanvasContent(), canvasNoteId, lastUpdatedAt) {
        value = try {
            canvasRepository.loadCanvasIncludingDeleted(canvasNoteId).liveOnly()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            e.printStackTrace()
            CanvasContent()
        }
    }
    val strokeCache = remember(canvasNoteId) { CanvasStrokeCache() }
    val pixelDensity = LocalDensity.current.density
    val isDarkTheme = LocalAppIsDark.current
    val edgeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val accentColor = MaterialTheme.colorScheme.primary
    val strokeInkColor = MaterialTheme.colorScheme.onSurface
    val highlighterAlpha = if (isDarkTheme) HIGHLIGHTER_ALPHA_IN_DARK_THEME else HIGHLIGHTER_ALPHA_IN_LIGHT_THEME

    BoxWithConstraints(modifier.clipToBounds().background(MaterialTheme.colorScheme.background)) {
        val previewSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val viewport = remember(canvas, previewSize, pixelDensity) {
            val boxesAndStrokes = canvas.nodes.filter { !it.isGroup }.map { it.worldRect } +
                canvas.strokes.map { strokeCache.boundsOnBoard(it) }
            val contentRects = boxesAndStrokes.ifEmpty { canvas.nodes.map { it.groupTitleWorldRect.expandedToInclude(it.worldRect) } }
            canvasPreviewViewport(contentRects, previewSize, pixelDensity)
        } ?: return@BoxWithConstraints
        val visibleBoardArea = Rect(
            topLeft = viewport.screenToWorld(Offset.Zero, pixelDensity),
            bottomRight = viewport.screenToWorld(Offset(previewSize.width, previewSize.height), pixelDensity)
        )
        val visibleNodes = canvas.nodes.filter { node ->
            val nodeArea = if (node.isGroup) node.groupTitleWorldRect.expandedToInclude(node.worldRect) else node.worldRect
            nodeArea.overlaps(visibleBoardArea)
        }

        visibleNodes.filter { it.isGroup }.sortedByDescending { it.width * it.height }.forEach { group ->
            key(group.nodeId) {
                CanvasGroupCard(
                    group = group,
                    viewport = viewport,
                    isSelected = false,
                    isEditingTitle = false,
                    cursorColor = accentColor,
                    onTitleChange = {}
                )
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val nodesById = canvas.nodes.associateBy { it.nodeId }
            canvas.edges.forEach { edge ->
                val curve = screenCurveFor(edge, nodesById, viewport, pixelDensity) ?: return@forEach
                drawCanvasEdge(
                    curve = curve,
                    color = edgeColor,
                    strokeWidth = EDGE_STROKE_WIDTH.toPx() * viewport.zoom,
                    arrowSize = ARROW_SIZE.toPx() * viewport.zoom
                )
            }
        }

        visibleNodes.filter { !it.isGroup }.forEach { node ->
            key(node.nodeId) {
                when {
                    node.isFreeText -> CanvasFreeTextCard(
                        node = node,
                        viewport = viewport,
                        isSelected = false,
                        isEditing = false,
                        cursorColor = accentColor,
                        onTextChange = {},
                        onSizeMeasured = { _, _ -> }
                    )
                    node.isImage -> CanvasImageCard(node = node, viewport = viewport, isSelected = false)
                    else -> CanvasNodeCard(
                        node = node,
                        viewport = viewport,
                        isSelected = false,
                        isEditing = false,
                        scrollState = rememberScrollState(),
                        cursorColor = accentColor,
                        onTextChange = {}
                    )
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val (highlighterStrokes, penStrokes) = canvas.strokes
                .filter { stroke -> strokeCache.boundsOnBoard(stroke).overlaps(visibleBoardArea) }
                .partition { it.tool == CanvasStrokeTool.HIGHLIGHTER }
            val pixelsPerUnit = viewport.pixelsPerUnit(pixelDensity)

            withTransform({
                translate(viewport.panOffset.x, viewport.panOffset.y)
                scale(pixelsPerUnit, pixelsPerUnit, pivot = Offset.Zero)
            }) {
                highlighterStrokes.forEach { stroke ->
                    val highlighterColor = highlightBackgroundFor(stroke.color, isDarkTheme).copy(alpha = highlighterAlpha * stroke.opacity)
                    translate(stroke.x, stroke.y) { drawPath(strokeCache.pathFor(stroke), highlighterColor) }
                }
                penStrokes.forEach { stroke ->
                    val inkColor = (CanvasInkColor.named(stroke.color)?.color ?: strokeInkColor).copy(alpha = stroke.opacity)
                    if (stroke.tool == CanvasStrokeTool.LINE) {
                        strokeCache.lineEndsOf(stroke)?.let { (lineStart, lineEnd) ->
                            drawCanvasLine(lineStart, lineEnd, stroke.width, inkColor, stroke.linePattern, stroke.hasArrowHead)
                        }
                    } else {
                        translate(stroke.x, stroke.y) { drawPath(strokeCache.pathFor(stroke), inkColor) }
                    }
                }
            }
        }
    }
}

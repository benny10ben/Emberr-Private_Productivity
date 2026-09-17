package com.emberr.presentation.mobile.home.note

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.NoteBlock
import com.emberr.presentation.shared.SubNoteOpenMode
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import dev.chrisbanes.haze.HazeState
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.maximize_2
import emberr.shared.generated.resources.minimize_2
import org.jetbrains.compose.resources.painterResource

private fun Painter.scaledDown(factor: Float): Painter = object : Painter() {
    override val intrinsicSize: Size = this@scaledDown.intrinsicSize
    override fun DrawScope.onDraw() {
        scale(factor) {
            with(this@scaledDown) { draw(size) }
        }
    }
}

/**
 * Notion-style slide-in panel for database row notes (desktop only).
 *
 * MUST be placed directly inside the right-panel Box (after clipping).
 * fillMaxSize() then refers only to the right panel — the left sidebar
 * is never touched, even when isExpanded = true (widthFraction = 1f).
 *
 * The expand icon sits immediately to the right of the note header bar's back arrow,
 * styled identically (44dp container, 22dp icon, same bg/tint).
 */
@Composable
fun SubNotePanel(
    noteId: String,
    onClose: () -> Unit,
    onExpand: (String) -> Unit,
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onExportMarkdown: (fileName: String, content: String) -> Unit = { _, _ -> },
    onExportPdf: (fileName: String, title: String, blocks: List<NoteBlock>) -> Unit = { _, _, _ -> },
    openMode: SubNoteOpenMode = SubNoteOpenMode.SIDE_PANEL,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(noteId) { visible = true }

    var isExpanded by remember(noteId) { mutableStateOf(openMode == SubNoteOpenMode.FULL_RIGHT_PANEL) }

    val isDialogMode = openMode == SubNoteOpenMode.CENTER_DIALOG

    val animationDurationMillis = 200

    // Side panel: slides in by growing its width fraction from the right edge.
    val widthFraction by animateFloatAsState(
        targetValue = when {
            isDialogMode -> if (isExpanded) 1f else 0.8f
            !visible     -> 0f
            isExpanded   -> 1f
            else         -> 0.5f
        },
        animationSpec = tween(durationMillis = animationDurationMillis),
        label = "panelWidth",
        finishedListener = { finishedValue -> if (!isDialogMode && finishedValue == 0f) onClose() }
    )

    val heightFraction by animateFloatAsState(
        targetValue = if (isDialogMode) { if (isExpanded) 1f else 0.92f } else 1f,
        animationSpec = tween(durationMillis = animationDurationMillis),
        label = "panelHeight"
    )

    // Center dialog: stays at its target size and instead scales + fades in/out,
    // so the note content underneath never has to reflow mid-animation.
    val dialogScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = tween(durationMillis = animationDurationMillis, easing = FastOutSlowInEasing),
        label = "dialogScale"
    )
    val dialogAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = animationDurationMillis),
        label = "dialogAlpha",
        finishedListener = { finishedValue -> if (isDialogMode && finishedValue == 0f) onClose() }
    )

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible && !isExpanded) 0.3f else 0f,
        animationSpec = tween(durationMillis = animationDurationMillis),
        label = "scrimAlpha"
    )

    val dismiss: () -> Unit = {
        visible = false
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Scrim — tap to close.
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight(heightFraction)
                .align(if (isDialogMode) Alignment.Center else Alignment.CenterEnd)
                .then(
                    if (isDialogMode) {
                        Modifier.graphicsLayer {
                            scaleX = dialogScale
                            scaleY = dialogScale
                            alpha = dialogAlpha
                        }
                    } else {
                        Modifier
                    }
                )
                .shadow(elevation = 20.dp, shape = if (isExpanded) RectangleShape else RoundedCornerShape(18.dp))
                .clip(if (isExpanded) RectangleShape else RoundedCornerShape(18.dp))
        ) {
            var showInnerPanel by remember { mutableStateOf(false) }
            var innerPanelNoteId by remember { mutableStateOf<String?>(null) }
            val hazeState = remember { HazeState() }

            val topBarBgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
            val topBarContentColor = MaterialTheme.colorScheme.onSurface

            val panelColorScheme = MaterialTheme.colorScheme.copy(
                background = if (isExpanded) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface
            )
            MaterialTheme(colorScheme = panelColorScheme) {
                NoteScreen(
                    noteId = noteId,
                    onNavigateBack = dismiss,
                    onPickImage = onPickImage,
                    onTakePhoto = onTakePhoto,
                    onPickDocument = onPickDocument,
                    onOpenFile = onOpenFile,
                    onExportMarkdown = onExportMarkdown,
                    onExportPdf = onExportPdf,
                    externalHazeState = hazeState,
                    topBarBgColor = topBarBgColor,
                    topBarContentColor = topBarContentColor,
                    onNavigateToEditor = { nestedId ->
                        innerPanelNoteId = nestedId
                        showInnerPanel = true
                    }
                )

                if (showInnerPanel && innerPanelNoteId != null) {
                    SubNotePanel(
                        noteId = innerPanelNoteId!!,
                        onClose = { showInnerPanel = false; innerPanelNoteId = null },
                        onExpand = onExpand,
                        onPickImage = onPickImage,
                        onTakePhoto = onTakePhoto,
                        onPickDocument = onPickDocument,
                        onOpenFile = onOpenFile,
                        onExportMarkdown = onExportMarkdown,
                        onExportPdf = onExportPdf,
                        openMode = openMode,
                    )
                }
            }

            EmberrTopHeaderBar(
                modifier = Modifier.align(Alignment.TopStart),
                backIcon = (if (isExpanded) painterResource(Res.drawable.minimize_2) else painterResource(Res.drawable.maximize_2)).scaledDown(0.8f),
                backContentDescription = if (isExpanded) "Collapse panel" else "Expand panel",
                backButtonBackground = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                backButtonTint = MaterialTheme.colorScheme.onSurface,
                hazeState = hazeState,
                applyStatusBarPadding = false,
                contentPadding = PaddingValues(top = 16.dp, start = 74.dp),
                onBackClick = { isExpanded = !isExpanded }
            )
        }
    }
}
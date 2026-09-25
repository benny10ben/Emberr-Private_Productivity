package com.emberr.presentation.shared.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.emberr.presentation.home.note.DesktopMenuItem
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrShadowElevation
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.TopBarIconButtonGroup
import com.emberr.presentation.shared.components.TopBarIconButtonItem
import com.emberr.presentation.shared.components.customEmberrShadow
import com.emberr.ui.theme.HighlightColor
import com.emberr.ui.theme.LocalAppIsDark
import dev.chrisbanes.haze.HazeState
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.ellipsis
import emberr.shared.generated.resources.minus
import emberr.shared.generated.resources.palette
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.redo_circle
import emberr.shared.generated.resources.scan_line
import emberr.shared.generated.resources.square_arrow_out_up_right
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.undo_circle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

val CanvasSelectionColor = Color(0xFF4F5B8A)

private val SelectionPillShape = RoundedCornerShape(50)
private val SelectionPillGap = 8.dp
private const val PALETTE_SWATCHES_PER_ROW = 5

@Composable
fun canvasNodeBackgroundFor(colorName: String?): Color {
    val isDarkTheme = LocalAppIsDark.current
    return HighlightColor.entries.firstOrNull { it.storageName == colorName }?.backgroundFor(isDarkTheme)
        ?: MaterialTheme.colorScheme.surface
}

@Composable
fun CanvasOptionsButton(
    hazeState: HazeState,
    showStickyNoteOption: Boolean,
    loadCurrentTitle: suspend () -> String,
    onRename: (String) -> Unit,
    onOpenAsStickyNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showRenamePopup by remember { mutableStateOf(false) }

    Box(modifier) {
        TopBarIconButton(
            icon = painterResource(Res.drawable.ellipsis),
            contentDescription = "Options",
            bgColor = Color.Transparent,
            tint = MaterialTheme.colorScheme.primary,
            hazeState = hazeState,
            hazeStyle = EmberrBlur.Regular,
            onClick = { showOptionsMenu = true }
        )
        EmberrDesktopMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
            Column(Modifier.width(240.dp).padding(vertical = 4.dp)) {
                DesktopMenuItem(painterResource(Res.drawable.pen), "Rename") {
                    showOptionsMenu = false
                    showRenamePopup = true
                }
                if (showStickyNoteOption) {
                    DesktopMenuItem(painterResource(Res.drawable.square_arrow_out_up_right), "Open as Sticky Note") {
                        showOptionsMenu = false
                        onOpenAsStickyNote()
                    }
                }
            }
        }
        EmberrDesktopMenu(
            expanded = showRenamePopup,
            onDismissRequest = { showRenamePopup = false },
            modifier = Modifier.width(280.dp)
        ) {
            CanvasRenamePopup(
                loadCurrentTitle = loadCurrentTitle,
                onConfirm = { newTitle ->
                    onRename(newTitle)
                    showRenamePopup = false
                },
                onDismiss = { showRenamePopup = false }
            )
        }
    }
}

@Composable
fun CanvasZoomButtons(
    hazeState: HazeState,
    onZoomIn: () -> Unit,
    onShowBusiestArea: () -> Unit,
    onZoomOut: () -> Unit
) {
    TopBarIconButtonGroup(
        items = listOf(
            TopBarIconButtonItem(painterResource(Res.drawable.plus), "Zoom in", onZoomIn),
            TopBarIconButtonItem(painterResource(Res.drawable.scan_line), "Go to boxes", onShowBusiestArea),
            TopBarIconButtonItem(painterResource(Res.drawable.minus), "Zoom out", onZoomOut)
        ),
        bgColor = Color.Transparent,
        tint = MaterialTheme.colorScheme.primary,
        hazeState = hazeState,
        hazeStyle = EmberrBlur.Regular,
        isVertical = true
    )
}

@Composable
fun CanvasUndoRedoButtons(hazeState: HazeState, onUndo: () -> Unit, onRedo: () -> Unit) {
    TopBarIconButtonGroup(
        items = listOf(
            TopBarIconButtonItem(painterResource(Res.drawable.undo_circle), "Undo", onUndo),
            TopBarIconButtonItem(painterResource(Res.drawable.redo_circle), "Redo", onRedo)
        ),
        bgColor = Color.Transparent,
        tint = MaterialTheme.colorScheme.primary,
        hazeState = hazeState,
        hazeStyle = EmberrBlur.Regular,
        isVertical = true
    )
}

@Composable
private fun CanvasRenamePopup(
    loadCurrentTitle: suspend () -> String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { titleInput = loadCurrentTitle() }
    val submit = { if (titleInput.isNotBlank()) onConfirm(titleInput.trim()) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "Rename Canvas",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 18.dp)
        )
        EmberrTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            placeholder = "Canvas title...",
            modifier = Modifier.fillMaxWidth(),
            onSubmit = submit
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmberrButtonSecondary(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            EmberrButtonPrimary(text = "Save", onClick = submit, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun CanvasSelectionPill(
    boxTopCenterOnScreen: Offset,
    currentColorName: String?,
    onDelete: () -> Unit,
    onColorSelected: (String?) -> Unit
) {
    var showPalette by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                val gap = SelectionPillGap.roundToPx()
                layout(placeable.width, placeable.height) {
                    placeable.place(
                        IntOffset(
                            x = (boxTopCenterOnScreen.x - placeable.width / 2f).roundToInt(),
                            y = (boxTopCenterOnScreen.y - placeable.height - gap).roundToInt()
                        )
                    )
                }
            }
            .customEmberrShadow(SelectionPillShape, EmberrShadowElevation.Standard)
            .clip(SelectionPillShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionPillButton(painterResource(Res.drawable.trash), "Delete box", onDelete)
        Box {
            SelectionPillButton(painterResource(Res.drawable.palette), "Box color") { showPalette = true }
            EmberrDesktopMenu(expanded = showPalette, onDismissRequest = { showPalette = false }) {
                CanvasColorPalette(
                    currentColorName = currentColorName,
                    onColorSelected = { colorName ->
                        onColorSelected(colorName)
                        showPalette = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectionPillButton(icon: Painter, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CanvasColorPalette(currentColorName: String?, onColorSelected: (String?) -> Unit) {
    val colorNames = listOf<String?>(null) + HighlightColor.entries.map { it.storageName }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colorNames.chunked(PALETTE_SWATCHES_PER_ROW).forEach { rowOfColorNames ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOfColorNames.forEach { colorName ->
                    val isCurrent = colorName == currentColorName
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(canvasNodeBackgroundFor(colorName))
                            .border(
                                width = if (isCurrent) 2.dp else 0.5.dp,
                                color = if (isCurrent) CanvasSelectionColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(colorName) }
                    )
                }
            }
        }
    }
}

data class CanvasMenuOption(
    val label: String,
    val icon: DrawableResource,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun CanvasContextMenu(
    anchorOnScreen: Offset,
    options: List<CanvasMenuOption>,
    onDismiss: () -> Unit
) {
    Box(Modifier.offset { IntOffset(anchorOnScreen.x.roundToInt(), anchorOnScreen.y.roundToInt()) }) {
        EmberrDesktopMenu(expanded = options.isNotEmpty(), onDismissRequest = onDismiss) {
            Column(Modifier.width(200.dp).padding(vertical = 4.dp)) {
                options.forEach { option ->
                    DesktopMenuItem(painterResource(option.icon), option.label, isDestructive = option.isDestructive) {
                        option.onClick()
                        onDismiss()
                    }
                }
            }
        }
    }
}

package com.emberr.presentation.shared.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.CanvasNodeShape
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.home.RenameBottomSheet
import com.emberr.presentation.home.note.BottomSheetOptionItem
import com.emberr.presentation.home.note.DesktopMenuItem
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.NoRippleIndicationNodeFactory
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrShadowElevation
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.TopBarIconButtonGroup
import com.emberr.presentation.shared.components.TopBarIconButtonItem
import com.emberr.presentation.shared.components.customEmberrShadow
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.ui.theme.HighlightColor
import com.emberr.ui.theme.LocalAppIsDark
import dev.chrisbanes.haze.HazeState
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_left
import emberr.shared.generated.resources.dot_grid
import emberr.shared.generated.resources.dot_grid_off
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
import emberr.shared.generated.resources.x
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

val CanvasSelectionColor = Color(0xFF4F5B8A)

private val SelectionPillShape = RoundedCornerShape(50)
private val SelectionPillGap = 8.dp
private const val PALETTE_SWATCHES_PER_ROW = 5
private val SelectionBarShape = RoundedCornerShape(12.dp)
private val TitlePillShape = RoundedCornerShape(50)
private val ShapePickerButtonSize = 44.dp
private val ShapePickerOpenCornerRadius = 16.dp
private const val SHAPE_PICKER_COLUMNS = 4
private val ShapeOptionSize = 40.dp
private val ShapeOptionShape = RoundedCornerShape(10.dp)
private val ShapeIconSize = 22.dp
private const val MAX_SHAPE_ICON_RATIO = 1.6f

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
    showMoveToTrashOption: Boolean,
    loadCurrentTitle: suspend () -> String,
    onRename: (String) -> Unit,
    onOpenAsStickyNote: () -> Unit,
    onMoveToTrash: () -> Unit,
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
        if (!isDesktopPlatform) {
            CanvasMobileOptionsSheets(
                showOptionsSheet = showOptionsMenu,
                showRenameSheet = showRenamePopup,
                showMoveToTrashOption = showMoveToTrashOption,
                loadCurrentTitle = loadCurrentTitle,
                onOpenRename = {
                    showOptionsMenu = false
                    showRenamePopup = true
                },
                onDismissOptions = { showOptionsMenu = false },
                onDismissRename = { showRenamePopup = false },
                onRename = onRename,
                onMoveToTrash = onMoveToTrash
            )
        } else {
            CanvasDesktopOptionsMenus(
                showOptionsMenu = showOptionsMenu,
                showRenamePopup = showRenamePopup,
                showStickyNoteOption = showStickyNoteOption,
                showMoveToTrashOption = showMoveToTrashOption,
                loadCurrentTitle = loadCurrentTitle,
                onOpenRename = {
                    showOptionsMenu = false
                    showRenamePopup = true
                },
                onDismissOptions = { showOptionsMenu = false },
                onDismissRename = { showRenamePopup = false },
                onRename = onRename,
                onOpenAsStickyNote = onOpenAsStickyNote,
                onMoveToTrash = onMoveToTrash
            )
        }
    }
}

@Composable
private fun CanvasDesktopOptionsMenus(
    showOptionsMenu: Boolean,
    showRenamePopup: Boolean,
    showStickyNoteOption: Boolean,
    showMoveToTrashOption: Boolean,
    loadCurrentTitle: suspend () -> String,
    onOpenRename: () -> Unit,
    onDismissOptions: () -> Unit,
    onDismissRename: () -> Unit,
    onRename: (String) -> Unit,
    onOpenAsStickyNote: () -> Unit,
    onMoveToTrash: () -> Unit
) {
    EmberrDesktopMenu(expanded = showOptionsMenu, onDismissRequest = onDismissOptions) {
        Column(Modifier.width(240.dp).padding(vertical = 4.dp)) {
            DesktopMenuItem(painterResource(Res.drawable.pen), "Rename") { onOpenRename() }
            if (showStickyNoteOption) {
                DesktopMenuItem(painterResource(Res.drawable.square_arrow_out_up_right), "Open as Sticky Note") {
                    onDismissOptions()
                    onOpenAsStickyNote()
                }
            }
            if (showMoveToTrashOption) {
                DesktopMenuItem(painterResource(Res.drawable.trash), "Move to Trash", isDestructive = true) {
                    onDismissOptions()
                    onMoveToTrash()
                }
            }
        }
    }
    EmberrDesktopMenu(
        expanded = showRenamePopup,
        onDismissRequest = onDismissRename,
        modifier = Modifier.width(280.dp)
    ) {
        CanvasRenamePopup(
            loadCurrentTitle = loadCurrentTitle,
            onConfirm = { newTitle ->
                onRename(newTitle)
                onDismissRename()
            },
            onDismiss = onDismissRename
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasMobileOptionsSheets(
    showOptionsSheet: Boolean,
    showRenameSheet: Boolean,
    showMoveToTrashOption: Boolean,
    loadCurrentTitle: suspend () -> String,
    onOpenRename: () -> Unit,
    onDismissOptions: () -> Unit,
    onDismissRename: () -> Unit,
    onRename: (String) -> Unit,
    onMoveToTrash: () -> Unit
) {
    var currentTitle by remember { mutableStateOf("") }
    LaunchedEffect(showRenameSheet) {
        if (showRenameSheet) currentTitle = loadCurrentTitle()
    }

    EmberrBottomSheet(expanded = showOptionsSheet, onDismiss = onDismissOptions, title = "Canvas") { closeAnd ->
        CompositionLocalProvider(
            LocalIndication provides NoRippleIndicationNodeFactory,
            LocalRippleConfiguration provides null
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                BottomSheetOptionItem(painterResource(Res.drawable.pen), "Rename") { closeAnd(onOpenRename) }
                if (showMoveToTrashOption) {
                    BottomSheetOptionItem(painterResource(Res.drawable.trash), "Move to Trash", isDestructive = true) {
                        closeAnd(onMoveToTrash)
                    }
                }
            }
        }
    }
    RenameBottomSheet(
        expanded = showRenameSheet,
        currentName = currentTitle,
        onDismiss = onDismissRename,
        onRename = { newTitle ->
            onRename(newTitle)
            onDismissRename()
        },
        title = "Rename Canvas",
        placeholder = "Canvas title..."
    )
}

@Composable
fun CanvasTitlePill(title: String, hazeState: HazeState, modifier: Modifier = Modifier) {
    Surface(
        shape = TitlePillShape,
        color = Color.Transparent,
        modifier = modifier
            .height(44.dp)
            .customEmberrShadow(TitlePillShape)
            .clip(TitlePillShape)
            .emberrBlur(hazeState, EmberrBlur.Regular)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = TitlePillShape
            )
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = title.ifBlank { "Untitled canvas" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CanvasBackButton(hazeState: HazeState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        TopBarIconButton(
            icon = painterResource(Res.drawable.arrow_left),
            contentDescription = "Back",
            bgColor = Color.Transparent,
            tint = MaterialTheme.colorScheme.primary,
            hazeState = hazeState,
            hazeStyle = EmberrBlur.Regular,
            onClick = onClick
        )
    }
}

@Composable
fun CanvasAddShapeButton(
    isOpen: Boolean,
    hazeState: HazeState,
    onToggle: () -> Unit,
    onShapeSelected: (CanvasNodeShape) -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = MaterialTheme.colorScheme.primary
    val iconRotation by animateFloatAsState(if (isOpen) 45f else 0f)
    val cornerRadius by animateDpAsState(if (isOpen) ShapePickerOpenCornerRadius else ShapePickerButtonSize / 2)
    val pickerShape = RoundedCornerShape(cornerRadius)

    Surface(
        shape = pickerShape,
        color = Color.Transparent,
        modifier = modifier
            .customEmberrShadow(pickerShape)
            .clip(pickerShape)
            .emberrBlur(hazeState, EmberrBlur.Regular)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = pickerShape
            )
    ) {
        Column(horizontalAlignment = Alignment.End) {
            AnimatedVisibility(
                visible = isOpen,
                enter = expandIn(expandFrom = Alignment.BottomEnd) + fadeIn(),
                exit = shrinkOut(shrinkTowards = Alignment.BottomEnd) + fadeOut()
            ) {
                CanvasShapeGrid(tint = tint, onShapeSelected = onShapeSelected)
            }
            Box(
                modifier = Modifier
                    .size(ShapePickerButtonSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = NoRippleIndicationNodeFactory,
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.plus),
                    contentDescription = if (isOpen) "Close shapes" else "Add shape",
                    tint = tint,
                    modifier = Modifier.size(22.dp).rotate(iconRotation)
                )
            }
        }
    }
}

@Composable
private fun CanvasShapeGrid(tint: Color, onShapeSelected: (CanvasNodeShape) -> Unit) {
    Column(
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CanvasNodeShape.entries.chunked(SHAPE_PICKER_COLUMNS).forEach { rowOfShapes ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowOfShapes.forEach { shape ->
                    CanvasShapeOption(shape = shape, tint = tint, onClick = { onShapeSelected(shape) })
                }
            }
        }
    }
}

@Composable
private fun CanvasShapeOption(shape: CanvasNodeShape, tint: Color, onClick: () -> Unit) {
    val label = shape.label
    val shapeRatio = shape.defaultWorldSize.width / shape.defaultWorldSize.height
    val iconWidth = if (shapeRatio >= 1f) ShapeIconSize else ShapeIconSize * shapeRatio
    val iconHeight = if (shapeRatio >= 1f) ShapeIconSize / shapeRatio.coerceAtMost(MAX_SHAPE_ICON_RATIO) else ShapeIconSize

    Box(
        modifier = Modifier
            .size(ShapeOptionSize)
            .clip(ShapeOptionShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(iconWidth, iconHeight)) {
            val lineStyle = Stroke(width = 1.5.dp.toPx())
            val cornerRadius = 3.dp.toPx()
            drawPath(shape.outlinePath(size, cornerRadius), tint, style = lineStyle)
            shape.detailLinePath(size, doubleLineGap = 3.dp.toPx(), cornerRadius = cornerRadius)?.let { detailLine ->
                drawPath(detailLine, tint, style = lineStyle)
            }
        }
    }
}

private val CanvasNodeShape.label: String
    get() = when (this) {
        CanvasNodeShape.RECTANGLE -> "Rectangle"
        CanvasNodeShape.SQUARE -> "Square"
        CanvasNodeShape.CIRCLE -> "Circle"
        CanvasNodeShape.OVAL -> "Oval"
        CanvasNodeShape.TRIANGLE -> "Triangle"
        CanvasNodeShape.DIAMOND -> "Diamond"
        CanvasNodeShape.PENTAGON -> "Pentagon"
        CanvasNodeShape.HEXAGON -> "Hexagon"
        CanvasNodeShape.PARALLELOGRAM -> "Parallelogram"
        CanvasNodeShape.TRAPEZOID -> "Trapezoid"
        CanvasNodeShape.PILL -> "Pill"
        CanvasNodeShape.DATABASE -> "Database"
        CanvasNodeShape.DOUBLE_RECTANGLE -> "Double rectangle"
        CanvasNodeShape.DOUBLE_SQUARE -> "Double square"
        CanvasNodeShape.DOUBLE_CIRCLE -> "Double circle"
        CanvasNodeShape.DOUBLE_TRIANGLE -> "Double triangle"
    }

@Composable
fun CanvasSelectionActionBar(
    isVisible: Boolean,
    selectedCount: Int,
    options: List<CanvasMenuOption>,
    onClose: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val tint = MaterialTheme.colorScheme.primary

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.padding(horizontal = 24.dp)
    ) {
        Surface(
            shape = SelectionBarShape,
            color = Color.Transparent,
            modifier = Modifier
                .padding(bottom = 32.dp)
                .customEmberrShadow(SelectionBarShape)
                .clip(SelectionBarShape)
                .emberrBlur(hazeState, EmberrBlur.Regular)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = SelectionBarShape
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SelectionBarIcon(painterResource(Res.drawable.x), "Clear", tint, onClose)
                Text(
                    text = "$selectedCount",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tint
                )
                Box(Modifier.width(1.dp).height(18.dp).background(tint.copy(alpha = 0.2f)))
                options.forEach { option ->
                    SelectionBarIcon(painterResource(option.icon), option.label, tint, option.onClick)
                }
            }
        }
    }
}

@Composable
private fun SelectionBarIcon(icon: Painter, contentDescription: String, tint: Color, onClick: () -> Unit) {
    Icon(
        painter = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(18.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    )
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
fun CanvasUndoRedoButtons(hazeState: HazeState, onUndo: () -> Unit, onRedo: () -> Unit, isVertical: Boolean = true) {
    TopBarIconButtonGroup(
        items = listOf(
            TopBarIconButtonItem(painterResource(Res.drawable.undo_circle), "Undo", onUndo),
            TopBarIconButtonItem(painterResource(Res.drawable.redo_circle), "Redo", onRedo)
        ),
        bgColor = Color.Transparent,
        tint = MaterialTheme.colorScheme.primary,
        hazeState = hazeState,
        hazeStyle = EmberrBlur.Regular,
        isVertical = isVertical
    )
}

@Composable
fun CanvasDotGridButton(hazeState: HazeState, isDotGridVisible: Boolean, onToggle: () -> Unit) {
    TopBarIconButton(
        icon = painterResource(if (isDotGridVisible) Res.drawable.dot_grid else Res.drawable.dot_grid_off),
        contentDescription = if (isDotGridVisible) "Hide dots" else "Show dots",
        bgColor = Color.Transparent,
        tint = MaterialTheme.colorScheme.primary,
        hazeState = hazeState,
        hazeStyle = EmberrBlur.Regular,
        onClick = onToggle
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

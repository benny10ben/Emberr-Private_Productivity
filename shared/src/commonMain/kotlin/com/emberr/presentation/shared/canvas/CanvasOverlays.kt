package com.emberr.presentation.shared.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberr.data.local.room.entity.CanvasNodeShape
import com.emberr.data.local.room.entity.CanvasStrokeTool
import com.emberr.domain.canvas.CanvasLineStyle
import com.emberr.domain.canvas.CanvasStrokeStyle
import com.emberr.domain.canvas.CanvasTextStyle
import com.emberr.domain.canvas.CanvasStrokePoint
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
import emberr.shared.generated.resources.fuzzybubbles_bold
import emberr.shared.generated.resources.fuzzybubbles_regular
import emberr.shared.generated.resources.eraser
import emberr.shared.generated.resources.highlight
import emberr.shared.generated.resources.line_tool
import emberr.shared.generated.resources.minus
import emberr.shared.generated.resources.palette
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.redo_circle
import emberr.shared.generated.resources.scan_line
import emberr.shared.generated.resources.shapes
import emberr.shared.generated.resources.square_arrow_out_up_right
import emberr.shared.generated.resources.text_highlight
import emberr.shared.generated.resources.text_input_focus
import emberr.shared.generated.resources.text_type
import emberr.shared.generated.resources.textalign_center2
import emberr.shared.generated.resources.textalign_left2
import emberr.shared.generated.resources.textalign_right2
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.undo_circle
import emberr.shared.generated.resources.x
import emberr.shared.generated.resources.yuyushort_regular
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

val CanvasSelectionColor = Color(0xFF4F5B8A)

private val SelectionPillShape = RoundedCornerShape(50)
private val SelectionPillGap = 8.dp
private const val PALETTE_SWATCHES_PER_ROW = 5
private val SelectionBarShape = RoundedCornerShape(12.dp)
private val TitlePillShape = RoundedCornerShape(50)
private const val SHAPE_PICKER_COLUMNS = 4
private val ShapeOptionSize = 40.dp
private val ShapeOptionShape = RoundedCornerShape(10.dp)
private val ShapeIconSize = 22.dp
private const val MAX_SHAPE_ICON_RATIO = 1.6f
private val ToolPanelShape = RoundedCornerShape(16.dp)
private val ToolPanelWidth = 212.dp
private val ToolPanelSwatchShape = RoundedCornerShape(6.dp)
private val ToolPanelSwatchRingShape = RoundedCornerShape(8.dp)
private val ToolPanelOptionShape = RoundedCornerShape(10.dp)
private val WidthOptionLabels = listOf("Thin", "Medium", "Thick")
private val WidthPreviewThicknesses = listOf(1.5.dp, 3.dp, 5.dp)
private val HighlighterColorsAfterYellow = listOf(HighlightColor.GREEN, HighlightColor.BLUE, HighlightColor.PINK, HighlightColor.ORANGE)
val ERASER_RADIUS_OPTIONS = listOf(6f, 10f, 18f)
private val EraserSizeLabels = listOf("Small", "Medium", "Large")
private val EraserPreviewRadii = listOf(3.dp, 5.dp, 8.dp)
private const val PRESSURE_PREVIEW_POINTS = 24
private val TextWeightOptions = listOf(FontWeight.Normal.weight to "Regular", FontWeight.Medium.weight to "Medium", FontWeight.Bold.weight to "Bold")
private val TextSizeOptions = listOf(16f to "Small", 24f to "Medium", 32f to "Large")
private val TextSizePreviewSizes = listOf(11.sp, 14.sp, 17.sp)
private val MobilePillHorizontalPadding = 8.dp
private val MobilePillItemSpacing = 4.dp
private val MobilePillSelectedBackgroundWidth = 36.dp + MobilePillHorizontalPadding * 2
private val MobileOpacitySliderWidth = 150.dp
private val TextBackgroundColors = listOf(HighlightColor.YELLOW, HighlightColor.GREEN, HighlightColor.BLUE, HighlightColor.PINK)

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
private fun CanvasShapeGrid(tint: Color, onShapeSelected: (CanvasNodeShape) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Box(
        modifier = Modifier
            .size(ShapeOptionSize)
            .clip(ShapeOptionShape)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        ShapeIcon(shape = shape, tint = tint)
    }
}

@Composable
private fun ShapeIcon(shape: CanvasNodeShape, tint: Color) {
    val shapeRatio = shape.defaultWorldSize.width / shape.defaultWorldSize.height
    val iconWidth = if (shapeRatio >= 1f) ShapeIconSize else ShapeIconSize * shapeRatio
    val iconHeight = if (shapeRatio >= 1f) ShapeIconSize / shapeRatio.coerceAtMost(MAX_SHAPE_ICON_RATIO) else ShapeIconSize
    Canvas(Modifier.size(iconWidth, iconHeight)) {
        val lineStyle = Stroke(width = 1.5.dp.toPx())
        val cornerRadius = 3.dp.toPx()
        drawPath(shape.outlinePath(size, cornerRadius), tint, style = lineStyle)
        shape.detailLinePath(size, doubleLineGap = 3.dp.toPx(), cornerRadius = cornerRadius)?.let { detailLine ->
            drawPath(detailLine, tint, style = lineStyle)
        }
    }
}

@Composable
fun CanvasShapePanel(hazeState: HazeState, onShapeSelected: (CanvasNodeShape) -> Unit, modifier: Modifier = Modifier) {
    CanvasToolPanelSurface(hazeState = hazeState, modifier = modifier) {
        ToolPanelSection("Shapes") {
            CanvasShapeGrid(tint = MaterialTheme.colorScheme.primary, onShapeSelected = onShapeSelected)
        }
    }
}

@Composable
fun CanvasMobileShapeSettings(hazeState: HazeState, onShapeSelected: (CanvasNodeShape) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        CanvasMobilePill(hazeState, isScrollable = true) {
            CanvasNodeShape.entries.forEach { shape ->
                MobilePillItem(label = shape.label, isSelected = false, onClick = { onShapeSelected(shape) }) { color ->
                    ShapeIcon(shape = shape, tint = color)
                }
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

enum class CanvasTool { PEN, HIGHLIGHTER, ERASER, TEXT, SHAPES, LINE }

private val CanvasTool.icon: DrawableResource
    get() = when (this) {
        CanvasTool.PEN -> Res.drawable.pen
        CanvasTool.HIGHLIGHTER -> Res.drawable.text_highlight
        CanvasTool.ERASER -> Res.drawable.eraser
        CanvasTool.TEXT -> Res.drawable.text_input_focus
        CanvasTool.SHAPES -> Res.drawable.shapes
        CanvasTool.LINE -> Res.drawable.line_tool
    }

private val CanvasTool.iconSize: Dp
    get() = when (this) {
        CanvasTool.PEN -> 20.dp
        CanvasTool.HIGHLIGHTER -> 22.dp
        CanvasTool.ERASER -> 22.dp
        CanvasTool.TEXT -> 24.dp
        CanvasTool.SHAPES -> 22.dp
        CanvasTool.LINE -> 22.dp
    }

private val CanvasTool.label: String
    get() = when (this) {
        CanvasTool.PEN -> "Pen"
        CanvasTool.HIGHLIGHTER -> "Highlighter"
        CanvasTool.ERASER -> "Eraser"
        CanvasTool.TEXT -> "Text"
        CanvasTool.SHAPES -> "Shapes"
        CanvasTool.LINE -> "Line"
    }

@Composable
fun CanvasToolbar(hazeState: HazeState, activeTool: CanvasTool?, onToolClick: (CanvasTool) -> Unit) {
    TopBarIconButtonGroup(
        items = CanvasTool.entries.map { tool ->
            TopBarIconButtonItem(
                icon = painterResource(tool.icon),
                contentDescription = tool.label,
                onClick = { onToolClick(tool) },
                isSelected = tool == activeTool,
                iconSize = tool.iconSize
            )
        },
        bgColor = Color.Transparent,
        tint = MaterialTheme.colorScheme.primary,
        hazeState = hazeState,
        hazeStyle = EmberrBlur.Regular,
        horizontalPadding = 8.dp,
        horizontalItemSpacing = 4.dp
    )
}

private data class StrokeColorChoice(val colorName: String?, val color: Color, val label: String)

@Composable
private fun strokeColorChoicesFor(tool: CanvasStrokeTool): List<StrokeColorChoice> {
    val isDarkTheme = LocalAppIsDark.current
    return when (tool) {
        CanvasStrokeTool.PEN, CanvasStrokeTool.LINE -> listOf(StrokeColorChoice(null, MaterialTheme.colorScheme.onSurface, "Default ink")) +
            CanvasInkColor.entries.map { inkColor ->
                StrokeColorChoice(inkColor.storageName, inkColor.color, inkColor.storageName.replaceFirstChar { it.uppercase() })
            }
        CanvasStrokeTool.HIGHLIGHTER -> {
            val yellow = HighlightColor.defaultColor
            listOf(StrokeColorChoice(null, yellow.backgroundFor(isDarkTheme), yellow.displayName)) +
                HighlighterColorsAfterYellow.map { highlightColor ->
                    StrokeColorChoice(highlightColor.storageName, highlightColor.backgroundFor(isDarkTheme), highlightColor.displayName)
                }
        }
    }
}

@Composable
fun CanvasStrokeStylePanel(
    tool: CanvasStrokeTool,
    style: CanvasStrokeStyle,
    hazeState: HazeState,
    onStyleChange: (CanvasStrokeStyle) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = MaterialTheme.colorScheme.primary

    fun change(newStyle: CanvasStrokeStyle) {
        onStyleChange(newStyle)
        onInteractionFinished()
    }

    CanvasToolPanelSurface(hazeState = hazeState, modifier = modifier) {
        ToolPanelSection("Stroke") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                strokeColorChoicesFor(tool).forEach { choice ->
                    ToolPanelColorSwatch(
                        color = choice.color,
                        label = choice.label,
                        isSelected = style.colorName == choice.colorName,
                        onClick = { change(style.copy(colorName = choice.colorName)) }
                    )
                }
            }
        }
        ToolPanelSection("Stroke width") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tool.widthOptions.forEachIndexed { index, width ->
                    ToolPanelOptionButton(
                        label = WidthOptionLabels[index],
                        isSelected = style.width == width,
                        onClick = { change(style.copy(width = width)) }
                    ) { color -> WidthPreview(thickness = WidthPreviewThicknesses[index], color = color) }
                }
            }
        }
        ToolPanelSection("Pressure") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolPanelOptionButton(
                    label = "Same thickness",
                    isSelected = !style.usesPressure,
                    onClick = { change(style.copy(usesPressure = false)) }
                ) { color -> PressurePreview(usesPressure = false, color = color) }
                ToolPanelOptionButton(
                    label = "Follow pressure",
                    isSelected = style.usesPressure,
                    onClick = { change(style.copy(usesPressure = true)) }
                ) { color -> PressurePreview(usesPressure = true, color = color) }
            }
        }
        ToolPanelSection("Opacity") {
            Slider(
                value = style.opacity,
                onValueChange = { opacity -> onStyleChange(style.copy(opacity = opacity)) },
                onValueChangeFinished = onInteractionFinished,
                colors = SliderDefaults.colors(
                    thumbColor = tint,
                    activeTrackColor = tint,
                    inactiveTrackColor = tint.copy(alpha = 0.2f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun CanvasEraserSizePanel(
    radius: Float,
    hazeState: HazeState,
    onRadiusChange: (Float) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    CanvasToolPanelSurface(hazeState = hazeState, modifier = modifier) {
        ToolPanelSection("Size") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ERASER_RADIUS_OPTIONS.forEachIndexed { index, option ->
                    ToolPanelOptionButton(
                        label = EraserSizeLabels[index],
                        isSelected = radius == option,
                        onClick = {
                            onRadiusChange(option)
                            onInteractionFinished()
                        }
                    ) { color ->
                        Canvas(Modifier.size(18.dp)) {
                            drawCircle(color = color, radius = EraserPreviewRadii[index].toPx(), style = Stroke(width = 1.5.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}

enum class CanvasTextFont(val storageName: String?, val label: String, val hasMediumWeight: Boolean) {
    FUZZY_BUBBLES("fuzzybubbles", "Fuzzy Bubbles", hasMediumWeight = false),
    YUYU_SHORT("yuyushort", "Yuyu Short", hasMediumWeight = false),
    DEFAULT(null, "Default", hasMediumWeight = true),
    SERIF("serif", "Serif", hasMediumWeight = true),
    MONOSPACE("monospace", "Monospace", hasMediumWeight = true);

    companion object {
        fun named(storageName: String?): CanvasTextFont = entries.firstOrNull { it.storageName == storageName } ?: DEFAULT
    }
}

enum class CanvasTextAlignment(val storageName: String?, val label: String, val icon: DrawableResource, val textAlign: TextAlign) {
    LEFT(null, "Align left", Res.drawable.textalign_left2, TextAlign.Start),
    CENTER("center", "Align center", Res.drawable.textalign_center2, TextAlign.Center),
    RIGHT("right", "Align right", Res.drawable.textalign_right2, TextAlign.End);

    companion object {
        fun named(storageName: String?): CanvasTextAlignment = entries.firstOrNull { it.storageName == storageName } ?: LEFT
    }
}

@Composable
fun CanvasTextFont.fontFamily(): FontFamily? = when (this) {
    CanvasTextFont.FUZZY_BUBBLES -> FontFamily(
        Font(Res.font.fuzzybubbles_regular, FontWeight.Normal),
        Font(Res.font.fuzzybubbles_bold, FontWeight.Bold)
    )
    CanvasTextFont.YUYU_SHORT -> FontFamily(Font(Res.font.yuyushort_regular, FontWeight.Normal))
    CanvasTextFont.DEFAULT -> MaterialTheme.typography.bodyLarge.fontFamily
    CanvasTextFont.SERIF -> FontFamily.Serif
    CanvasTextFont.MONOSPACE -> FontFamily.Monospace
}

private fun CanvasTextStyle.withFont(font: CanvasTextFont): CanvasTextStyle {
    val keepsWeight = font.hasMediumWeight || fontWeight != FontWeight.Medium.weight
    return copy(fontFamily = font.storageName, fontWeight = if (keepsWeight) fontWeight else FontWeight.Normal.weight)
}

private fun weightOptionsFor(style: CanvasTextStyle): List<Pair<Int, String>> {
    val font = CanvasTextFont.named(style.fontFamily)
    return TextWeightOptions.filter { (weight, _) -> font.hasMediumWeight || weight != FontWeight.Medium.weight }
}

@Composable
fun CanvasTextStylePanel(
    style: CanvasTextStyle,
    hazeState: HazeState,
    onStyleChange: (CanvasTextStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalAppIsDark.current

    CanvasToolPanelSurface(hazeState = hazeState, modifier = modifier, sectionSpacing = 12.dp) {
        ToolPanelSection("Font") {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CanvasTextFont.entries.forEach { font ->
                    ToolPanelOptionButton(
                        label = font.label,
                        isSelected = style.fontFamily == font.storageName,
                        onClick = { onStyleChange(style.withFont(font)) },
                        width = 32.dp
                    ) { color -> Text("Aa", color = color, fontFamily = font.fontFamily(), fontSize = 14.sp) }
                }
            }
        }
        ToolPanelSection("Weight") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weightOptionsFor(style).forEach { (weight, label) ->
                    ToolPanelOptionButton(
                        label = label,
                        isSelected = style.fontWeight == weight,
                        onClick = { onStyleChange(style.copy(fontWeight = weight)) }
                    ) { color -> Text("Aa", color = color, fontWeight = FontWeight(weight), fontSize = 14.sp) }
                }
            }
        }
        ToolPanelSection("Color") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolPanelColorSwatch(
                    color = MaterialTheme.colorScheme.onSurface,
                    label = "Default ink",
                    isSelected = style.textColor == null,
                    onClick = { onStyleChange(style.copy(textColor = null)) }
                )
                CanvasInkColor.entries.forEach { inkColor ->
                    ToolPanelColorSwatch(
                        color = inkColor.color,
                        label = inkColor.storageName.replaceFirstChar { it.uppercase() },
                        isSelected = style.textColor == inkColor.storageName,
                        onClick = { onStyleChange(style.copy(textColor = inkColor.storageName)) }
                    )
                }
            }
        }
        ToolPanelSection("Background") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolPanelColorSwatch(
                    color = null,
                    label = "No background",
                    isSelected = style.backgroundColor == null,
                    onClick = { onStyleChange(style.copy(backgroundColor = null)) }
                )
                TextBackgroundColors.forEach { highlightColor ->
                    ToolPanelColorSwatch(
                        color = highlightColor.backgroundFor(isDarkTheme),
                        label = highlightColor.displayName,
                        isSelected = style.backgroundColor == highlightColor.storageName,
                        onClick = { onStyleChange(style.copy(backgroundColor = highlightColor.storageName)) }
                    )
                }
            }
        }
        ToolPanelSection("Size") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextSizeOptions.forEachIndexed { index, (size, label) ->
                    ToolPanelOptionButton(
                        label = label,
                        isSelected = style.fontSize == size,
                        onClick = { onStyleChange(style.copy(fontSize = size)) }
                    ) { color -> Text("A", color = color, fontSize = TextSizePreviewSizes[index]) }
                }
            }
        }
        ToolPanelSection("Align") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CanvasTextAlignment.entries.forEach { alignment ->
                    ToolPanelOptionButton(
                        label = alignment.label,
                        isSelected = style.textAlign == alignment.storageName,
                        onClick = { onStyleChange(style.copy(textAlign = alignment.storageName)) }
                    ) { color -> Icon(painterResource(alignment.icon), contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CanvasToolPanelSurface(
    hazeState: HazeState,
    modifier: Modifier,
    sectionSpacing: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Surface(
        shape = ToolPanelShape,
        color = Color.Transparent,
        modifier = modifier
            .width(ToolPanelWidth)
            .customEmberrShadow(ToolPanelShape)
            .clip(ToolPanelShape)
            .emberrBlur(hazeState, EmberrBlur.Regular)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape = ToolPanelShape)
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            content()
        }
    }
}

@Composable
private fun ToolPanelSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        content()
    }
}

@Composable
private fun ToolPanelColorSwatch(color: Color?, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val emptySwatchSlashColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(width = 2.dp, color = if (isSelected) CanvasSelectionColor else Color.Transparent, shape = ToolPanelSwatchRingShape)
            .padding(3.dp)
            .clip(ToolPanelSwatchShape)
            .background(color ?: Color.Transparent)
            .then(
                if (color == null) Modifier.drawBehind { drawEmptySwatchSlash(emptySwatchSlashColor) } else Modifier
            )
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = ToolPanelSwatchShape)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
    )
}

@Composable
private fun ToolPanelOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    width: Dp = 44.dp,
    content: @Composable (Color) -> Unit
) {
    val tint = MaterialTheme.colorScheme.primary
    val contentColor = if (isSelected) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    Box(
        modifier = Modifier
            .size(width = width, height = 36.dp)
            .clip(ToolPanelOptionShape)
            .background(if (isSelected) tint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        content(contentColor)
    }
}

@Composable
private fun PressurePreview(usesPressure: Boolean, color: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
        val points = (0..PRESSURE_PREVIEW_POINTS).map { step ->
            val progress = step.toFloat() / PRESSURE_PREVIEW_POINTS
            CanvasStrokePoint(
                x = progress * size.width,
                y = size.height / 2 + sin(progress * 2 * PI).toFloat() * size.height * 0.3f,
                pressure = 0.15f + 0.85f * sin(progress * PI).toFloat()
            )
        }
        val outline = canvasStrokeOutline(points, width = 2.5.dp.toPx(), usesPressure = usesPressure, isComplete = true)
        drawPath(outline.toSmoothPath(), color)
    }
}

enum class CanvasStrokeSettingsCategory(val label: String) {
    STROKE("Stroke"),
    WIDTH("Stroke width"),
    PRESSURE("Pressure"),
    OPACITY("Opacity")
}

enum class CanvasTextSettingsCategory(val label: String) {
    FONT("Font"),
    WEIGHT("Weight"),
    COLOR("Color"),
    BACKGROUND("Background"),
    SIZE("Size"),
    ALIGN("Align")
}

@Composable
fun CanvasMobileStrokeSettings(
    tool: CanvasStrokeTool,
    style: CanvasStrokeStyle,
    openCategory: CanvasStrokeSettingsCategory?,
    hazeState: HazeState,
    onCategoryClick: (CanvasStrokeSettingsCategory) -> Unit,
    onStyleChange: (CanvasStrokeStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorChoices = strokeColorChoicesFor(tool)
    val currentColor = (colorChoices.firstOrNull { it.colorName == style.colorName } ?: colorChoices.first()).color
    val widthOptions = tool.widthOptions
    val selectedWidthIndex = widthOptions.indexOf(style.width).coerceAtLeast(0)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (openCategory) {
            CanvasStrokeSettingsCategory.STROKE -> CanvasMobilePill(hazeState) {
                colorChoices.forEach { choice ->
                    MobileColorOption(
                        color = choice.color,
                        label = choice.label,
                        isSelected = style.colorName == choice.colorName,
                        onClick = { onStyleChange(style.copy(colorName = choice.colorName)) }
                    )
                }
            }
            CanvasStrokeSettingsCategory.WIDTH -> CanvasMobilePill(hazeState) {
                widthOptions.forEachIndexed { index, width ->
                    MobilePillItem(
                        label = WidthOptionLabels[index],
                        isSelected = style.width == width,
                        onClick = { onStyleChange(style.copy(width = width)) }
                    ) { color -> WidthPreview(thickness = WidthPreviewThicknesses[index], color = color) }
                }
            }
            CanvasStrokeSettingsCategory.PRESSURE -> CanvasMobilePill(hazeState) {
                MobilePillItem(
                    label = "Same thickness",
                    isSelected = !style.usesPressure,
                    onClick = { onStyleChange(style.copy(usesPressure = false)) }
                ) { color -> PressurePreview(usesPressure = false, color = color) }
                MobilePillItem(
                    label = "Follow pressure",
                    isSelected = style.usesPressure,
                    onClick = { onStyleChange(style.copy(usesPressure = true)) }
                ) { color -> PressurePreview(usesPressure = true, color = color) }
            }
            CanvasStrokeSettingsCategory.OPACITY -> CanvasMobilePill(hazeState) {
                val tint = MaterialTheme.colorScheme.primary
                Slider(
                    value = style.opacity,
                    onValueChange = { opacity -> onStyleChange(style.copy(opacity = opacity)) },
                    colors = SliderDefaults.colors(
                        thumbColor = tint,
                        activeTrackColor = tint,
                        inactiveTrackColor = tint.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.width(MobileOpacitySliderWidth).padding(start = 8.dp)
                )
                Text(
                    text = "${(style.opacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(44.dp).padding(start = 8.dp)
                )
            }
            null -> Unit
        }

        CanvasMobilePill(hazeState) {
            CanvasStrokeSettingsCategory.entries.forEach { category ->
                MobilePillItem(
                    label = category.label,
                    isSelected = openCategory == category,
                    onClick = { onCategoryClick(category) }
                ) { color ->
                    when (category) {
                        CanvasStrokeSettingsCategory.STROKE -> SettingsColorDot(color = currentColor)
                        CanvasStrokeSettingsCategory.WIDTH -> WidthPreview(thickness = WidthPreviewThicknesses[selectedWidthIndex], color = color)
                        CanvasStrokeSettingsCategory.PRESSURE -> PressurePreview(usesPressure = style.usesPressure, color = color)
                        CanvasStrokeSettingsCategory.OPACITY -> Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = style.opacity))
                                .border(width = 1.5.dp, color = color, shape = CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasMobileEraserSettings(
    radius: Float,
    hazeState: HazeState,
    onRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        CanvasMobilePill(hazeState) {
            ERASER_RADIUS_OPTIONS.forEachIndexed { index, option ->
                MobilePillItem(
                    label = EraserSizeLabels[index],
                    isSelected = radius == option,
                    onClick = { onRadiusChange(option) }
                ) { color ->
                    Canvas(Modifier.size(18.dp)) {
                        drawCircle(color = color, radius = EraserPreviewRadii[index].toPx(), style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasMobileTextSettings(
    style: CanvasTextStyle,
    openCategory: CanvasTextSettingsCategory?,
    hazeState: HazeState,
    onCategoryClick: (CanvasTextSettingsCategory) -> Unit,
    onStyleChange: (CanvasTextStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalAppIsDark.current
    val currentFont = CanvasTextFont.named(style.fontFamily)
    val textColorChoices = strokeColorChoicesFor(CanvasStrokeTool.PEN)
    val currentTextColor = (textColorChoices.firstOrNull { it.colorName == style.textColor } ?: textColorChoices.first()).color
    val currentBackground = TextBackgroundColors.firstOrNull { it.storageName == style.backgroundColor }?.backgroundFor(isDarkTheme)
    val selectedSizeIndex = TextSizeOptions.indexOfFirst { (size, _) -> size == style.fontSize }.coerceAtLeast(0)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (openCategory) {
            CanvasTextSettingsCategory.FONT -> CanvasMobilePill(hazeState) {
                CanvasTextFont.entries.forEach { font ->
                    MobilePillItem(
                        label = font.label,
                        isSelected = style.fontFamily == font.storageName,
                        onClick = { onStyleChange(style.withFont(font)) }
                    ) { color -> Text("Aa", color = color, fontFamily = font.fontFamily(), fontSize = 14.sp) }
                }
            }
            CanvasTextSettingsCategory.WEIGHT -> CanvasMobilePill(hazeState) {
                weightOptionsFor(style).forEach { (weight, label) ->
                    MobilePillItem(
                        label = label,
                        isSelected = style.fontWeight == weight,
                        onClick = { onStyleChange(style.copy(fontWeight = weight)) }
                    ) { color -> Text("Aa", color = color, fontFamily = currentFont.fontFamily(), fontWeight = FontWeight(weight), fontSize = 14.sp) }
                }
            }
            CanvasTextSettingsCategory.COLOR -> CanvasMobilePill(hazeState) {
                textColorChoices.forEach { choice ->
                    MobileColorOption(
                        color = choice.color,
                        label = choice.label,
                        isSelected = style.textColor == choice.colorName,
                        onClick = { onStyleChange(style.copy(textColor = choice.colorName)) }
                    )
                }
            }
            CanvasTextSettingsCategory.BACKGROUND -> CanvasMobilePill(hazeState) {
                MobileColorOption(
                    color = null,
                    label = "No background",
                    isSelected = style.backgroundColor == null,
                    onClick = { onStyleChange(style.copy(backgroundColor = null)) }
                )
                TextBackgroundColors.forEach { highlightColor ->
                    MobileColorOption(
                        color = highlightColor.backgroundFor(isDarkTheme),
                        label = highlightColor.displayName,
                        isSelected = style.backgroundColor == highlightColor.storageName,
                        onClick = { onStyleChange(style.copy(backgroundColor = highlightColor.storageName)) }
                    )
                }
            }
            CanvasTextSettingsCategory.SIZE -> CanvasMobilePill(hazeState) {
                TextSizeOptions.forEachIndexed { index, (size, label) ->
                    MobilePillItem(
                        label = label,
                        isSelected = style.fontSize == size,
                        onClick = { onStyleChange(style.copy(fontSize = size)) }
                    ) { color -> Text("A", color = color, fontSize = TextSizePreviewSizes[index]) }
                }
            }
            CanvasTextSettingsCategory.ALIGN -> CanvasMobilePill(hazeState) {
                CanvasTextAlignment.entries.forEach { alignment ->
                    MobilePillItem(
                        label = alignment.label,
                        isSelected = style.textAlign == alignment.storageName,
                        onClick = { onStyleChange(style.copy(textAlign = alignment.storageName)) }
                    ) { color -> Icon(painterResource(alignment.icon), contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) }
                }
            }
            null -> Unit
        }

        CanvasMobilePill(hazeState) {
            CanvasTextSettingsCategory.entries.forEach { category ->
                MobilePillItem(
                    label = category.label,
                    isSelected = openCategory == category,
                    onClick = { onCategoryClick(category) }
                ) { color ->
                    when (category) {
                        CanvasTextSettingsCategory.FONT -> Text("Aa", color = color, fontFamily = currentFont.fontFamily(), fontSize = 14.sp)
                        CanvasTextSettingsCategory.WEIGHT -> Text("B", color = color, fontWeight = FontWeight(style.fontWeight), fontSize = 15.sp)
                        CanvasTextSettingsCategory.COLOR -> SettingsColorDot(color = currentTextColor)
                        CanvasTextSettingsCategory.BACKGROUND -> SettingsColorDot(color = currentBackground)
                        CanvasTextSettingsCategory.SIZE -> Text("A", color = color, fontSize = TextSizePreviewSizes[selectedSizeIndex])
                        CanvasTextSettingsCategory.ALIGN -> Icon(
                            painterResource(CanvasTextAlignment.named(style.textAlign).icon),
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsColorDot(color: Color?) {
    val emptySlashColor = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color ?: Color.Transparent)
            .then(if (color == null) Modifier.drawBehind { drawEmptySwatchSlash(emptySlashColor) } else Modifier)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape = CircleShape)
    )
}

private fun DrawScope.drawEmptySwatchSlash(color: Color) {
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5.dp.toPx())
}

@Composable
private fun CanvasMobilePill(hazeState: HazeState, isScrollable: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .height(44.dp)
            .customEmberrShadow(CircleShape)
            .clip(CircleShape)
            .emberrBlur(hazeState, EmberrBlur.Regular)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape = CircleShape)
    ) {
        Row(
            modifier = Modifier
                .then(if (isScrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = MobilePillHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(MobilePillItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun MobilePillItem(label: String, isSelected: Boolean, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(Modifier.requiredSize(width = MobilePillSelectedBackgroundWidth, height = 36.dp).background(tint.copy(alpha = 0.15f), CircleShape))
        }
        content(tint)
    }
}

@Composable
private fun MobileColorOption(color: Color?, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val emptySlashColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(32.dp)
                .border(width = 2.dp, color = if (isSelected) CanvasSelectionColor else Color.Transparent, shape = CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(color ?: Color.Transparent)
                .then(if (color == null) Modifier.drawBehind { drawEmptySwatchSlash(emptySlashColor) } else Modifier)
                .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = CircleShape)
        )
    }
}

@Composable
private fun WidthPreview(thickness: Dp, color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = thickness.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private val CanvasLineStyle.label: String
    get() = when (this) {
        CanvasLineStyle.ARROW -> "Arrow"
        CanvasLineStyle.DOTTED_ARROW -> "Dotted arrow"
        CanvasLineStyle.DASHED_ARROW -> "Dashed arrow"
        CanvasLineStyle.SOLID_LINE -> "Solid line"
        CanvasLineStyle.DOTTED_LINE -> "Dotted line"
        CanvasLineStyle.DASHED_LINE -> "Dashed line"
    }

@Composable
private fun LinePreview(style: CanvasLineStyle, color: Color) {
    Canvas(Modifier.size(width = 24.dp, height = 14.dp)) {
        drawCanvasLine(
            start = Offset(1.dp.toPx(), size.height / 2),
            end = Offset(size.width - 1.dp.toPx(), size.height / 2),
            width = 1.75.dp.toPx(),
            color = color,
            pattern = style.pattern,
            hasArrowHead = style.hasArrowHead
        )
    }
}

@Composable
fun CanvasLinePanel(
    selectedStyle: CanvasLineStyle,
    strokeStyle: CanvasStrokeStyle,
    hazeState: HazeState,
    onStyleChange: (CanvasLineStyle) -> Unit,
    onStrokeStyleChange: (CanvasStrokeStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    CanvasToolPanelSurface(hazeState = hazeState, modifier = modifier) {
        ToolPanelSection("Line") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CanvasLineStyle.entries.chunked(3).forEach { rowOfStyles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOfStyles.forEach { style ->
                            ToolPanelOptionButton(
                                label = style.label,
                                isSelected = selectedStyle == style,
                                onClick = { onStyleChange(style) }
                            ) { color -> LinePreview(style = style, color = color) }
                        }
                    }
                }
            }
        }
        ToolPanelSection("Stroke") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                strokeColorChoicesFor(CanvasStrokeTool.LINE).forEach { choice ->
                    ToolPanelColorSwatch(
                        color = choice.color,
                        label = choice.label,
                        isSelected = strokeStyle.colorName == choice.colorName,
                        onClick = { onStrokeStyleChange(strokeStyle.copy(colorName = choice.colorName)) }
                    )
                }
            }
        }
        ToolPanelSection("Stroke width") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CanvasStrokeTool.LINE.widthOptions.forEachIndexed { index, width ->
                    ToolPanelOptionButton(
                        label = WidthOptionLabels[index],
                        isSelected = strokeStyle.width == width,
                        onClick = { onStrokeStyleChange(strokeStyle.copy(width = width)) }
                    ) { color -> WidthPreview(thickness = WidthPreviewThicknesses[index], color = color) }
                }
            }
        }
    }
}

enum class CanvasLineSettingsCategory(val label: String) {
    STYLE("Line style"),
    STROKE("Stroke"),
    WIDTH("Stroke width")
}

@Composable
fun CanvasMobileLineSettings(
    selectedStyle: CanvasLineStyle,
    strokeStyle: CanvasStrokeStyle,
    openCategory: CanvasLineSettingsCategory?,
    hazeState: HazeState,
    onCategoryClick: (CanvasLineSettingsCategory) -> Unit,
    onStyleChange: (CanvasLineStyle) -> Unit,
    onStrokeStyleChange: (CanvasStrokeStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorChoices = strokeColorChoicesFor(CanvasStrokeTool.LINE)
    val currentColor = (colorChoices.firstOrNull { it.colorName == strokeStyle.colorName } ?: colorChoices.first()).color
    val widthOptions = CanvasStrokeTool.LINE.widthOptions
    val selectedWidthIndex = widthOptions.indexOf(strokeStyle.width).coerceAtLeast(0)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (openCategory) {
            CanvasLineSettingsCategory.STYLE -> CanvasMobilePill(hazeState) {
                CanvasLineStyle.entries.forEach { style ->
                    MobilePillItem(
                        label = style.label,
                        isSelected = selectedStyle == style,
                        onClick = { onStyleChange(style) }
                    ) { color -> LinePreview(style = style, color = color) }
                }
            }
            CanvasLineSettingsCategory.STROKE -> CanvasMobilePill(hazeState) {
                colorChoices.forEach { choice ->
                    MobileColorOption(
                        color = choice.color,
                        label = choice.label,
                        isSelected = strokeStyle.colorName == choice.colorName,
                        onClick = { onStrokeStyleChange(strokeStyle.copy(colorName = choice.colorName)) }
                    )
                }
            }
            CanvasLineSettingsCategory.WIDTH -> CanvasMobilePill(hazeState) {
                widthOptions.forEachIndexed { index, width ->
                    MobilePillItem(
                        label = WidthOptionLabels[index],
                        isSelected = strokeStyle.width == width,
                        onClick = { onStrokeStyleChange(strokeStyle.copy(width = width)) }
                    ) { color -> WidthPreview(thickness = WidthPreviewThicknesses[index], color = color) }
                }
            }
            null -> Unit
        }

        CanvasMobilePill(hazeState) {
            CanvasLineSettingsCategory.entries.forEach { category ->
                MobilePillItem(
                    label = category.label,
                    isSelected = openCategory == category,
                    onClick = { onCategoryClick(category) }
                ) { color ->
                    when (category) {
                        CanvasLineSettingsCategory.STYLE -> LinePreview(style = selectedStyle, color = color)
                        CanvasLineSettingsCategory.STROKE -> SettingsColorDot(color = currentColor)
                        CanvasLineSettingsCategory.WIDTH -> WidthPreview(thickness = WidthPreviewThicknesses[selectedWidthIndex], color = color)
                    }
                }
            }
        }
    }
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
    onColorSelected: (String?) -> Unit,
    showsColorOption: Boolean = true
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
        if (showsColorOption) {
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

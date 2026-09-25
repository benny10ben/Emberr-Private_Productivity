package com.emberr.presentation.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteKind
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.presentation.mobile.home.DropInsertPosition
import com.emberr.presentation.mobile.home.HomeItemKey
import com.emberr.presentation.mobile.home.ROOT_TREE_GUIDE_LINES
import com.emberr.presentation.mobile.home.SINGLE_ITEM_TREE_MENU
import com.emberr.presentation.mobile.home.TreeGuideLines
import com.emberr.presentation.mobile.home.TreeSelectionMenu
import com.emberr.presentation.shared.components.AnimatedFolderIcon
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.NoteKindTabs
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.file_text
import emberr.shared.generated.resources.group
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.star
import emberr.shared.generated.resources.template
import org.jetbrains.compose.resources.painterResource

private val INDENT_STEP          = 24.dp
private val SIDEBAR_BASE_START   = 8.dp
private val ROW_ICON_SLOT        = 26.dp
private val ROW_ICON_SIZE        = 24.dp
private val ROW_MIN_HEIGHT       = 42.dp
private val ROW_VERTICAL_PADDING = 2.dp
private val ROW_INNER_PADDING    = 4.dp
private val ROW_ICON_LEADING_GAP = 8.dp
internal val ROW_ICON_START      = SIDEBAR_BASE_START + ROW_INNER_PADDING + ROW_ICON_LEADING_GAP
private val ROW_LABEL_GAP        = 10.dp
private val ROW_SHAPE            = RoundedCornerShape(8.dp)
private val GUIDE_COLUMN_START   = ROW_ICON_START + 4.dp
private val GUIDE_WIDTH          = 1.5.dp
private val GUIDE_END_GAP        = 3.dp
private val GUIDE_CORNER         = 6.dp
private val ACTIVE_ACCENT_WIDTH  = 3.dp
private val ACTIVE_ACCENT_HEIGHT = 18.dp
private val ACTIVE_ACCENT_START  = 2.dp

val SIDEBAR_ROW_HEIGHT = ROW_MIN_HEIGHT + ROW_VERTICAL_PADDING * 2

const val SIDEBAR_HOVER_ALPHA    = 0.055f
const val SIDEBAR_SELECTED_ALPHA = 0.11f

private val RowColorSpec = tween<Color>(durationMillis = 180, easing = FastOutSlowInEasing)
private val RowFloatSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
private val ChevronSpec  = spring<Float>(stiffness = Spring.StiffnessMediumLow)

val sidebarRowTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.sp)

@Composable
fun sidebarRowBackground(isActive: Boolean, isSelected: Boolean, isHovered: Boolean): Color = when {
    isActive || isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = SIDEBAR_SELECTED_ALPHA)
    isHovered              -> MaterialTheme.colorScheme.onSurface.copy(alpha = SIDEBAR_HOVER_ALPHA)
    else                   -> Color.Transparent
}

@Composable
fun SidebarActiveAccent(isActive: Boolean, modifier: Modifier = Modifier) {
    val accentProgress by animateFloatAsState(if (isActive) 1f else 0f, RowFloatSpec, label = "sidebar_accent")
    if (accentProgress <= 0f) return
    Box(
        modifier = modifier
            .padding(start = ACTIVE_ACCENT_START)
            .width(ACTIVE_ACCENT_WIDTH)
            .height(ACTIVE_ACCENT_HEIGHT)
            .scale(scaleX = 1f, scaleY = accentProgress)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = accentProgress))
    )
}

data class SidebarClickModifiers(
    val addToSelection: Boolean,
    val extendSelection: Boolean
)

private suspend fun AwaitPointerEventScope.awaitAnyPointerPress(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        val press = event.changes.firstOrNull()
        if (event.type == PointerEventType.Press && press != null) return press
    }
}

private suspend fun AwaitPointerEventScope.waitForReleaseWithoutDragging(
    pressPosition: Offset
): PointerInputChange? {
    val allowedTravel = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: return null
        if (change.isConsumed) return null
        if (event.type == PointerEventType.Scroll) return null
        if (ListScrollActivity.isWheelScrollingRightNow()) return null
        if ((change.position - pressPosition).getDistance() > allowedTravel) return null
        if (change.changedToUp()) return change
        val afterEveryoneElseHandledIt = awaitPointerEvent(PointerEventPass.Final)
        if (afterEveryoneElseHandledIt.changes.any { it.isConsumed }) return null
    }
}

@Composable
fun Modifier.sidebarNoRippleClickable(onClick: () -> Unit): Modifier =
    this.pointerInput(onClick) {
        detectTapGestures(onTap = { onClick() })
    }

@Composable
internal fun DesktopNamePopup(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "Name...",
    onOpenTemplates: (() -> Unit)? = null,
    noteKind: NoteKind? = null,
    onNoteKindChange: (NoteKind) -> Unit = {}
) {
    var input by remember(initialValue) { mutableStateOf(initialValue) }
    val onSubmit: () -> Unit = { if (input.isNotBlank()) onConfirm(input.trim()) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
            if (onOpenTemplates != null) {
                Icon(
                    painter = painterResource(Res.drawable.template),
                    contentDescription = "Templates",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(22.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenTemplates)
                )
            }
        }
        if (noteKind != null) {
            NoteKindTabs(selectedKind = noteKind, onKindSelected = onNoteKindChange, modifier = Modifier.padding(bottom = 12.dp))
        }
        EmberrTextField(value = input, onValueChange = { input = it }, placeholder = placeholder, modifier = Modifier.fillMaxWidth(), onSubmit = onSubmit)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmberrButtonSecondary(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            EmberrButtonPrimary(text = confirmLabel, onClick = onSubmit, modifier = Modifier.weight(1f))
        }
    }
}

// Row composables

@Composable
fun SidebarFolderRow(
    folder: FolderEntity,
    level: Int,
    guideLines: TreeGuideLines = ROOT_TREE_GUIDE_LINES,
    isExpanded: Boolean,
    isSelected: Boolean,
    dragState: DesktopListDragState,
    menu: TreeSelectionMenu = SINGLE_ITEM_TREE_MENU,
    onClick: (SidebarClickModifiers) -> Unit,
    onToggleFavorite: () -> Unit = {},
    onAddNote: (String, NoteKind) -> Unit,
    onOpenTemplates: () -> Unit,
    templatesMenu: @Composable (isExpanded: Boolean, onDismiss: () -> Unit) -> Unit,
    onAddSubfolder: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowId = HomeItemKey.forFolder(folder.folderId)
    val currentOnClick by rememberUpdatedState(onClick)

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var showAddNotePopup by remember { mutableStateOf(false) }
    var addNoteKind by remember { mutableStateOf(NoteKind.NOTE) }
    var showTemplatesMenu by remember { mutableStateOf(false) }
    var showAddSubfolderPopup by remember { mutableStateOf(false) }
    var showRenamePopup by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val rowStartPadding = SIDEBAR_BASE_START + INDENT_STEP * level

    val isInsertBefore = dragState.dragging &&
            dragState.dropTargetId == rowId &&
            dragState.dropPosition == DropInsertPosition.BEFORE

    val isInsertAfter = dragState.dragging &&
            dragState.dropTargetId == rowId &&
            dragState.dropPosition == DropInsertPosition.AFTER

    val isIntoTarget = dragState.dragging &&
            dragState.dropTargetId == rowId &&
            dragState.dropPosition == DropInsertPosition.INTO &&
            dragState.payload != "$DRAG_PREFIX_FOLDER${folder.folderId}"

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgTarget: Color =
        if (isIntoTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else sidebarRowBackground(isActive = false, isSelected = isSelected, isHovered = isHovered)
    val bgColor by animateColorAsState(bgTarget, RowColorSpec, label = "fbg_${folder.folderId}")

    val borderAlpha by animateFloatAsState(if (isIntoTarget) 1f else 0f, RowFloatSpec, label = "fborder_${folder.folderId}")
    val beforeAlpha by animateFloatAsState(if (isInsertBefore) 1f else 0f, tween(150, easing = FastOutSlowInEasing), label = "fbefore_${folder.folderId}")
    val afterAlpha  by animateFloatAsState(if (isInsertAfter)  1f else 0f, tween(150, easing = FastOutSlowInEasing), label = "fafter_${folder.folderId}")

    val shape = ROW_SHAPE

    val guideColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawSidebarGuideLines(level, guideLines, guideColor) }
    ) {
        // Insert line above row
        if (beforeAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = beforeAlpha))
                    .scale(scaleX = beforeAlpha, scaleY = 1f)
                    .zIndex(10f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = SIDEBAR_BASE_START + INDENT_STEP * level,
                    end = 8.dp,
                    top = ROW_VERTICAL_PADDING,
                    bottom = ROW_VERTICAL_PADDING
                )
                .clip(shape)
                .background(bgColor)
                .then(
                    if (borderAlpha > 0f) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha),
                        shape
                    ) else Modifier
                )
                .hoverable(interactionSource)
                .pointerInput(rowStartPadding) {
                    awaitEachGesture {
                        val press = awaitAnyPointerPress()

                        if (currentEvent.buttons.isSecondaryPressed) {
                            press.consume()
                            contextMenuOffset = with(density) {
                                DpOffset(rowStartPadding + press.position.x.toDp(), ROW_VERTICAL_PADDING + press.position.y.toDp())
                            }
                            showContextMenu = true
                            waitForUpOrCancellation()?.consume()
                            return@awaitEachGesture
                        }

                        if (currentEvent.buttons.isTertiaryPressed) return@awaitEachGesture

                        val pressedModifiers = currentEvent.keyboardModifiers
                        val up = waitForReleaseWithoutDragging(press.position) ?: return@awaitEachGesture
                        up.consume()
                        currentOnClick(
                            SidebarClickModifiers(
                                addToSelection = pressedModifiers.isCtrlPressed || pressedModifiers.isMetaPressed,
                                extendSelection = pressedModifiers.isShiftPressed
                            )
                        )
                    }
                }
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(horizontal = ROW_INNER_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(ROW_ICON_LEADING_GAP))
            Box(Modifier.width(ROW_ICON_SLOT), contentAlignment = Alignment.Center) {
                AnimatedFolderIcon(
                    isExpanded = isExpanded,
                    tint = if (isIntoTarget) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isHovered || isSelected) 0.9f else 0.6f),
                    modifier = Modifier.size(ROW_ICON_SIZE - 1.dp)
                )
            }
            Spacer(Modifier.width(ROW_LABEL_GAP))
            Text(
                text = folder.name,
                style = sidebarRowTextStyle,
                fontWeight = FontWeight.Medium,
                color = if (isIntoTarget) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            when {
                isSelected -> SidebarTrailingCheck()
                isHovered && !dragState.dragging -> {
                    SidebarHoverAction(painterResource(Res.drawable.plus), "New note here") { addNoteKind = NoteKind.NOTE; showAddNotePopup = true }
                    Spacer(Modifier.width(2.dp))
                }
            }

            Box {
                EmberrDesktopMenu(expanded = showAddNotePopup, onDismissRequest = { showAddNotePopup = false }, modifier = Modifier.width(280.dp)) {
                    DesktopNamePopup(
                        title = "New Note in ${folder.name}",
                        initialValue = "",
                        confirmLabel = "Create",
                        onConfirm = { title -> onAddNote(title, addNoteKind); showAddNotePopup = false },
                        onDismiss = { showAddNotePopup = false },
                        placeholder = "Note title...",
                        noteKind = addNoteKind,
                        onNoteKindChange = { addNoteKind = it },
                        onOpenTemplates = {
                            showAddNotePopup = false
                            onOpenTemplates()
                            showTemplatesMenu = true
                        }
                    )
                }
                templatesMenu(showTemplatesMenu) { showTemplatesMenu = false }
                EmberrDesktopMenu(expanded = showAddSubfolderPopup, onDismissRequest = { showAddSubfolderPopup = false }, modifier = Modifier.width(280.dp)) {
                    DesktopNamePopup(
                        title = "New Subfolder",
                        initialValue = "",
                        confirmLabel = "Create",
                        onConfirm = { name -> onAddSubfolder(name); showAddSubfolderPopup = false },
                        onDismiss = { showAddSubfolderPopup = false }
                    )
                }
                EmberrDesktopMenu(expanded = showRenamePopup, onDismissRequest = { showRenamePopup = false }, modifier = Modifier.width(280.dp)) {
                    DesktopNamePopup(
                        title = "Rename Folder",
                        initialValue = folder.name,
                        confirmLabel = "Save",
                        onConfirm = { name -> onRename(name); showRenamePopup = false },
                        onDismiss = { showRenamePopup = false }
                    )
                }
            }
        }

        // Right-click context menu, anchored at the exact press position
        Box(modifier = Modifier.offset(x = contextMenuOffset.x, y = contextMenuOffset.y)) {
            EmberrDesktopMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = DpOffset.Zero
            ) {
                SidebarMenuItems {
                    if (menu.showRename) {
                        SidebarMenuItem("Add Subfolder", Icons.Default.CreateNewFolder) { showContextMenu = false; showAddSubfolderPopup = true }
                        SidebarMenuItem("Rename", Icons.Default.Edit) { showContextMenu = false; showRenamePopup = true }
                    }
                    SidebarMenuItem(menu.deleteLabel, Icons.Default.Delete, isDestructive = true) { showContextMenu = false; onDelete() }
                }
            }
        }

        // Insert line below row (last item)
        if (afterAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = afterAlpha))
                    .scale(scaleX = afterAlpha, scaleY = 1f)
                    .zIndex(10f)
            )
        }
    }
}

@Composable
fun SidebarNoteRow(
    modifier: Modifier = Modifier,
    note: NoteMetadataEntity,
    level: Int,
    guideLines: TreeGuideLines = ROOT_TREE_GUIDE_LINES,
    isActive: Boolean,
    isSelected: Boolean,
    dragState: DesktopListDragState,
    menu: TreeSelectionMenu = SINGLE_ITEM_TREE_MENU,
    onClick: (SidebarClickModifiers) -> Unit,
    onToggleFavorite: () -> Unit = {},
    onRename: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    rowKey: String = HomeItemKey.forNote(note.noteId)
) {
    val currentOnClick by rememberUpdatedState(onClick)

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var showRenamePopup by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val rowStartPadding = SIDEBAR_BASE_START + INDENT_STEP * level

    val isInsertBefore = dragState.dragging &&
            dragState.dropTargetId == rowKey &&
            dragState.dropPosition == DropInsertPosition.BEFORE

    val isInsertAfter = dragState.dragging &&
            dragState.dropTargetId == rowKey &&
            dragState.dropPosition == DropInsertPosition.AFTER

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgTarget = sidebarRowBackground(isActive = isActive, isSelected = isSelected, isHovered = isHovered)
    val bgColor by animateColorAsState(bgTarget, RowColorSpec, label = "nbg_${note.noteId}")

    val beforeAlpha by animateFloatAsState(if (isInsertBefore) 1f else 0f, tween(150, easing = FastOutSlowInEasing), label = "nbefore_${note.noteId}")
    val afterAlpha  by animateFloatAsState(if (isInsertAfter)  1f else 0f, tween(150, easing = FastOutSlowInEasing), label = "nafter_${note.noteId}")

    val guideColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawSidebarGuideLines(level, guideLines, guideColor) }
    ) {
        SidebarActiveAccent(isActive = isActive, modifier = Modifier.align(Alignment.CenterStart).zIndex(2f))

        if (beforeAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = beforeAlpha))
                    .scale(scaleX = beforeAlpha, scaleY = 1f)
                    .zIndex(10f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = SIDEBAR_BASE_START + INDENT_STEP * level,
                    end = 8.dp,
                    top = ROW_VERTICAL_PADDING,
                    bottom = ROW_VERTICAL_PADDING
                )
                .clip(ROW_SHAPE)
                .background(bgColor)
                .hoverable(interactionSource)
                .pointerInput(rowStartPadding) {
                    awaitEachGesture {
                        val press = awaitAnyPointerPress()

                        if (currentEvent.buttons.isSecondaryPressed) {
                            press.consume()
                            contextMenuOffset = with(density) {
                                DpOffset(rowStartPadding + press.position.x.toDp(), ROW_VERTICAL_PADDING + press.position.y.toDp())
                            }
                            showContextMenu = true
                            waitForUpOrCancellation()?.consume()
                            return@awaitEachGesture
                        }

                        if (currentEvent.buttons.isTertiaryPressed) return@awaitEachGesture

                        val pressedModifiers = currentEvent.keyboardModifiers
                        val up = waitForReleaseWithoutDragging(press.position) ?: return@awaitEachGesture
                        up.consume()
                        currentOnClick(
                            SidebarClickModifiers(
                                addToSelection = pressedModifiers.isCtrlPressed || pressedModifiers.isMetaPressed,
                                extendSelection = pressedModifiers.isShiftPressed
                            )
                        )
                    }
                }
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(horizontal = ROW_INNER_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(ROW_ICON_LEADING_GAP))
            Box(Modifier.width(ROW_ICON_SLOT), contentAlignment = Alignment.Center) {
                if (!note.icon.isNullOrEmpty()) {
                    Text(text = note.icon, fontSize = 18.sp, textAlign = TextAlign.Center)
                } else {
                    Icon(
                        painterResource(if (note.kind == NoteKind.CANVAS) Res.drawable.group else Res.drawable.file_text),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (isActive || isHovered || isSelected) 0.9f else 0.55f
                        ),
                        modifier = Modifier.size(ROW_ICON_SIZE)
                    )
                }
            }
            Spacer(Modifier.width(ROW_LABEL_GAP))
            Text(
                text = note.title.ifEmpty { "Untitled" },
                style = sidebarRowTextStyle,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isActive) 1f else 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            when {
                isSelected -> SidebarTrailingCheck()
                note.isFavorite -> {
                    Icon(
                        painterResource(Res.drawable.star),
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(end = 6.dp).size(14.dp)
                    )
                }
            }

            Box {
                EmberrDesktopMenu(expanded = showRenamePopup, onDismissRequest = { showRenamePopup = false }, modifier = Modifier.width(280.dp)) {
                    DesktopNamePopup(
                        title = "Rename Note",
                        initialValue = note.title,
                        confirmLabel = "Save",
                        onConfirm = { name -> onRename(name); showRenamePopup = false },
                        onDismiss = { showRenamePopup = false }
                    )
                }
            }
        }

        // Right-click context menu, anchored at the exact press position
        Box(modifier = Modifier.offset(x = contextMenuOffset.x, y = contextMenuOffset.y)) {
            EmberrDesktopMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = DpOffset.Zero
            ) {
                SidebarMenuItems {
                    if (menu.showRename) {
                        SidebarMenuItem("Rename", Icons.Default.Edit) { showContextMenu = false; showRenamePopup = true }
                    }
                    if (menu.showFavorite) {
                        SidebarMenuItem(menu.favoriteLabel, Icons.Default.Star) { showContextMenu = false; onToggleFavorite() }
                    }
                    SidebarMenuItem(menu.deleteLabel, Icons.Default.Delete, isDestructive = true) { showContextMenu = false; onDelete() }
                }
            }
        }

        if (afterAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = afterAlpha))
                    .scale(scaleX = afterAlpha, scaleY = 1f)
                    .zIndex(10f)
            )
        }
    }
}

private fun DrawScope.drawSidebarGuideLines(level: Int, guideLines: TreeGuideLines, color: Color) {
    if (level == 0) return

    val indentStep = INDENT_STEP.toPx()
    val guideColumnStart = GUIDE_COLUMN_START.toPx()
    val lineWidth = GUIDE_WIDTH.toPx()

    guideLines.ancestorVerticalLines.forEachIndexed { depth, isVisible ->
        if (!isVisible) return@forEachIndexed
        val x = depth * indentStep + guideColumnStart
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round
        )
    }

    val elbowX = (level - 1) * indentStep + guideColumnStart
    val middleY = size.height / 2f
    val rowIconStartX = level * indentStep + ROW_ICON_START.toPx() - GUIDE_END_GAP.toPx()
    val cornerRadius = minOf(GUIDE_CORNER.toPx(), middleY, rowIconStartX - elbowX)

    if (!guideLines.isLastChildOfParent) {
        drawLine(
            color = color,
            start = Offset(elbowX, 0f),
            end = Offset(elbowX, size.height),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round
        )
    }

    val elbowStartY = if (guideLines.isLastChildOfParent) 0f else middleY - cornerRadius
    val elbow = Path().apply {
        moveTo(elbowX, elbowStartY)
        lineTo(elbowX, middleY - cornerRadius)
        quadraticTo(elbowX, middleY, elbowX + cornerRadius, middleY)
        lineTo(rowIconStartX, middleY)
    }
    drawPath(path = elbow, color = color, style = Stroke(width = lineWidth, cap = StrokeCap.Round))
}

@Composable
fun SidebarSectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val chevronAlpha by animateFloatAsState(if (isHovered) 0.8f else 0.3f, RowFloatSpec, label = "header_chevron_$title")
    val chevronRotation by animateFloatAsState(if (isExpanded) 0f else -90f, ChevronSpec, label = "header_turn_$title")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .padding(start = ROW_ICON_START, end = 10.dp)
            .padding(top = 26.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .sidebarNoRippleClickable { onToggle() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle $title",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = chevronAlpha),
                modifier = Modifier.padding(start = 2.dp).size(22.dp).rotate(chevronRotation)
            )
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                content = trailing
            )
        }
    }
}

@Composable
private fun Modifier.sidebarHoverActionSurface(interactionSource: MutableInteractionSource, isHovered: Boolean, onClick: () -> Unit): Modifier {
    val background by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent,
        RowColorSpec,
        label = "hover_action_bg"
    )
    return this
        .size(30.dp)
        .clip(RoundedCornerShape(7.dp))
        .background(background)
        .hoverable(interactionSource)
        .sidebarNoRippleClickable { onClick() }
}

@Composable
private fun SidebarHoverAction(painter: Painter, description: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier.sidebarHoverActionSurface(interactionSource, isHovered, onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isHovered) 0.95f else 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SidebarTrailingCheck() {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(20.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp)
        )
    }
}

private val SidebarMenuWidth = 220.dp

@Composable
private fun SidebarMenuItems(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.width(SidebarMenuWidth).padding(vertical = 4.dp),
        content = content
    )
}

@Composable
private fun SidebarMenuItem(
    text: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val baseColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val contentColor = if (enabled) baseColor else baseColor.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}

@Composable
fun SidebarEmptyNotesHint(
    onCreateNote: () -> Unit,
    onCreateFolder: () -> Unit
) {
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
    val actionColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ROW_ICON_START, end = 10.dp, top = 6.dp, bottom = 8.dp)
    ) {
        Text(
            text = "This space is empty.",
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Start with",
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor
            )
            Text(
                text = "a note",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = actionColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .sidebarNoRippleClickable(onCreateNote)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(
                text = "or",
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor
            )
            Text(
                text = "a folder",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = actionColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .sidebarNoRippleClickable(onCreateFolder)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

package com.emberr.presentation.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.SpaceEntity
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrDesktopMenuItem
import com.emberr.presentation.shared.components.EmberrDesktopMenuItems
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.plus
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

private val BAR_HEIGHT = 44.dp
private val DOT_SIZE = 8.dp
private val ACTIVE_DOT_WIDTH = 22.dp
private val DOT_SPACING = 4.dp
private val DOT_SLOT_WIDTH = 24.dp
private const val DRAGGED_DOT_SCALE = 1.35f
private val PLUS_BUTTON_SIZE = 28.dp
private val PLUS_ICON_SIZE = 16.dp
private val PLUS_BUTTON_END_PADDING = 12.dp

private val DotWidthSpec = tween<Dp>(durationMillis = 220, easing = FastOutSlowInEasing)
private val DotColorSpec = tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)

private val MENU_BUTTON_SIZE = 22.dp
private val MENU_ICON_SIZE = 16.dp
private val SPACE_HEADER_HEIGHT = 32.dp
private val SPACE_HEADER_TOP_PADDING = 12.dp

private val HeaderAlphaSpec = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)

val DESKTOP_SPACE_BAR_HEIGHT = BAR_HEIGHT

@Composable
fun DesktopSpaceBar(
    spaces: List<SpaceEntity>,
    activeSpaceId: String,
    onOpenSpace: (String) -> Unit,
    onCreateSpace: (String) -> Unit,
    onReorderSpaces: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreatePopup by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth().height(BAR_HEIGHT)) {
        SpaceDotsRow(
            spaces = spaces,
            activeSpaceId = activeSpaceId,
            onOpenSpace = onOpenSpace,
            onReorderSpaces = onReorderSpaces,
            modifier = Modifier.align(Alignment.Center)
        )

        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = PLUS_BUTTON_END_PADDING)) {
            AddSpaceButton(onClick = { showAddMenu = true })

            EmberrDesktopMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false }
            ) {
                EmberrDesktopMenuItems {
                    EmberrDesktopMenuItem(text = "Create Space", icon = Icons.Default.Add) {
                        showAddMenu = false
                        showCreatePopup = true
                    }
                    EmberrDesktopMenuItem(
                        text = "Split View",
                        icon = Icons.Default.Splitscreen,
                        enabled = false
                    ) {}
                }
            }

            EmberrDesktopMenu(
                expanded = showCreatePopup,
                onDismissRequest = { showCreatePopup = false },
                modifier = Modifier.width(260.dp)
            ) {
                DesktopNamePopup(
                    title = "New Space",
                    initialValue = "",
                    confirmLabel = "Create",
                    onConfirm = { name -> onCreateSpace(name); showCreatePopup = false },
                    onDismiss = { showCreatePopup = false },
                    placeholder = "Space name..."
                )
            }
        }
    }
}

@Composable
private fun SpaceDotsRow(
    spaces: List<SpaceEntity>,
    activeSpaceId: String,
    onOpenSpace: (String) -> Unit,
    onReorderSpaces: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val slotStridePx = with(density) { (DOT_SLOT_WIDTH + DOT_SPACING).toPx() }

    var draggedFromIndex by remember { mutableStateOf(-1) }
    var draggedToIndex by remember { mutableStateOf(-1) }

    val displayedSpaces = if (draggedFromIndex >= 0 && draggedToIndex >= 0) {
        spaces.toMutableList().apply { add(draggedToIndex, removeAt(draggedFromIndex)) }
    } else {
        spaces
    }

    Row(
        modifier = modifier.pointerInput(spaces) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val startIndex = (down.position.x / slotStridePx).toInt()
                if (startIndex !in spaces.indices) return@awaitEachGesture

                var travelledX = 0f
                var isReordering = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break

                    if (change.changedToUp()) {
                        if (!isReordering) {
                            onOpenSpace(spaces[startIndex].spaceId)
                        } else if (draggedToIndex >= 0 && draggedToIndex != startIndex) {
                            val reordered = spaces.toMutableList()
                                .apply { add(draggedToIndex, removeAt(startIndex)) }
                            onReorderSpaces(reordered.map { it.spaceId })
                        }
                        break
                    }

                    travelledX += change.positionChange().x
                    if (!isReordering && abs(travelledX) > viewConfiguration.touchSlop) {
                        isReordering = true
                        draggedFromIndex = startIndex
                    }
                    if (isReordering) {
                        change.consume()
                        val slotsMoved = (travelledX / slotStridePx).roundToInt()
                        draggedToIndex = (startIndex + slotsMoved).coerceIn(0, spaces.lastIndex)
                    }
                }

                draggedFromIndex = -1
                draggedToIndex = -1
            }
        },
        horizontalArrangement = Arrangement.spacedBy(DOT_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        displayedSpaces.forEachIndexed { index, space ->
            Box(
                modifier = Modifier.width(DOT_SLOT_WIDTH).height(BAR_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                SpaceDot(
                    isActive = space.spaceId == activeSpaceId,
                    isBeingMoved = draggedFromIndex >= 0 && index == draggedToIndex
                )
            }
        }
    }
}

@Composable
private fun SpaceDot(isActive: Boolean, isBeingMoved: Boolean) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val dotScale by animateFloatAsState(
        if (isBeingMoved) DRAGGED_DOT_SCALE else 1f,
        HeaderAlphaSpec,
        label = "space_dot_scale"
    )

    val dotWidth by animateDpAsState(
        if (isActive) ACTIVE_DOT_WIDTH else DOT_SIZE,
        DotWidthSpec,
        label = "space_dot_width"
    )
    val dotColor by animateColorAsState(
        when {
            isActive -> MaterialTheme.colorScheme.onSurface
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
        },
        DotColorSpec,
        label = "space_dot_color"
    )

    Box(
        modifier = Modifier
            .width(dotWidth)
            .height(DOT_SIZE)
            .scale(dotScale)
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(DOT_SIZE / 2))
            .background(dotColor)
    )
}

@Composable
private fun AddSpaceButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = SIDEBAR_HOVER_ALPHA) else Color.Transparent,
        DotColorSpec,
        label = "add_space_background"
    )

    Box(
        modifier = Modifier
            .size(PLUS_BUTTON_SIZE)
            .hoverable(interactionSource)
            .clip(CircleShape)
            .background(backgroundColor)
            .sidebarNoRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.plus),
            contentDescription = "Add space",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.size(PLUS_ICON_SIZE)
        )
    }
}

@Composable
fun SidebarSpaceHeader(
    displayName: String,
    canDelete: Boolean,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showRenamePopup by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val areActionsVisible = isHovered || showMenu || showRenamePopup || showDeleteConfirm
    val actionsAlpha by animateFloatAsState(
        if (areActionsVisible) 1f else 0f,
        HeaderAlphaSpec,
        label = "space_header_actions"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SPACE_HEADER_TOP_PADDING)
            .height(SPACE_HEADER_HEIGHT)
            .hoverable(interactionSource)
            .padding(start = ROW_ICON_START, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Box {
            SpaceMenuButton(alpha = actionsAlpha, onClick = { showMenu = true })

            EmberrDesktopMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                EmberrDesktopMenuItems {
                    EmberrDesktopMenuItem(text = "Rename Space", icon = Icons.Default.Edit) {
                        showMenu = false
                        showRenamePopup = true
                    }
                    EmberrDesktopMenuItem(
                        text = "Delete Space",
                        icon = Icons.Default.Delete,
                        isDestructive = true,
                        enabled = canDelete
                    ) {
                        showMenu = false
                        showDeleteConfirm = true
                    }
                }
            }

            EmberrDesktopMenu(
                expanded = showRenamePopup,
                onDismissRequest = { showRenamePopup = false },
                modifier = Modifier.width(260.dp)
            ) {
                DesktopNamePopup(
                    title = "Rename Space",
                    initialValue = displayName,
                    confirmLabel = "Save",
                    onConfirm = { name -> onRename(name); showRenamePopup = false },
                    onDismiss = { showRenamePopup = false },
                    placeholder = "Space name..."
                )
            }

            EmberrDesktopMenu(
                expanded = showDeleteConfirm,
                onDismissRequest = { showDeleteConfirm = false },
                modifier = Modifier.width(300.dp)
            ) {
                DeleteSpaceConfirmation(
                    displayName = displayName,
                    onConfirm = { onDelete(); showDeleteConfirm = false },
                    onDismiss = { showDeleteConfirm = false }
                )
            }
        }
    }
}

@Composable
private fun SpaceMenuButton(alpha: Float, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = SIDEBAR_HOVER_ALPHA * alpha) else Color.Transparent,
        DotColorSpec,
        label = "space_menu_background"
    )

    Box(
        modifier = Modifier
            .size(MENU_BUTTON_SIZE)
            .hoverable(interactionSource)
            .clip(CircleShape)
            .background(backgroundColor)
            .sidebarNoRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "Space options",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f * alpha),
            modifier = Modifier.size(MENU_ICON_SIZE)
        )
    }
}

@Composable
private fun DeleteSpaceConfirmation(
    displayName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "Delete \"$displayName\"?",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Every note, folder, tag and chat in this space is deleted with it. This cannot be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmberrButtonSecondary(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(text = "Delete", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

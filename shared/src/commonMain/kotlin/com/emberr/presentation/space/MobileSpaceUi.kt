package com.emberr.presentation.space

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.SpaceEntity
import com.emberr.presentation.home.RenameBottomSheet
import com.emberr.presentation.shared.components.EmberrAlertDialog
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrShadowElevation
import com.emberr.presentation.shared.components.NoRippleIndicationNodeFactory
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.pen_square
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.trash
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

private val SPACE_ROW_HEIGHT = 48.dp
private val HANDLE_ZONE_WIDTH = 44.dp
private val HANDLE_ICON_SIZE = 20.dp
private val ROW_SHAPE = RoundedCornerShape(12.dp)

private const val ACTIVE_ROW_ALPHA = 0.11f
private const val MOVING_ROW_ALPHA = 0.18f

private val RowColorSpec = tween<Color>(durationMillis = 160, easing = FastOutSlowInEasing)

@Composable
fun SpaceOptionsSheets(
    expanded: Boolean,
    spaces: List<SpaceEntity>,
    activeSpaceId: String,
    onDismiss: () -> Unit,
    onOpenSpace: (String) -> Unit,
    onReorderSpaces: (List<String>) -> Unit,
    onCreateSpace: (String) -> Unit,
    onRenameSpace: (String) -> Unit,
    onDeleteSpace: () -> Unit
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var showRenameSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val activeSpaceName = spaces.firstOrNull { it.spaceId == activeSpaceId }?.displayName.orEmpty()

    EmberrBottomSheet(expanded = expanded, onDismiss = onDismiss, title = "Spaces") { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)) {

            SpaceSheetRow(
                text = "Create Space",
                icon = painterResource(Res.drawable.plus)
            ) { closeAnd { showCreateSheet = true } }

            SpaceSheetRow(
                text = "Rename Space",
                icon = painterResource(Res.drawable.pen_square)
            ) { closeAnd { showRenameSheet = true } }

            SpaceSheetRow(
                text = "Delete Space",
                icon = painterResource(Res.drawable.trash),
                isDestructive = true,
                enabled = spaces.size > 1
            ) { closeAnd { showDeleteConfirm = true } }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            SpaceReorderList(
                spaces = spaces,
                activeSpaceId = activeSpaceId,
                onOpenSpace = { spaceId -> closeAnd { onOpenSpace(spaceId) } },
                onReorderSpaces = onReorderSpaces
            )
        }
    }

    RenameBottomSheet(
        expanded = showCreateSheet,
        currentName = "",
        onDismiss = { showCreateSheet = false },
        onRename = { name -> showCreateSheet = false; onCreateSpace(name) },
        title = "New Space",
        subtitle = "Work, Personal, anything you like.",
        confirmLabel = "Create",
        placeholder = "Space name..."
    )

    RenameBottomSheet(
        expanded = showRenameSheet,
        currentName = activeSpaceName,
        onDismiss = { showRenameSheet = false },
        onRename = { name -> showRenameSheet = false; onRenameSpace(name) },
        title = "Rename Space",
        subtitle = "Pick a new name.",
        placeholder = "Space name..."
    )

    if (showDeleteConfirm) {
        EmberrAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete \"$activeSpaceName\"?"
        ) {
            Text(
                text = "Every note, folder, tag and chat in this space is deleted with it. This cannot be undone.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EmberrButtonSecondary(
                    text = "Cancel",
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showDeleteConfirm = false; onDeleteSpace() },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = EmberrShadowElevation.None)
                ) {
                    Text(text = "Delete", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SpaceReorderList(
    spaces: List<SpaceEntity>,
    activeSpaceId: String,
    onOpenSpace: (String) -> Unit,
    onReorderSpaces: (List<String>) -> Unit
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { SPACE_ROW_HEIGHT.toPx() }
    val handleZoneWidthPx = with(density) { HANDLE_ZONE_WIDTH.toPx() }

    var draggedFromIndex by remember { mutableStateOf(-1) }
    var draggedToIndex by remember { mutableStateOf(-1) }

    val displayedSpaces = if (draggedFromIndex >= 0 && draggedToIndex >= 0) {
        spaces.toMutableList().apply { add(draggedToIndex, removeAt(draggedFromIndex)) }
    } else {
        spaces
    }

    Column(
        modifier = Modifier.fillMaxWidth().pointerInput(spaces) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val startIndex = (down.position.y / rowHeightPx).toInt()
                if (startIndex !in spaces.indices) return@awaitEachGesture

                val startedOnHandle = down.position.x <= handleZoneWidthPx
                var travelledY = 0f
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

                    travelledY += change.positionChange().y

                    if (!isReordering && abs(travelledY) > viewConfiguration.touchSlop) {
                        if (!startedOnHandle) break
                        isReordering = true
                        draggedFromIndex = startIndex
                    }

                    if (isReordering) {
                        change.consume()
                        val slotsMoved = (travelledY / rowHeightPx).roundToInt()
                        draggedToIndex = (startIndex + slotsMoved).coerceIn(0, spaces.lastIndex)
                    }
                }

                draggedFromIndex = -1
                draggedToIndex = -1
            }
        }
    ) {
        displayedSpaces.forEachIndexed { index, space ->
            SpaceRow(
                displayName = space.displayName,
                isActive = space.spaceId == activeSpaceId,
                isBeingMoved = draggedFromIndex >= 0 && index == draggedToIndex
            )
        }
    }
}

@Composable
private fun SpaceRow(displayName: String, isActive: Boolean, isBeingMoved: Boolean) {
    val backgroundColor by animateColorAsState(
        when {
            isBeingMoved -> MaterialTheme.colorScheme.onSurface.copy(alpha = MOVING_ROW_ALPHA)
            isActive -> MaterialTheme.colorScheme.onSurface.copy(alpha = ACTIVE_ROW_ALPHA)
            else -> Color.Transparent
        },
        RowColorSpec,
        label = "space_row_background"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SPACE_ROW_HEIGHT)
            .clip(ROW_SHAPE)
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(HANDLE_ZONE_WIDTH).height(SPACE_ROW_HEIGHT),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Reorder space",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(HANDLE_ICON_SIZE)
            )
        }

        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
    }
}

@Composable
private fun SpaceSheetRow(
    text: String,
    icon: Painter,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val baseColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val contentColor = if (enabled) baseColor else baseColor.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}

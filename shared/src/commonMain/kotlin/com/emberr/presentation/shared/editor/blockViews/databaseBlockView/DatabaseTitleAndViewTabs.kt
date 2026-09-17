@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.ViewType
import com.emberr.presentation.shared.editor.EditorActions
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_up_down
import emberr.shared.generated.resources.sliders_horizontal
import emberr.shared.generated.resources.square_kanban
import emberr.shared.generated.resources.table
import emberr.shared.generated.resources.widget
import emberr.shared.generated.resources.x
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun DatabaseTitleField(block: DatabaseBlock, inSelectionMode: Boolean, actions: EditorActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var titleTfv by remember(block.id) {
            mutableStateOf(TextFieldValue(block.title, TextRange(block.title.length)))
        }
        var lastSentTitle by remember(block.id) { mutableStateOf(block.title) }

        LaunchedEffect(block.title) {
            if (titleTfv.text != block.title && block.title != lastSentTitle) {
                titleTfv = titleTfv.copy(
                    text = block.title,
                    selection = TextRange(titleTfv.selection.start.coerceAtMost(block.title.length))
                )
            }
        }

        LaunchedEffect(titleTfv.text) {
            if (titleTfv.text != block.title) {
                delay(400L.milliseconds)
                lastSentTitle = titleTfv.text
                actions.onUpdateDbTitle(block.id, titleTfv.text)
            }
        }

        BasicTextField(
            value = titleTfv,
            onValueChange = { titleTfv = it },
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (titleTfv.text.isEmpty()) {
                    Text(
                        text = "Untitled Database",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                inner()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !inSelectionMode,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DatabaseViewTabsRow(
    block: DatabaseBlock,
    activeView: DatabaseView,
    visibleColumns: List<DatabaseColumn>,
    inSelectionMode: Boolean,
    state: DatabaseSheetState,
    actions: EditorActions,
    desktopDropdown: @Composable (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val orderedViews = remember(block.views) { mutableStateListOf(*block.views.toTypedArray()) }
        var draggedViewId by remember { mutableStateOf<String?>(null) }
        var dragStartIndex by remember { mutableStateOf(-1) }
        var dragPointerX by remember { mutableStateOf(0f) }
        val viewBoundsInWindow = remember { mutableStateMapOf<String, Rect>() }

        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            orderedViews.forEach { view ->
                key(view.id) {
                    val isActive = view.id == activeView.id
                    val isDragged = draggedViewId == view.id

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { viewBoundsInWindow[view.id] = it.boundsInWindow() }
                            .graphicsLayer { alpha = if (isDragged) 0.6f else 1f }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                            modifier = Modifier
                                .clickable(enabled = !inSelectionMode) {
                                    if (isActive) {
                                        state.textInput = view.name
                                        state.renamingViewId = view.id
                                        state.open(DatabaseSheet.RENAME_VIEW)
                                    } else {
                                        actions.onSetActiveDatabaseView(block.id, view.id)
                                    }
                                }
                                .pointerInput(view.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedViewId = view.id
                                            dragStartIndex = block.views.indexOfFirst { it.id == view.id }
                                            dragPointerX = viewBoundsInWindow[view.id]?.center?.x ?: 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragPointerX += dragAmount.x
                                            val hovered = viewBoundsInWindow.entries
                                                .firstOrNull { (_, rect) -> dragPointerX in rect.left..rect.right }
                                                ?.key
                                            if (hovered != null && hovered != view.id) {
                                                val from = orderedViews.indexOfFirst { it.id == view.id }
                                                val to = orderedViews.indexOfFirst { it.id == hovered }
                                                if (from != -1 && to != -1) {
                                                    orderedViews.add(to, orderedViews.removeAt(from))
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedViewId = null
                                            val finalIndex = orderedViews.indexOfFirst { it.id == view.id }
                                            if (dragStartIndex != -1 && finalIndex != -1 && dragStartIndex != finalIndex) {
                                                actions.onReorderDatabaseViews(block.id, dragStartIndex, finalIndex)
                                            }
                                            dragStartIndex = -1
                                        },
                                        onDragCancel = {
                                            draggedViewId = null
                                            dragStartIndex = -1
                                        }
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(view.type.tabIcon()),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isActive) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = view.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        desktopDropdown(state.renamingViewId == view.id && state.currentSheet == DatabaseSheet.RENAME_VIEW)
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                val hasSort = activeView.activeSorts.isNotEmpty()
                ToolbarIconButton(
                    iconRes = Res.drawable.arrow_up_down,
                    contentDescription = null,
                    isHighlighted = hasSort,
                    enabled = !inSelectionMode
                ) {
                    val firstColumn = visibleColumns.firstOrNull() ?: return@ToolbarIconButton
                    state.openForColumn(
                        activeView.activeSorts.firstOrNull()?.columnId ?: firstColumn.id,
                        DatabaseSheet.SORT
                    )
                }
                desktopDropdown(state.currentSheet == DatabaseSheet.SORT)
            }

            Box {
                val hasFilter = activeView.activeFilters.isNotEmpty()
                val hasGroupBy = activeView.type == ViewType.KANBAN && activeView.groupByColumnId != null
                val hasCardSize =
                    activeView.type == ViewType.GALLERY && activeView.galleryCardSize != GalleryCardSize.MEDIUM
                ToolbarIconButton(
                    iconRes = Res.drawable.sliders_horizontal,
                    contentDescription = "Table settings",
                    isHighlighted = hasFilter || hasGroupBy || hasCardSize,
                    enabled = !inSelectionMode
                ) { state.open(DatabaseSheet.TABLE_SETTINGS) }

                desktopDropdown(
                    state.currentSheet in listOf(
                        DatabaseSheet.TABLE_SETTINGS,
                        DatabaseSheet.FILTER,
                        DatabaseSheet.SAVE_AS_TEMPLATE,
                        DatabaseSheet.ADD_VIEW,
                        DatabaseSheet.GROUP_BY,
                        DatabaseSheet.CARD_SIZE
                    )
                )
            }
        }
    }
}

private fun ViewType.tabIcon() = when (this) {
    ViewType.TABLE -> Res.drawable.table
    ViewType.KANBAN -> Res.drawable.square_kanban
    ViewType.GALLERY -> Res.drawable.widget
}

@Composable
private fun ToolbarIconButton(
    iconRes: DrawableResource,
    contentDescription: String?,
    isHighlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun DatabaseActiveFilterChips(
    block: DatabaseBlock,
    activeView: DatabaseView,
    visibleColumns: List<DatabaseColumn>,
    inSelectionMode: Boolean,
    actions: EditorActions
) {
    if (activeView.activeFilters.isEmpty()) return

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        activeView.activeFilters.forEach { filter ->
            val columnName = visibleColumns.find { it.id == filter.columnId }?.name ?: "?"

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filterChipLabel(filter, columnName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(Res.drawable.x),
                        contentDescription = null,
                        modifier = Modifier
                            .size(13.dp)
                            .clickable(enabled = !inSelectionMode) { actions.onRemoveDbFilter(block.id, filter) },
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.ViewType
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrTextField
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.bookmark
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.funnel
import emberr.shared.generated.resources.group
import emberr.shared.generated.resources.maximize_2
import emberr.shared.generated.resources.minimize_2
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.slider_h2
import emberr.shared.generated.resources.square
import emberr.shared.generated.resources.square_check
import emberr.shared.generated.resources.square_kanban
import emberr.shared.generated.resources.table
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.transfer_h
import emberr.shared.generated.resources.widget
import emberr.shared.generated.resources.x
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RenameViewSheet(context: DatabaseSheetContext) {
    val state = context.state

    val onConfirmRename: () -> Unit = {
        val viewId = state.renamingViewId
        if (viewId != null && state.textInput.isNotBlank()) {
            val newName = state.textInput.trim()
            state.applyAction { context.actions.onRenameDatabaseView(context.block.id, viewId, newName) }
        }
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = state.textInput,
            onValueChange = { state.textInput = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onSubmit = onConfirmRename
        )
    }
    SheetCancelAndConfirmButtons(
        confirmText = "Save",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = onConfirmRename,
        modifier = Modifier.padding(vertical = 12.dp)
    )

    if (context.block.views.size > 1) {
        SheetDivider()
        SheetMenuRow(
            icon = painterResource(Res.drawable.trash),
            text = "Delete View",
            color = MaterialTheme.colorScheme.error
        ) {
            val viewId = state.renamingViewId ?: return@SheetMenuRow
            state.applyAction { context.actions.onDeleteDatabaseView(context.block.id, viewId) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun SaveAsTemplateSheet(context: DatabaseSheetContext) {
    val state = context.state

    val onConfirmSaveAsTemplate: () -> Unit = {
        val name = state.textInput.trim()
        if (name.isNotEmpty()) {
            state.applyAction { context.actions.onSaveDatabaseAsTemplate(context.block.id, name) }
        }
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = state.textInput,
            onValueChange = { state.textInput = it },
            placeholder = "Template name",
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onSubmit = onConfirmSaveAsTemplate
        )
    }
    SheetCancelAndConfirmButtons(
        confirmText = "Save",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = onConfirmSaveAsTemplate,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
internal fun AddViewSheet(context: DatabaseSheetContext) {
    val state = context.state
    val blockId = context.block.id

    SheetMenuRow(icon = painterResource(Res.drawable.table), text = "Table") {
        state.applyAction { context.actions.onAddDatabaseView(blockId, ViewType.TABLE) }
    }
    SheetMenuRow(icon = painterResource(Res.drawable.square_kanban), text = "Board") {
        state.applyAction { context.actions.onAddDatabaseView(blockId, ViewType.KANBAN) }
    }
    SheetMenuRow(icon = painterResource(Res.drawable.widget), text = "Gallery") {
        state.applyAction { context.actions.onAddDatabaseView(blockId, ViewType.GALLERY) }
    }
}

@Composable
internal fun TableSettingsSheet(context: DatabaseSheetContext) {
    val state = context.state

    SheetMenuRow(icon = painterResource(Res.drawable.funnel), text = "Filter") {
        val firstColumn = context.visibleColumns.firstOrNull() ?: return@SheetMenuRow
        state.textInput = ""
        state.textInputMax = ""
        state.filterOperator = "contains"
        state.openForColumn(firstColumn.id, DatabaseSheet.FILTER)
    }
    SheetMenuRow(icon = painterResource(Res.drawable.bookmark), text = "Save as Template") {
        state.textInput = ""
        state.open(DatabaseSheet.SAVE_AS_TEMPLATE)
    }
    SheetMenuRow(icon = painterResource(Res.drawable.plus), text = "Add View") {
        state.open(DatabaseSheet.ADD_VIEW)
    }

    if (context.activeView.type == ViewType.KANBAN) {
        SheetMenuRow(icon = painterResource(Res.drawable.group), text = "Group By") {
            state.open(DatabaseSheet.GROUP_BY)
        }
    }
    if (context.activeView.type == ViewType.GALLERY) {
        SheetMenuRow(icon = painterResource(Res.drawable.slider_h2), text = "Card Size") {
            state.open(DatabaseSheet.CARD_SIZE)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SortSheet(context: DatabaseSheetContext) {
    val state = context.state
    val actions = context.actions
    val blockId = context.block.id
    val activeSorts = context.activeView.activeSorts
    val sortedColumnIds = activeSorts.map { it.columnId }
    val unsortedColumns = context.visibleColumns.filter { it.id !in sortedColumnIds }

    if (activeSorts.isNotEmpty()) {
        Text(
            text = "Sort order — top layer wins, lower layers break ties",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).sheetSidePadding()
        )

        activeSorts.forEachIndexed { index, rule ->
            val column = context.visibleColumns.find { it.id == rule.columnId } ?: return@forEachIndexed
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).sheetSidePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    column.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { actions.onUpdateDbSort(blockId, column.id, !rule.isAscending) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(if (rule.isAscending) Res.drawable.arrow_up else Res.drawable.arrow_down),
                            null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (rule.isAscending) "Asc" else "Desc",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(Res.drawable.x),
                    "Remove sort layer",
                    modifier = Modifier.size(18.dp).clickable { actions.onUpdateDbSort(blockId, column.id, null) },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        SheetDivider()
    }

    if (unsortedColumns.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth().sheetSidePadding()
        ) {
            unsortedColumns.forEach { column ->
                SuggestionChip(
                    onClick = { actions.onUpdateDbSort(blockId, column.id, true) },
                    label = { Text(column.name, style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(painterResource(Res.drawable.plus), null, modifier = Modifier.size(15.dp)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
            }
        }
    } else if (activeSorts.isNotEmpty()) {
        Text(
            text = "Every column is already in the sort.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp).sheetSidePadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activeSorts.isNotEmpty()) {
            EmberrButtonSecondary(
                text = "Clear all",
                onClick = {
                    sortedColumnIds.forEach { actions.onUpdateDbSort(blockId, it, null) }
                    state.dismissCurrentSheet()
                },
                modifier = Modifier.weight(1f)
            )
            EmberrButtonPrimary(text = "Done", onClick = { state.dismissCurrentSheet() }, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(context: DatabaseSheetContext) {
    val state = context.state
    val activeColumn = context.activeColumn
    val isCheckbox = activeColumn?.type == ColumnType.CHECKBOX
    val isNumber = activeColumn?.type == ColumnType.NUMBER || activeColumn?.type == ColumnType.MONEY
    val isDate = activeColumn?.type == ColumnType.DATE

    SheetSectionLabel("Column", Modifier.padding(top = 4.dp, bottom = 8.dp).sheetSidePadding())

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .sheetSidePadding()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                val index = context.visibleColumns.indexOfFirst { it.id == state.activeColId }
                state.activeColId = context.visibleColumns[(index + 1) % context.visibleColumns.size].id
                state.filterOperator = "contains"
                state.textInput = ""
                state.textInputMax = ""
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeColumn?.name ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                painterResource(Res.drawable.transfer_h),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    SheetDivider(verticalPadding = 22.dp)
    SheetSectionLabel("Condition", Modifier.padding(bottom = 8.dp).sheetSidePadding())

    val operatorOptions = filterConditionsFor(activeColumn?.type)
    if (operatorOptions.none { it.first == state.filterOperator }) {
        state.filterOperator = operatorOptions.first().first
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().sheetSidePadding()
    ) {
        operatorOptions.forEach { (operator, label) ->
            val isSelected = state.filterOperator == operator
            FilterChip(
                selected = isSelected,
                onClick = {
                    state.filterOperator = operator
                    state.textInput = ""
                    state.textInputMax = ""
                },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }

    fun onConfirmFilter() {
        val columnId = state.activeColId ?: return
        val canApply = when {
            isCheckbox -> true
            state.filterOperator in listOf("not_empty", "empty") -> true
            state.filterOperator == "priority" -> state.filterPriority.isNotBlank()
            state.filterOperator == "between" -> state.textInput.isNotBlank() && state.textInputMax.isNotBlank()
            else -> state.textInput.isNotBlank()
        }
        if (!canApply) return

        val operator = state.filterOperator
        val value = when (operator) {
            "priority" -> state.filterPriority.trim()
            "between" -> "${state.textInput.trim()}|${state.textInputMax.trim()}"
            else -> state.textInput.trim()
        }
        state.applyAction { context.actions.onAddDbFilter(context.block.id, columnId, operator, value) }
    }

    val needsTextInput = state.filterOperator in listOf(
        "contains", "equals", "not_equals", "gt", "gte", "lt", "lte", "before", "after", "starts_with", "ends_with"
    )

    if (needsTextInput) {
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.sheetSidePadding()) {
            EmberrTextField(
                value = state.textInput,
                onValueChange = { state.textInput = it },
                placeholder = if (isNumber) "Enter number…" else "Enter value…",
                modifier = Modifier.fillMaxWidth(),
                onSubmit = ::onConfirmFilter
            )
        }
    }

    if (state.filterOperator == "between") {
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.sheetSidePadding()) {
                EmberrTextField(
                    value = state.textInput,
                    onValueChange = { state.textInput = it },
                    placeholder = if (isDate) "Start" else "Min",
                    modifier = Modifier.weight(1f),
                    onSubmit = ::onConfirmFilter
                )
                EmberrTextField(
                    value = state.textInputMax,
                    onValueChange = { state.textInputMax = it },
                    placeholder = if (isDate) "End" else "Max",
                    modifier = Modifier.weight(1f),
                    onSubmit = ::onConfirmFilter
                )
            }
        }
    }

    if (state.filterOperator == "priority") {
        Spacer(Modifier.height(10.dp))
        SheetSectionLabel("Priority level", Modifier.padding(bottom = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.sheetSidePadding()
        ) {
            PRIORITY_LEVELS.forEach { priority ->
                val isSelected = state.filterPriority == priority
                val chipColor = priorityAccentColor(priority) ?: MaterialTheme.colorScheme.outline
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        state.filterPriority = priority
                        state.textInput = priority
                    },
                    label = { Text(priority, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) chipColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }

    SheetCancelAndConfirmButtons(
        confirmText = "Apply",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = ::onConfirmFilter,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
    )
}

@Composable
internal fun GroupBySheet(context: DatabaseSheetContext) {
    val state = context.state
    val activeView = context.activeView
    val eligibleColumns = context.visibleColumns.filter {
        it.type == ColumnType.CHECKBOX || it.type == ColumnType.STATUS
    }
    val selectedGroupColumn = eligibleColumns.find { it.id == activeView.groupByColumnId }

    Column(modifier = Modifier.fillMaxWidth()) {
        val isNoneSelected = activeView.groupByColumnId == null
        SheetMenuRow(
            icon = painterResource(Res.drawable.x),
            text = "None",
            color = if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            selected = isNoneSelected
        ) { state.applyAction { context.actions.onUpdateDbGroupBy(context.block.id, null) } }

        eligibleColumns.forEach { column ->
            val isSelected = activeView.groupByColumnId == column.id
            val icon = when (column.type) {
                ColumnType.CHECKBOX -> painterResource(Res.drawable.square_check)
                ColumnType.STATUS -> painterResource(Res.drawable.check_square)
                else -> rememberVectorPainter(Icons.AutoMirrored.Filled.Subject)
            }
            SheetMenuRow(
                icon = icon,
                text = column.name,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                selected = isSelected
            ) { state.applyAction { context.actions.onUpdateDbGroupBy(context.block.id, column.id) } }
        }

        if (eligibleColumns.isEmpty()) {
            Text(
                text = "Add a Checkbox or Status column to group by.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (selectedGroupColumn != null) {
            SheetDivider()
            SheetSectionLabel("Visible boards", Modifier.padding(bottom = 4.dp))
            BoardVisibilityAndOrderList(context, selectedGroupColumn)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BoardVisibilityAndOrderList(
    context: DatabaseSheetContext,
    groupColumn: DatabaseColumn
) {
    val activeView = context.activeView
    val defaultBucketKeys = remember(groupColumn) { bucketKeysFor(groupColumn) }
    val orderedKeys = remember(activeView.id, defaultBucketKeys, activeView.groupOrder) {
        mutableStateListOf(*orderedBucketKeys(defaultBucketKeys, activeView.groupOrder).toTypedArray())
    }
    var draggedBucket by remember { mutableStateOf<String?>(null) }
    var dragPointerY by remember { mutableStateOf(0f) }
    val rowBoundsInWindow = remember { mutableStateMapOf<String, Rect>() }

    orderedKeys.forEach { bucketName ->
        key(bucketName) {
            val isVisible = bucketName !in activeView.hiddenGroups
            val isDragged = draggedBucket == bucketName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { rowBoundsInWindow[bucketName] = it.boundsInWindow() }
                    .graphicsLayer { alpha = if (isDragged) 0.5f else 1f }
                    .clickable {
                        context.actions.onToggleKanbanGroupVisibility(
                            context.block.id,
                            activeView.id,
                            bucketName,
                            isVisible
                        )
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Reorder $bucketName",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(18.dp)
                            .pointerInput(bucketName) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedBucket = bucketName
                                        dragPointerY = rowBoundsInWindow[bucketName]?.center?.y ?: 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragPointerY += dragAmount.y
                                        val hovered = rowBoundsInWindow.entries
                                            .firstOrNull { (_, rect) -> dragPointerY in rect.top..rect.bottom }
                                            ?.key
                                        if (hovered != null && hovered != bucketName) {
                                            val from = orderedKeys.indexOf(bucketName)
                                            val to = orderedKeys.indexOf(hovered)
                                            if (from != -1 && to != -1) {
                                                orderedKeys.add(to, orderedKeys.removeAt(from))
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedBucket = null
                                        context.actions.onReorderKanbanGroups(
                                            context.block.id,
                                            activeView.id,
                                            orderedKeys.toList()
                                        )
                                    },
                                    onDragCancel = { draggedBucket = null }
                                )
                            }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        bucketName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(checked = isVisible, onCheckedChange = null, modifier = Modifier.scale(0.8f))
            }
        }
    }
}

@Composable
internal fun CardSizeSheet(context: DatabaseSheetContext) {
    val options = listOf(
        Triple(GalleryCardSize.SMALL, "Small", painterResource(Res.drawable.minimize_2)),
        Triple(GalleryCardSize.MEDIUM, "Medium", painterResource(Res.drawable.square)),
        Triple(GalleryCardSize.LARGE, "Large", painterResource(Res.drawable.maximize_2))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        options.forEach { (size, label, icon) ->
            val isSelected = context.activeView.galleryCardSize == size
            SheetMenuRow(
                icon = icon,
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                selected = isSelected
            ) { context.state.applyAction { context.actions.onUpdateDbGalleryCardSize(context.block.id, size) } }
        }
    }
    Spacer(Modifier.height(8.dp))
}

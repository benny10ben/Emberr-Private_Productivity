package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.activeView
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.DatabaseSort
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.x
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DatabaseSortMenu(
    expanded: Boolean,
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    onDismiss: () -> Unit
) {
    val view = block.activeView()
    val sorts = view.sorts
    val columnTargets = listOf<DatabaseColumnTarget>(DatabaseColumnTarget.NotesTitle) + block.columns
    val unsortedColumns = columnTargets.filter { target -> sorts.none { it.target == target } }

    fun setSorts(newSorts: List<DatabaseSort>) = editor.setSorts(block.id, view.id, newSorts)

    DatabaseMenu(expanded = expanded, title = "Sort", onDismiss = onDismiss) { _ ->
        if (sorts.isEmpty()) {
            DatabaseMenuSectionLabel(text = "Sort by")
            DatabaseSortColumnChoices(
                block = block,
                columns = columnTargets,
                selectedColumn = null,
                onPick = { target -> setSorts(listOf(DatabaseSort(target = target))) }
            )
        } else {
            DatabaseActiveSorts(
                block = block,
                sorts = sorts,
                columnTargets = columnTargets,
                unsortedColumns = unsortedColumns,
                setSorts = ::setSorts
            )
        }
    }
}

@Composable
private fun DatabaseActiveSorts(
    block: DatabaseBlock,
    sorts: List<DatabaseSort>,
    columnTargets: List<DatabaseColumnTarget>,
    unsortedColumns: List<DatabaseColumnTarget>,
    setSorts: (List<DatabaseSort>) -> Unit
) {
    sorts.forEachIndexed { position, sort ->
        DatabaseSortEditor(
            block = block,
            sort = sort,
            columnsToPick = columnTargets.filter { it == sort.target || it in unsortedColumns },
            onChange = { changedSort -> setSorts(sorts.toMutableList().apply { set(position, changedSort) }) },
            onRemove = { setSorts(sorts - sort) }
        )
    }

    DatabaseMenuSectionDivider()
    if (unsortedColumns.isNotEmpty()) {
        DatabaseMenuLayer(
            title = "Add sort",
            anchor = { openLayer ->
                DatabaseMenuOption(
                    label = "Add sort",
                    icon = { DatabaseOptionIcon(Res.drawable.plus) },
                    onClick = openLayer
                )
            }
        ) { closeLayerAnd ->
            DatabaseSortColumnChoices(
                block = block,
                columns = unsortedColumns,
                selectedColumn = null,
                onPick = { target -> closeLayerAnd { setSorts(sorts + DatabaseSort(target = target)) } }
            )
        }
    }
    DatabaseMenuOption(
        label = "Remove all sorts",
        icon = { DatabaseOptionIcon(Res.drawable.x, tint = MaterialTheme.colorScheme.error) },
        labelColor = MaterialTheme.colorScheme.error,
        onClick = { setSorts(emptyList()) }
    )
}

@Composable
private fun DatabaseSortEditor(
    block: DatabaseBlock,
    sort: DatabaseSort,
    columnsToPick: List<DatabaseColumnTarget>,
    onChange: (DatabaseSort) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DatabaseMenuRowInset, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DatabaseMenuLayer(
            title = "Sort by",
            anchor = { openLayer -> DatabaseChoiceButton(text = block.labelOf(sort.target), onClick = openLayer) }
        ) { closeLayerAnd ->
            DatabaseSortColumnChoices(
                block = block,
                columns = columnsToPick,
                selectedColumn = sort.target,
                onPick = { target -> closeLayerAnd { onChange(sort.copy(target = target)) } }
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        DatabaseMenuLayer(
            title = "Order",
            anchor = { openLayer ->
                DatabaseChoiceButton(text = if (sort.isDescending) "Descending" else "Ascending", onClick = openLayer)
            }
        ) { closeLayerAnd ->
            DatabaseMenuOption(
                label = "Ascending",
                isSelected = !sort.isDescending,
                icon = { DatabaseOptionIcon(Res.drawable.arrow_up) },
                onClick = { closeLayerAnd { onChange(sort.copy(isDescending = false)) } }
            )
            DatabaseMenuOption(
                label = "Descending",
                isSelected = sort.isDescending,
                icon = { DatabaseOptionIcon(Res.drawable.arrow_down) },
                onClick = { closeLayerAnd { onChange(sort.copy(isDescending = true)) } }
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(Res.drawable.x),
            contentDescription = "Remove sort",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(4.dp)
                .size(14.dp)
        )
    }
}

@Composable
private fun DatabaseSortColumnChoices(
    block: DatabaseBlock,
    columns: List<DatabaseColumnTarget>,
    selectedColumn: DatabaseColumnTarget?,
    onPick: (DatabaseColumnTarget) -> Unit
) {
    columns.forEach { target ->
        DatabaseMenuOption(
            label = block.labelOf(target),
            isSelected = target == selectedColumn,
            icon = { DatabaseOptionIcon(block.iconOf(target)) },
            onClick = { onPick(target) }
        )
    }
}

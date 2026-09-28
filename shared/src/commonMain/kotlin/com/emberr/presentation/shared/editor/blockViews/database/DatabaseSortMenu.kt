package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.DatabaseSort
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.x

@Composable
internal fun DatabaseSortMenu(
    expanded: Boolean,
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    onDismiss: () -> Unit
) {
    val columnTargets = listOf<DatabaseColumnTarget>(DatabaseColumnTarget.NotesTitle) + block.columns
    val sort = block.sort

    DatabaseMenu(expanded = expanded, title = "Sort", onDismiss = onDismiss) { _ ->
        DatabaseMenuSectionLabel(text = "Sort by")
        columnTargets.forEach { target ->
            DatabaseMenuOption(
                label = block.labelOf(target),
                isSelected = sort?.target == target,
                icon = { DatabaseOptionIcon(block.iconOf(target)) },
                onClick = { editor.setSort(block.id, DatabaseSort(target = target, isDescending = sort?.isDescending ?: false)) }
            )
        }

        if (sort != null) {
            DatabaseMenuSectionDivider()
            DatabaseMenuSectionLabel(text = "Order")
            DatabaseMenuOption(
                label = "Ascending",
                isSelected = !sort.isDescending,
                icon = { DatabaseOptionIcon(Res.drawable.arrow_up) },
                onClick = { editor.setSort(block.id, sort.copy(isDescending = false)) }
            )
            DatabaseMenuOption(
                label = "Descending",
                isSelected = sort.isDescending,
                icon = { DatabaseOptionIcon(Res.drawable.arrow_down) },
                onClick = { editor.setSort(block.id, sort.copy(isDescending = true)) }
            )
            DatabaseMenuOption(
                label = "Remove sort",
                icon = { DatabaseOptionIcon(Res.drawable.x, tint = MaterialTheme.colorScheme.error) },
                labelColor = MaterialTheme.colorScheme.error,
                onClick = { editor.setSort(block.id, null) }
            )
        }
    }
}

package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.activeView
import com.emberr.domain.database.allViews
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.kanban
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.table
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.widget
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal fun DatabaseViewType.iconResource(): DrawableResource = when (this) {
    DatabaseViewType.TABLE -> Res.drawable.table
    DatabaseViewType.GALLERY -> Res.drawable.widget
    DatabaseViewType.BOARD -> Res.drawable.kanban
}

@Composable
internal fun DatabaseViewTabs(
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    inSelectionMode: Boolean,
    runAfterKeyboardCloses: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val views = block.allViews()
    val activeViewId = block.activeView().id

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        views.forEach { view ->
            key(view.id) {
                DatabaseViewTab(
                    block = block,
                    view = view,
                    isActive = view.id == activeViewId,
                    canDelete = views.size > 1,
                    enabled = !inSelectionMode,
                    editor = editor,
                    runAfterKeyboardCloses = runAfterKeyboardCloses
                )
            }
        }
        DatabaseAddViewButton(block = block, enabled = !inSelectionMode, editor = editor, runAfterKeyboardCloses = runAfterKeyboardCloses)
    }
}

@Composable
private fun DatabaseViewTab(
    block: DatabaseBlock,
    view: DatabaseView,
    isActive: Boolean,
    canDelete: Boolean,
    enabled: Boolean,
    editor: DatabaseBlockEditor,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
                .clickable(enabled = enabled) {
                    if (isActive) runAfterKeyboardCloses { showMenu = true } else editor.showView(block.id, view.id)
                }
                .padding(horizontal = HeaderButtonInnerPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(view.type.iconResource()), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = view.name.ifBlank { view.type.label }, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1)
        }

        DatabaseMenu(expanded = showMenu, title = view.name.ifBlank { view.type.label }, onDismiss = { showMenu = false }) { closeAnd ->
            DatabaseMenuLayer(
                title = "Rename view",
                anchor = { openLayer ->
                    DatabaseMenuOption(label = "Rename", icon = { DatabaseOptionIcon(Res.drawable.pen) }, onClick = openLayer)
                }
            ) { closeLayerAnd ->
                DatabaseRenameViewPage(
                    currentName = view.name,
                    onCancel = { closeLayerAnd { } },
                    onRename = { name -> closeLayerAnd { closeAnd { editor.renameView(block.id, view.id, name) } } }
                )
            }
            if (canDelete) {
                DatabaseMenuOption(
                    label = "Delete view",
                    icon = { DatabaseOptionIcon(Res.drawable.trash, tint = MaterialTheme.colorScheme.error) },
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = { closeAnd { editor.deleteView(block.id, view.id) } }
                )
            }
        }
    }
}

@Composable
private fun DatabaseRenameViewPage(currentName: String, onCancel: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    val saveName = { if (name.isNotBlank()) onRename(name.trim()) }

    DatabaseMenuContent {
        EmberrTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "View name",
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            onSubmit = saveName
        )
    }
    DatabaseMenuButtons(cancelText = "Cancel", onCancel = onCancel, confirmText = "Save", onConfirm = saveName)
}

@Composable
private fun DatabaseAddViewButton(
    block: DatabaseBlock,
    enabled: Boolean,
    editor: DatabaseBlockEditor,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Icon(
            painter = painterResource(Res.drawable.plus),
            contentDescription = "Add view",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { runAfterKeyboardCloses { showMenu = true } }
                .padding(6.dp)
                .size(16.dp)
        )

        DatabaseMenu(expanded = showMenu, title = "Add view", onDismiss = { showMenu = false }) { closeAnd ->
            DatabaseViewType.entries.forEach { type ->
                DatabaseMenuOption(
                    label = type.label,
                    icon = { DatabaseOptionIcon(type.iconResource()) },
                    onClick = { closeAnd { editor.addView(block.id, type) } }
                )
            }
        }
    }
}

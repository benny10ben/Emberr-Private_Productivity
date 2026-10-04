package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseTemplateRepeat
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.doc_text
import emberr.shared.generated.resources.ellipsis
import emberr.shared.generated.resources.file_text
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.refresh_cw
import emberr.shared.generated.resources.star
import emberr.shared.generated.resources.trash
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DatabaseTemplateMenu(
    expanded: Boolean,
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    onOpenTemplate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val templates by remember(editor) { editor.databaseTemplates }.collectAsState(initial = emptyList())
    val defaultTemplateId = block.defaultTemplateId?.takeIf { templateId -> templates.any { it.noteId == templateId } }
    var searchQuery by remember(expanded) { mutableStateOf("") }
    val matchingTemplates = templates.filter { templateLabel(it).contains(searchQuery.trim(), ignoreCase = true) }

    DatabaseMenu(expanded = expanded, title = "Templates", onDismiss = onDismiss) { closeAnd ->
        DatabaseMenuContent {
            EmberrTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search templates...",
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }

        if (searchQuery.isBlank()) {
            DatabaseTemplateOption(
                label = "Empty",
                icon = Res.drawable.doc_text,
                isDefault = defaultTemplateId == null,
                onUse = { closeAnd { editor.addRowFromTemplate(block.id, null) } },
                onSetDefault = { editor.setDefaultTemplate(block.id, null) },
                repeat = null,
                onRepeatChange = null,
                onEdit = null,
                onDelete = null
            )
        } else if (matchingTemplates.isEmpty()) {
            DatabaseMenuMessage(text = "No templates match \"${searchQuery.trim()}\"", color = MaterialTheme.colorScheme.outline)
        }

        matchingTemplates.forEach { template ->
            key(template.noteId) {
                DatabaseTemplateOption(
                    label = templateLabel(template),
                    icon = Res.drawable.file_text,
                    isDefault = template.noteId == defaultTemplateId,
                    onUse = { closeAnd { editor.addRowFromTemplate(block.id, template.noteId) } },
                    onSetDefault = { editor.setDefaultTemplate(block.id, template.noteId) },
                    repeat = block.repeatingTemplates[template.noteId],
                    onRepeatChange = { repeat -> editor.setTemplateRepeat(block.id, template.noteId, repeat) },
                    onEdit = { closeAnd { onOpenTemplate(template.noteId) } },
                    onDelete = { editor.deleteTemplate(template.noteId) }
                )
            }
        }

        DatabaseMenuOption(
            label = "New template",
            icon = { DatabaseOptionIcon(Res.drawable.plus) },
            onClick = { closeAnd { editor.createTemplate(block.id, onOpenTemplate) } }
        )
    }
}

private fun templateLabel(template: NoteMetadataEntity): String = template.title.ifBlank { "Untitled" }

@Composable
private fun DatabaseTemplateOption(
    label: String,
    icon: DrawableResource,
    isDefault: Boolean,
    onUse: () -> Unit,
    onSetDefault: () -> Unit,
    repeat: DatabaseTemplateRepeat?,
    onRepeatChange: ((DatabaseTemplateRepeat?) -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    DatabaseMenuLayer(
        title = label,
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = label,
                icon = { DatabaseOptionIcon(icon) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (repeat != null) {
                            Icon(
                                painter = painterResource(Res.drawable.refresh_cw),
                                contentDescription = repeat.summary(),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 10.dp).size(14.dp)
                            )
                        }
                        if (isDefault) {
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                        Icon(
                            painter = painterResource(Res.drawable.ellipsis),
                            contentDescription = "$label options",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = openLayer)
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                },
                onClick = onUse
            )
        }
    ) { closeLayerAnd ->
        DatabaseMenuOption(
            label = if (isDefault) "Default for new rows" else "Set as default",
            isSelected = isDefault,
            icon = { DatabaseOptionIcon(Res.drawable.star) },
            onClick = { closeLayerAnd(onSetDefault) }
        )
        if (onRepeatChange != null) {
            DatabaseMenuLayer(
                title = "Repeat",
                anchor = { openRepeatLayer ->
                    DatabaseMenuOption(
                        label = "Repeat",
                        icon = { DatabaseOptionIcon(Res.drawable.refresh_cw) },
                        trailing = {
                            Text(
                                text = repeat?.summary() ?: "Off",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        },
                        onClick = openRepeatLayer
                    )
                }
            ) { _ ->
                DatabaseTemplateRepeatChoices(repeat = repeat, onRepeatChange = onRepeatChange)
            }
        }
        if (onEdit != null) {
            DatabaseMenuOption(
                label = "Edit",
                icon = { DatabaseOptionIcon(Res.drawable.pen) },
                onClick = { closeLayerAnd(onEdit) }
            )
        }
        if (onDelete != null) {
            DatabaseMenuOption(
                label = "Delete",
                icon = { DatabaseOptionIcon(Res.drawable.trash, tint = MaterialTheme.colorScheme.error) },
                labelColor = MaterialTheme.colorScheme.error,
                onClick = { closeLayerAnd(onDelete) }
            )
        }
    }
}

package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.activeView
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCardSize
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.labelOf
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.eye3
import emberr.shared.generated.resources.ghost_smile
import emberr.shared.generated.resources.hash
import emberr.shared.generated.resources.image
import emberr.shared.generated.resources.maximize_2

@Composable
internal fun DatabaseSettingsMenu(
    expanded: Boolean,
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    onDismiss: () -> Unit
) {
    val activeView = block.activeView()
    val hiddenColumnCount = block.columns.count { it.columnKey in activeView.hiddenColumnKeys }

    DatabaseMenu(expanded = expanded, title = "Database settings", onDismiss = onDismiss) { _ ->
        DatabaseMenuSectionLabel(text = "${activeView.name.ifBlank { activeView.type.label }} view")
        DatabaseMenuOption(
            label = "Show icon",
            icon = { DatabaseOptionIcon(Res.drawable.ghost_smile) },
            trailing = { DatabaseSettingSwitch(isOn = activeView.showsIcon) },
            onClick = { editor.setViewShowsIcon(block.id, activeView.id, !activeView.showsIcon) }
        )
        if (activeView.type == DatabaseViewType.GALLERY) {
            DatabaseMenuOption(
                label = "Show cover image",
                icon = { DatabaseOptionIcon(Res.drawable.image) },
                trailing = { DatabaseSettingSwitch(isOn = activeView.showsCoverImage) },
                onClick = { editor.setViewShowsCoverImage(block.id, activeView.id, !activeView.showsCoverImage) }
            )
            DatabaseMenuLayer(
                title = "Card size",
                opensAtTapOnDesktop = true,
                anchor = { openLayer ->
                    DatabaseMenuOption(
                        label = "Card size",
                        icon = { DatabaseOptionIcon(Res.drawable.maximize_2) },
                        trailing = {
                            Text(
                                text = activeView.cardSize.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        },
                        onClick = openLayer
                    )
                }
            ) { closeLayerAnd ->
                DatabaseCardSize.entries.forEach { cardSize ->
                    DatabaseMenuOption(
                        label = cardSize.label,
                        isSelected = cardSize == activeView.cardSize,
                        onClick = { closeLayerAnd { editor.setViewCardSize(block.id, activeView.id, cardSize) } }
                    )
                }
            }
        }

        DatabaseMenuLayer(
            title = "Show / hide columns",
            opensAtTapOnDesktop = true,
            anchor = { openLayer ->
                DatabaseMenuOption(
                    label = "Show / hide columns",
                    icon = { DatabaseOptionIcon(Res.drawable.eye3) },
                    trailing = if (hiddenColumnCount == 0) null else {
                        {
                            Text(
                                text = "$hiddenColumnCount hidden",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        }
                    },
                    onClick = openLayer
                )
            }
        ) { _ ->
            if (block.columns.isEmpty()) {
                DatabaseMenuMessage(text = "No columns to show or hide yet", color = MaterialTheme.colorScheme.outline)
            }
            block.columns.forEach { column ->
                key(column.columnKey) {
                    val isShown = column.columnKey !in activeView.hiddenColumnKeys
                    DatabaseMenuOption(
                        label = block.labelOf(column),
                        icon = { DatabaseOptionIcon(block.iconOf(column)) },
                        trailing = { DatabaseSettingSwitch(isOn = isShown) },
                        onClick = { editor.setColumnShown(block.id, activeView.id, column, !isShown) }
                    )
                }
            }
        }

        DatabaseMenuSectionLabel(text = "All views")
        DatabaseMenuOption(
            label = "Row count",
            icon = { DatabaseOptionIcon(Res.drawable.hash) },
            trailing = { DatabaseSettingSwitch(isOn = block.showsRowCount) },
            onClick = { editor.setShowsRowCount(block.id, !block.showsRowCount) }
        )
    }
}

@Composable
private fun DatabaseSettingSwitch(isOn: Boolean) {
    Box(modifier = Modifier.size(width = 40.dp, height = 24.dp), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = isOn,
                onCheckedChange = null,
                modifier = Modifier.scale(0.75f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

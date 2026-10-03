package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColorRule
import com.emberr.domain.model.DatabaseColorRuleTarget
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.valueTypeOf
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.formatPropertyDate
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.palette
import emberr.shared.generated.resources.plus

@Composable
internal fun DatabaseColorRulesOption(block: DatabaseBlock, editor: DatabaseBlockEditor) {
    DatabaseMenuLayer(
        title = "Color rules",
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = "Color rules",
                icon = { DatabaseOptionIcon(Res.drawable.palette) },
                trailing = if (block.colorRules.isEmpty()) null else {
                    {
                        Text(
                            text = block.colorRules.size.toString(),
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
        if (block.colorRules.isEmpty()) {
            DatabaseMenuMessage(
                text = "Color the rows or cells that match a condition, like tasks that are overdue.",
                color = MaterialTheme.colorScheme.outline
            )
        }
        block.colorRules.forEach { rule ->
            key(rule.id) {
                DatabaseColorRuleOption(block = block, rule = rule, editor = editor)
            }
        }
        DatabaseMenuLayer(
            title = "Add rule",
            anchor = { openLayer ->
                DatabaseMenuOption(label = "Add rule", icon = { DatabaseOptionIcon(Res.drawable.plus) }, onClick = openLayer)
            }
        ) { closeLayerAnd ->
            DatabaseMenuSectionLabel(text = "When this property matches")
            (listOf<DatabaseColumnTarget>(DatabaseColumnTarget.NotesTitle) + block.columns).forEach { column ->
                DatabaseMenuOption(
                    label = block.labelOf(column),
                    icon = { DatabaseOptionIcon(block.iconOf(column)) },
                    onClick = { closeLayerAnd { editor.addColorRule(block.id, column) } }
                )
            }
        }
    }
}

@Composable
private fun DatabaseColorRuleOption(block: DatabaseBlock, rule: DatabaseColorRule, editor: DatabaseBlockEditor) {
    fun changeRule(change: (DatabaseColorRule) -> DatabaseColorRule) = editor.changeColorRule(block.id, rule.id, change)

    DatabaseMenuLayer(
        title = "Color rule",
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = block.summaryOf(rule.condition),
                icon = { DatabaseColorRuleSwatch(rule) },
                onClick = openLayer
            )
        }
    ) { closeLayerAnd ->
        DatabaseMenuSectionLabel(text = "When")
        DatabaseFilterEditor(
            block = block,
            filter = rule.condition,
            editor = editor,
            onChange = { change -> changeRule { it.copy(condition = change(it.condition)) } },
            onRemove = { closeLayerAnd { editor.removeColorRule(block.id, rule.id) } },
            removeLabel = "Delete rule"
        )

        DatabaseMenuSectionDivider()
        DatabaseMenuSectionLabel(text = "Color")
        DatabaseColorRuleTarget.entries.forEach { target ->
            DatabaseMenuOption(
                label = target.label,
                isSelected = target == rule.target,
                onClick = { changeRule { it.copy(target = target) } }
            )
        }

        DatabaseMenuSectionDivider()
        DatabaseMenuSectionLabel(text = "Text color")
        DatabaseStyleColorChoices(
            selectedColorName = rule.textColorName,
            colorOf = { databaseTextColorNamed(it.storageName) },
            onColorChosen = { colorName -> changeRule { it.copy(textColorName = colorName) } }
        )

        DatabaseMenuSectionDivider()
        DatabaseMenuSectionLabel(text = "Background color")
        DatabaseStyleColorChoices(
            selectedColorName = rule.backgroundColorName,
            colorOf = { databaseBackgroundColorNamed(it.storageName) },
            onColorChosen = { colorName -> changeRule { it.copy(backgroundColorName = colorName) } }
        )
    }
}

@Composable
private fun DatabaseColorRuleSwatch(rule: DatabaseColorRule) {
    val color = databaseBackgroundColorNamed(rule.backgroundColorName)
        ?: databaseTextColorNamed(rule.textColorName)
        ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun DatabaseBlock.summaryOf(condition: DatabaseFilter): String {
    val valueText = filterValueText(condition, valueTypeOf(condition.target) ?: PropertyValueType.TEXT)
    return listOfNotNull(labelOf(condition.target), condition.condition.label, valueText).joinToString(" ")
}

private fun filterValueText(filter: DatabaseFilter, valueType: PropertyValueType): String? = when {
    !filter.condition.needsValue -> null
    valueType.holdsDate && filter.condition == DatabaseFilterCondition.IS_WITHIN -> filter.dateRange?.label?.lowercase()
    valueType.holdsDate -> filter.relativeDate?.label?.lowercase() ?: filter.date?.let { formatPropertyDate(it) }
    valueType.holdsTags -> filter.tagName
    else -> filter.text.trim().takeIf { it.isNotEmpty() }?.let { "\"$it\"" }
}

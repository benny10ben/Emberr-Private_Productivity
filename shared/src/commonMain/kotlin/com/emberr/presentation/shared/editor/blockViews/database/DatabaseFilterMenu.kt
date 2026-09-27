package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.filterConditionsFor
import com.emberr.domain.database.withTarget
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.tagPoolKey
import com.emberr.domain.model.valueTypeOf
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.PropertyTagChip
import com.emberr.presentation.shared.editor.blockViews.formatPropertyDate
import com.emberr.presentation.shared.editor.blockViews.propertyTagColor
import com.emberr.ui.theme.LocalAppIsDark
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.x
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Instant

private val DesktopFilterMenuWidth = 320.dp

private fun DatabaseBlockEditor.setFilterColumn(block: DatabaseBlock, filterId: String, target: DatabaseColumnTarget) {
    val valueType = block.valueTypeOf(target) ?: PropertyValueType.TEXT
    changeFilter(block.id, filterId) { it.withTarget(target, valueType) }
}

private fun DatabaseBlockEditor.setFilterCondition(blockId: String, filterId: String, condition: DatabaseFilterCondition) {
    changeFilter(blockId, filterId) { it.copy(condition = condition) }
}

private fun DatabaseBlockEditor.setFilterOption(blockId: String, filterId: String, tagName: String) {
    changeFilter(blockId, filterId) { it.copy(tagName = tagName) }
}

@Composable
internal fun DatabaseFilterMenu(
    expanded: Boolean,
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    onDismiss: () -> Unit
) {
    val columnTargets = listOf<DatabaseColumnTarget>(DatabaseColumnTarget.NotesTitle) + block.columns

    DatabaseMenu(expanded = expanded, title = "Filter", onDismiss = onDismiss, desktopWidth = DesktopFilterMenuWidth) { _ ->
        val filters = block.filters
        if (filters.isEmpty()) {
            DatabaseMenuMessage(text = "No filters yet", color = MaterialTheme.colorScheme.outline)
        }

        filters.forEachIndexed { index, filter ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = DatabaseMenuRowInset, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            }
            DatabaseFilterEditor(block = block, filter = filter, columnTargets = columnTargets, editor = editor)
        }

        DatabaseMenuLayer(
            title = "Filter by",
            anchor = { openLayer ->
                DatabaseMenuOption(
                    label = "Add filter",
                    icon = { DatabaseOptionIcon(Res.drawable.plus) },
                    onClick = openLayer
                )
            }
        ) { closeLayerAnd ->
            DatabaseColumnChoices(
                block = block,
                targets = columnTargets,
                selectedTarget = null,
                onPick = { target -> closeLayerAnd { editor.addFilter(block.id, target) } }
            )
        }
    }
}

@Composable
private fun DatabaseFilterEditor(
    block: DatabaseBlock,
    filter: DatabaseFilter,
    columnTargets: List<DatabaseColumnTarget>,
    editor: DatabaseBlockEditor
) {
    val valueType = block.valueTypeOf(filter.target) ?: PropertyValueType.TEXT
    val columnLabel = block.labelOf(filter.target)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DatabaseMenuRowInset, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DatabaseMenuLayer(
                title = "Filter by",
                anchor = { openLayer ->
                    DatabaseFilterChoiceButton(text = columnLabel, icon = block.iconOf(filter.target), onClick = openLayer)
                }
            ) { closeLayerAnd ->
                DatabaseColumnChoices(
                    block = block,
                    targets = columnTargets,
                    selectedTarget = filter.target,
                    onPick = { target -> closeLayerAnd { editor.setFilterColumn(block, filter.id, target) } }
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            DatabaseMenuLayer(
                title = columnLabel,
                anchor = { openLayer ->
                    DatabaseFilterChoiceButton(text = filter.condition.label, icon = null, onClick = openLayer)
                }
            ) { closeLayerAnd ->
                DatabaseConditionChoices(
                    valueType = valueType,
                    selectedCondition = filter.condition,
                    onPick = { condition -> closeLayerAnd { editor.setFilterCondition(block.id, filter.id, condition) } }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(Res.drawable.x),
                contentDescription = "Remove filter",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { editor.removeFilter(block.id, filter.id) }
                    .padding(4.dp)
                    .size(14.dp)
            )
        }

        if (filter.condition.needsValue) {
            Spacer(modifier = Modifier.height(8.dp))
            when {
                valueType.holdsDate -> DatabaseFilterDateValue(
                    date = filter.date,
                    onDateChosen = { date -> editor.changeFilter(block.id, filter.id) { it.copy(date = date) } }
                )
                valueType.holdsTags -> DatabaseMenuLayer(
                    title = columnLabel,
                    anchor = { openLayer -> DatabaseFilterOptionValue(tagName = filter.tagName, onClick = openLayer) }
                ) { closeLayerAnd ->
                    DatabaseOptionChoices(
                        filter = filter,
                        editor = editor,
                        onPick = { tagName -> closeLayerAnd { editor.setFilterOption(block.id, filter.id, tagName) } }
                    )
                }
                else -> EmberrTextField(
                    value = filter.text,
                    onValueChange = { text -> editor.changeFilter(block.id, filter.id) { it.copy(text = text) } },
                    placeholder = "Type a value",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DatabaseFilterChoiceButton(text: String, icon: DrawableResource?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun DatabaseFilterDateValue(date: LocalDate?, onDateChosen: (LocalDate) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    val timeZone = TimeZone.currentSystemDefault()

    DatabaseFilterValueBox(onClick = { showDatePicker = true }) {
        Text(
            text = date?.let { formatPropertyDate(it) } ?: "Pick a date",
            style = MaterialTheme.typography.bodyLarge,
            color = if (date == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
        )
    }

    if (showDatePicker) {
        MinimalDatePickerDialog(
            initialTimestamp = date?.atStartOfDayIn(timeZone)?.toEpochMilliseconds(),
            onDismiss = { showDatePicker = false },
            onConfirm = { selectedMillis ->
                onDateChosen(Instant.fromEpochMilliseconds(selectedMillis).toLocalDateTime(timeZone).date)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun DatabaseFilterOptionValue(tagName: String?, onClick: () -> Unit) {
    DatabaseFilterValueBox(onClick = onClick) {
        if (tagName == null) {
            Text(text = "Pick an option", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        } else {
            PropertyTagChip(tagName = tagName, textStyle = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DatabaseFilterValueBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        content()
    }
}

@Composable
private fun DatabaseColumnChoices(
    block: DatabaseBlock,
    targets: List<DatabaseColumnTarget>,
    selectedTarget: DatabaseColumnTarget?,
    onPick: (DatabaseColumnTarget) -> Unit
) {
    targets.forEach { target ->
        DatabaseMenuOption(
            label = block.labelOf(target),
            isSelected = target == selectedTarget,
            icon = { DatabaseOptionIcon(block.iconOf(target)) },
            onClick = { onPick(target) }
        )
    }
}

@Composable
private fun DatabaseConditionChoices(
    valueType: PropertyValueType,
    selectedCondition: DatabaseFilterCondition,
    onPick: (DatabaseFilterCondition) -> Unit
) {
    filterConditionsFor(valueType).forEach { condition ->
        DatabaseMenuOption(
            label = condition.label,
            isSelected = condition == selectedCondition,
            onClick = { onPick(condition) }
        )
    }
}

@Composable
private fun DatabaseOptionChoices(
    filter: DatabaseFilter,
    editor: DatabaseBlockEditor,
    onPick: (String) -> Unit
) {
    val tagPoolKey = filter.target.tagPoolKey
    val savedTags by remember(tagPoolKey) {
        if (tagPoolKey == null) flowOf(emptyList()) else editor.savedTagsOf(tagPoolKey)
    }.collectAsState(initial = emptyList())
    val isDarkTheme = LocalAppIsDark.current
    val tagNames = (savedTags.map { it.name } + listOfNotNull(filter.tagName)).distinctBy { it.lowercase() }

    if (tagNames.isEmpty()) {
        DatabaseMenuMessage(text = "No options saved yet", color = MaterialTheme.colorScheme.outline)
    }
    tagNames.forEach { tagName ->
        DatabaseMenuOption(
            label = tagName,
            isSelected = tagName.equals(filter.tagName, ignoreCase = true),
            icon = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(propertyTagColor(tagName, isDarkTheme))
                )
            },
            onClick = { onPick(tagName) }
        )
    }
}

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.activeView
import com.emberr.domain.database.filterConditionsFor
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseDateRange
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseRelativeDate
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.tagPoolKey
import com.emberr.domain.model.valueTypeOf
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.property.PropertyTagChip
import com.emberr.presentation.shared.editor.blockViews.property.formatPropertyDate
import com.emberr.presentation.shared.editor.blockViews.property.rememberPropertyTagColor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.funnel
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.x
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Instant

private val DesktopFilterMenuWidth = 320.dp

@Composable
internal fun DatabaseColumnFilterOption(
    block: DatabaseBlock,
    column: DatabaseColumnTarget,
    editor: DatabaseBlockEditor
) {
    val view = block.activeView()
    val columnFilters = view.filters.filter { it.target == column }

    DatabaseMenuLayer(
        title = "Filter",
        desktopWidth = DesktopFilterMenuWidth,
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = "Filter",
                icon = { DatabaseOptionIcon(Res.drawable.funnel) },
                trailing = if (columnFilters.isEmpty()) null else {
                    {
                        Text(
                            text = columnFilters.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                },
                onClick = openLayer
            )
        }
    ) { _ ->
        if (columnFilters.isEmpty()) {
            DatabaseMenuMessage(text = "No filters on this column yet", color = MaterialTheme.colorScheme.outline)
        }

        columnFilters.forEachIndexed { index, filter ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = DatabaseMenuRowInset, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            }
            DatabaseFilterEditor(
                block = block,
                filter = filter,
                editor = editor,
                onChange = { change -> editor.changeFilter(block.id, view.id, filter.id, change) },
                onRemove = { editor.removeFilter(block.id, view.id, filter.id) }
            )
        }

        DatabaseMenuOption(
            label = "Add filter",
            icon = { DatabaseOptionIcon(Res.drawable.plus) },
            onClick = { editor.addFilter(block.id, view.id, column) }
        )
    }
}

@Composable
internal fun DatabaseFilterEditor(
    block: DatabaseBlock,
    filter: DatabaseFilter,
    editor: DatabaseBlockEditor,
    onChange: ((DatabaseFilter) -> DatabaseFilter) -> Unit,
    onRemove: () -> Unit,
    removeLabel: String = "Remove filter"
) {
    val valueType = block.valueTypeOf(filter.target) ?: PropertyValueType.TEXT
    val columnLabel = block.labelOf(filter.target)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DatabaseMenuRowInset, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DatabaseMenuLayer(
                title = columnLabel,
                anchor = { openLayer ->
                    DatabaseChoiceButton(text = filter.condition.label, onClick = openLayer)
                }
            ) { closeLayerAnd ->
                DatabaseConditionChoices(
                    valueType = valueType,
                    selectedCondition = filter.condition,
                    onPick = { condition -> closeLayerAnd { onChange { it.copy(condition = condition) } } }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(Res.drawable.x),
                contentDescription = removeLabel,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(4.dp)
                    .size(14.dp)
            )
        }

        if (filter.condition.needsValue) {
            Spacer(modifier = Modifier.height(8.dp))
            when {
                valueType.holdsDate && filter.condition == DatabaseFilterCondition.IS_WITHIN -> DatabaseFilterDateRangeValue(
                    title = columnLabel,
                    dateRange = filter.dateRange,
                    onDateRangeChosen = { dateRange -> onChange { it.copy(dateRange = dateRange) } }
                )
                valueType.holdsDate -> DatabaseFilterDateValue(
                    title = columnLabel,
                    date = filter.date,
                    relativeDate = filter.relativeDate,
                    onRelativeDateChosen = { relativeDate -> onChange { it.copy(relativeDate = relativeDate, date = null) } },
                    onDateChosen = { date -> onChange { it.copy(date = date, relativeDate = null) } }
                )
                valueType.holdsTags -> DatabaseMenuLayer(
                    title = columnLabel,
                    anchor = { openLayer -> DatabaseFilterOptionValue(tagName = filter.tagName, tagPoolKey = filter.target.tagPoolKey, onClick = openLayer) }
                ) { closeLayerAnd ->
                    DatabaseOptionChoices(
                        filter = filter,
                        editor = editor,
                        onPick = { tagName -> closeLayerAnd { onChange { it.copy(tagName = tagName) } } }
                    )
                }
                else -> EmberrTextField(
                    value = filter.text,
                    onValueChange = { text -> onChange { it.copy(text = text) } },
                    placeholder = if (valueType.holdsNumber) "Type a number" else "Type a value",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = if (valueType.holdsNumber) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default
                )
            }
        }
    }
}

@Composable
internal fun DatabaseChoiceButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun DatabaseFilterDateValue(
    title: String,
    date: LocalDate?,
    relativeDate: DatabaseRelativeDate?,
    onRelativeDateChosen: (DatabaseRelativeDate) -> Unit,
    onDateChosen: (LocalDate) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val timeZone = TimeZone.currentSystemDefault()
    val shownValue = relativeDate?.label ?: date?.let { formatPropertyDate(it) }

    DatabaseMenuLayer(
        title = title,
        anchor = { openLayer ->
            DatabaseFilterValueBox(onClick = openLayer) {
                DatabaseFilterValueText(text = shownValue ?: "Pick a date", isPlaceholder = shownValue == null)
            }
        }
    ) { closeLayerAnd ->
        DatabaseRelativeDate.entries.forEach { option ->
            DatabaseMenuOption(
                label = option.label,
                isSelected = option == relativeDate,
                onClick = { closeLayerAnd { onRelativeDateChosen(option) } }
            )
        }
        DatabaseMenuSectionDivider()
        DatabaseMenuOption(
            label = date?.let { "Exact date: ${formatPropertyDate(it)}" } ?: "Exact date",
            isSelected = relativeDate == null && date != null,
            onClick = { closeLayerAnd { showDatePicker = true } }
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
private fun DatabaseFilterDateRangeValue(
    title: String,
    dateRange: DatabaseDateRange?,
    onDateRangeChosen: (DatabaseDateRange) -> Unit
) {
    DatabaseMenuLayer(
        title = title,
        anchor = { openLayer ->
            DatabaseFilterValueBox(onClick = openLayer) {
                DatabaseFilterValueText(text = dateRange?.label ?: "Pick a range", isPlaceholder = dateRange == null)
            }
        }
    ) { closeLayerAnd ->
        DatabaseDateRange.entries.forEach { option ->
            DatabaseMenuOption(
                label = option.label,
                isSelected = option == dateRange,
                onClick = { closeLayerAnd { onDateRangeChosen(option) } }
            )
        }
    }
}

@Composable
private fun DatabaseFilterValueText(text: String, isPlaceholder: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isPlaceholder) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun DatabaseFilterOptionValue(tagName: String?, tagPoolKey: String?, onClick: () -> Unit) {
    DatabaseFilterValueBox(onClick = onClick) {
        if (tagName == null) {
            Text(text = "Pick an option", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        } else {
            PropertyTagChip(tagName = tagName, tagPoolKey = tagPoolKey, textStyle = MaterialTheme.typography.labelSmall)
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
                        .background(rememberPropertyTagColor(tagPoolKey, tagName))
                )
            },
            onClick = { onPick(tagName) }
        )
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.emberr.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.RecurrenceEditScope
import com.emberr.domain.model.RecurrenceFrequency
import com.emberr.domain.model.RecurrenceRule
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBlur
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrBottomSheetAction
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrShadowElevation
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.components.MinimalTimePickerDialog
import com.emberr.presentation.shared.components.NoRippleIndicationNodeFactory
import com.emberr.presentation.shared.components.TopBarIconButton
import dev.chrisbanes.haze.HazeState
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.calendar
import emberr.shared.generated.resources.clock_circle
import emberr.shared.generated.resources.doc_text
import emberr.shared.generated.resources.link
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.widget2
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import org.jetbrains.compose.resources.painterResource

private val EventChipTextColor = Color(0xFF1A1A1A)
private val InteractiveShape = RoundedCornerShape(12.dp)
private val FieldPadding = 14.dp
private val SectionSpacing = 16.dp

@Composable
fun EventChip(
    text: String,
    color: Color,
    hasCategory: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val textColor = if (hasCategory) EventChipTextColor else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = InteractiveShape,
        color = color,
        modifier = modifier
            .height(height)
            .clip(InteractiveShape)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.TopStart) {
            Text(
                text = text.ifBlank { "Untitled event" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

data class EventEditorState(
    val original: CalendarEvent?,
    val name: String,
    val date: LocalDate,
    val hour: Int,
    val minute: Int,
    val categoryId: String?,
    val durationMinutes: Int = 30,
    val url: String = "",
    val description: String = "",
    val recurrenceRule: RecurrenceRule? = null,
    val editScope: RecurrenceEditScope = RecurrenceEditScope.ALL_EVENTS,
    val isEditing: Boolean = original == null
)

fun CalendarEvent.toEditorState(): EventEditorState {
    val startedAt = Instant.fromEpochMilliseconds(reminderTimestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    return EventEditorState(
        original = this,
        name = text,
        date = startedAt.date,
        hour = startedAt.hour,
        minute = startedAt.minute,
        categoryId = categoryId,
        durationMinutes = durationMinutes,
        url = url.orEmpty(),
        description = description.orEmpty(),
        recurrenceRule = recurrenceRule
    )
}


fun EventEditorState.toEpochMillis(): Long {
    val localDateTime = LocalDateTime(date.year, date.month.number, date.day, hour, minute)
    return localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}
fun EventEditorState.toEndEpochMillis(): Long = toEpochMillis() + durationMinutes * 60_000L

fun EventEditorState.endHour(): Int {
    val startTotal = hour * 60 + minute
    return ((startTotal + durationMinutes) / 60) % 24
}

fun EventEditorState.endMinute(): Int = (minute + durationMinutes) % 60

fun formatTimeOfDay(hour: Int, minute: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val period = if (hour < 12) "AM" else "PM"
    return "$displayHour:${minute.toString().padStart(2, '0')} $period"
}

fun formatRecurrenceSummary(rule: RecurrenceRule): String {
    val base = when (rule.frequency) {
        RecurrenceFrequency.DAILY -> if (rule.interval == 1) "Daily" else "Every ${rule.interval} days"
        RecurrenceFrequency.WEEKLY -> {
            val dayLabel = rule.daysOfWeek.takeIf { it.isNotEmpty() }
                ?.sortedBy { it.isoDayNumber }
                ?.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
                ?.let { " on $it" }
                ?: ""
            (if (rule.interval == 1) "Weekly" else "Every ${rule.interval} weeks") + dayLabel
        }
        RecurrenceFrequency.MONTHLY -> if (rule.interval == 1) "Monthly" else "Every ${rule.interval} months"
        RecurrenceFrequency.YEARLY -> if (rule.interval == 1) "Yearly" else "Every ${rule.interval} years"
    }
    val until = rule.untilDateString?.let { " until ${formatFullDate(LocalDate.parse(it))}" } ?: ""
    return base + until
}

fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}min"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}min"
    }
}

@Composable
fun EventEditorSheet(
    state: EventEditorState?,
    categories: List<CalendarCategory>,
    onNameChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onDurationChange: (minutes: Int) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onUrlChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRecurrenceChange: (RecurrenceRule?) -> Unit,
    onEditClick: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val title = when {
        state == null || state.original == null -> "Add Event"
        state.isEditing -> "Edit Event"
        else -> null
    }

    EmberrBottomSheet(
        expanded = state != null,
        onDismiss = onDismiss,
        title = title,
        headerAction = if (onDelete != null) {
            EmberrBottomSheetAction(
                icon = painterResource(Res.drawable.trash),
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        } else null,
    ) { closeAnd ->
      CompositionLocalProvider(
        LocalIndication provides NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides null
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            if (state != null) {
                EventEditorFields(
                    state = state,
                    categories = categories,
                    onNameChange = onNameChange,
                    onDateChange = onDateChange,
                    onTimeChange = onTimeChange,
                    onDurationChange = onDurationChange,
                    onCategoryChange = onCategoryChange,
                    onUrlChange = onUrlChange,
                    onDescriptionChange = onDescriptionChange,
                    onRecurrenceChange = onRecurrenceChange,
                    onEditClick = onEditClick,
                    onCancel = { closeAnd(onDismiss) },
                    onSave = { closeAnd(onSave) },
                    onDelete = onDelete?.let { delete -> { closeAnd(delete) } }
                )
            }
        }
      }
    }
}

@Composable
private fun EventEditorFields(
    state: EventEditorState,
    categories: List<CalendarCategory>,
    onNameChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onDurationChange: (minutes: Int) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onUrlChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRecurrenceChange: (RecurrenceRule?) -> Unit,
    onEditClick: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?
) {
    if (!state.isEditing) {
        EventViewFields(
            state = state,
            categories = categories,
            onEditClick = onEditClick,
            onDelete = onDelete,
            onClose = onCancel,
        )
        return
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = if (isDesktopPlatform) 16.dp else 10.dp,
                bottom = if (isDesktopPlatform) 0.dp else 20.dp
            ),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        EmberrTextField(
            value = state.name,
            onValueChange = onNameChange,
            placeholder = "Event name",
            modifier = Modifier.fillMaxWidth(),
            onSubmit = onSave
        )

        EmberrTextField(
            value = state.url,
            onValueChange = onUrlChange,
            placeholder = "URL (optional)",
            modifier = Modifier.fillMaxWidth(),
            onSubmit = onSave
        )
        val canEditDate = state.editScope == RecurrenceEditScope.ALL_EVENTS
        EventFieldRow(
            icon = painterResource(Res.drawable.calendar),
            label = formatFullDate(state.date),
            onClick = { if (canEditDate) showDatePicker = true },
            modifier = Modifier.fillMaxWidth().let { if (canEditDate) it else it.alpha(0.5f) }
        )
        if (showDatePicker) {
            MinimalDatePickerDialog(
                initialTimestamp = state.toEpochMillis(),
                onDismiss = { showDatePicker = false },
                onConfirm = { millis ->
                    val instant = Instant.fromEpochMilliseconds(millis)
                    onDateChange(instant.toLocalDateTime(TimeZone.UTC).date)
                }
            )
        }

        var showRepeatDialog by remember { mutableStateOf(false) }
        val canEditRecurrence = state.editScope == RecurrenceEditScope.ALL_EVENTS
        EventFieldRow(
            icon = painterResource(Res.drawable.clock_circle),
            label = state.recurrenceRule?.let(::formatRecurrenceSummary) ?: "Does not repeat",
            onClick = { if (canEditRecurrence) showRepeatDialog = true },
            modifier = Modifier.fillMaxWidth().let { if (canEditRecurrence) it else it.alpha(0.5f) }
        )
        if (showRepeatDialog) {
            RepeatOptionsDialog(
                initialRule = state.recurrenceRule,
                anchorDate = state.date,
                onDismiss = { showRepeatDialog = false },
                onConfirm = { rule ->
                    onRecurrenceChange(rule)
                    showRepeatDialog = false
                }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    EventFieldRow(
                        icon = painterResource(Res.drawable.clock_circle),
                        label = formatTimeOfDay(state.hour, state.minute),
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showStartTimePicker) {
                        MinimalTimePickerDialog(
                            initialTimestamp = state.toEpochMillis(),
                            onDismiss = { showStartTimePicker = false },
                            onConfirm = { hour, minute -> onTimeChange(hour, minute) }
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    EventFieldRow(
                        icon = painterResource(Res.drawable.clock_circle),
                        label = formatTimeOfDay(state.endHour(), state.endMinute()),
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showEndTimePicker) {
                        MinimalTimePickerDialog(
                            initialTimestamp = state.toEndEpochMillis(),
                            onDismiss = { showEndTimePicker = false },
                            onConfirm = { hour, minute ->
                                val startTotal = state.hour * 60 + state.minute
                                var endTotal = hour * 60 + minute
                                if (endTotal <= startTotal) endTotal += 24 * 60
                                onDurationChange((endTotal - startTotal).coerceAtLeast(5))
                            }
                        )
                    }
                }
            }

            Text(
                text = "Duration: ${formatDuration(state.durationMinutes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    label = "None",
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    hasCategory = false,
                    isSelected = state.categoryId == null,
                    onClick = { onCategoryChange(null) }
                )
                categories.forEach { category ->
                    CategoryChip(
                        label = category.name,
                        color = category.colorHex.toCategoryColor(),
                        hasCategory = true,
                        isSelected = state.categoryId == category.id,
                        onClick = { onCategoryChange(category.id) }
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            EmberrTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                placeholder = "Add a description (optional)",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmberrButtonSecondary(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            EmberrButtonPrimary(
                text = if (state.original == null) "Add" else "Save",
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EventViewFields(
    state: EventEditorState,
    categories: List<CalendarCategory>,
    onEditClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val category = categories.firstOrNull { it.id == state.categoryId }
    val accentColor = category?.colorHex?.toCategoryColor() ?: MaterialTheme.colorScheme.primary
    val hazeState = remember { HazeState() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = if (isDesktopPlatform) 16.dp else 24.dp,
                bottom = if (isDesktopPlatform) 0.dp else 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopBarIconButton(
                    icon = painterResource(Res.drawable.pen),
                    contentDescription = "Edit",
                    bgColor = Color.Transparent,
                    tint = MaterialTheme.colorScheme.primary,
                    hazeState = hazeState,
                    hazeStyle = EmberrBlur.Regular,
                    shadowElevation = EmberrShadowElevation.None,
                    onClick = onEditClick
                )
                if (onDelete != null) {
                    TopBarIconButton(
                        icon = painterResource(Res.drawable.trash),
                        contentDescription = "Delete",
                        bgColor = Color.Transparent,
                        tint = MaterialTheme.colorScheme.error,
                        hazeState = hazeState,
                        hazeStyle = EmberrBlur.Regular,
                        shadowElevation = EmberrShadowElevation.None,
                        onClick = onDelete
                    )
                }
            }
        }

        // Accent bar + title/subtitle block.
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.name.ifBlank { "Untitled event" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatFullDate(state.date)}, ${formatTimeOfDay(state.hour, state.minute)} – " +
                            formatTimeOfDay(state.endHour(), state.endMinute()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.recurrenceRule?.let { rule ->
                InfoRow(icon = painterResource(Res.drawable.clock_circle), label = "Repeats ${formatRecurrenceSummary(rule).replaceFirstChar(Char::lowercase)}")
            }
            if (category != null) {
                InfoRow(icon = painterResource(Res.drawable.widget2), label = category.name)
            }
            if (state.url.isNotBlank()) {
                val uriHandler = LocalUriHandler.current
                InfoRow(
                    icon = painterResource(Res.drawable.link),
                    label = state.url,
                    isLink = true,
                    onClick = { try { uriHandler.openUri(state.url) } catch (_: Exception) {} }
                )
            }
            if (state.description.isNotBlank()) {
                InfoRow(icon = painterResource(Res.drawable.doc_text), label = state.description)
            }
        }

        if (!isDesktopPlatform) {
            EmberrButtonPrimary(
                text = "Close",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: Painter,
    label: String,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (isLink) TextDecoration.Underline else null
        )
    }
}

@Composable
private fun EventFieldRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = InteractiveShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clip(InteractiveShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FieldPadding, vertical = FieldPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    color: Color,
    hasCategory: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (hasCategory) EventChipTextColor else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = InteractiveShape,
        color = color,
        modifier = Modifier
            .clip(InteractiveShape)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else Color.Transparent,
                shape = InteractiveShape
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = FieldPadding, vertical = 8.dp)
        )
    }
}

private val WeekdayOrder = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
)

@Composable
private fun SheetOptionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InteractiveShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun RepeatOptionsDialog(
    initialRule: RecurrenceRule?,
    anchorDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRule?) -> Unit
) {
    var showCustom by remember { mutableStateOf(false) }

    if (showCustom) {
        CustomRepeatDialog(
            initialRule = initialRule,
            anchorDate = anchorDate,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
        return
    }

    EmberrBottomSheet(expanded = true, onDismiss = onDismiss, title = "Repeat") { closeAnd ->
        CompositionLocalProvider(
            LocalIndication provides NoRippleIndicationNodeFactory,
            LocalRippleConfiguration provides null
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                SheetOptionRow("Never") { closeAnd { onConfirm(null) } }
                SheetOptionRow("Daily") { closeAnd { onConfirm(RecurrenceRule(frequency = RecurrenceFrequency.DAILY)) } }
                SheetOptionRow("Weekly") {
                    closeAnd { onConfirm(RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(anchorDate.dayOfWeek))) }
                }
                SheetOptionRow("Monthly") { closeAnd { onConfirm(RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY)) } }
                SheetOptionRow("Yearly") { closeAnd { onConfirm(RecurrenceRule(frequency = RecurrenceFrequency.YEARLY)) } }
                SheetOptionRow("Custom…") { showCustom = true }

                EmberrButtonPrimary(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

private val EmberrCorner = RoundedCornerShape(12.dp)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(EmberrCorner)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun DayOfWeek.shortLabel(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

private fun buildRepeatSummary(
    frequency: RecurrenceFrequency,
    interval: Int,
    daysOfWeek: Set<DayOfWeek>,
    anchorDate: LocalDate,
    untilDateString: String?
): String {
    val unit = when (frequency) {
        RecurrenceFrequency.DAILY -> "day"
        RecurrenceFrequency.WEEKLY -> "week"
        RecurrenceFrequency.MONTHLY -> "month"
        RecurrenceFrequency.YEARLY -> "year"
    }
    val base = if (interval == 1) "Every $unit" else "Every $interval ${unit}s"
    val detail = when (frequency) {
        RecurrenceFrequency.WEEKLY -> daysOfWeek
            .sortedBy { WeekdayOrder.indexOf(it) }
            .joinToString(", ") { it.shortLabel() }
            .let { if (it.isBlank()) "" else " on $it" }
        RecurrenceFrequency.MONTHLY -> " on day ${anchorDate.day}"
        else -> ""
    }
    val ending = untilDateString
        ?.let { " · until ${formatFullDate(LocalDate.parse(it))}" }
        .orEmpty()
    return base + detail + ending
}

@Composable
private fun CustomRepeatDialog(
    initialRule: RecurrenceRule?,
    anchorDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRule?) -> Unit
) {
    var frequency by remember { mutableStateOf(initialRule?.frequency ?: RecurrenceFrequency.WEEKLY) }
    var interval by remember { mutableIntStateOf(initialRule?.interval ?: 1) }
    var daysOfWeek by remember {
        mutableStateOf(initialRule?.daysOfWeek?.takeIf { it.isNotEmpty() } ?: setOf(anchorDate.dayOfWeek))
    }
    var hasEndDate by remember { mutableStateOf(initialRule?.untilDateString != null) }
    var untilDateString by remember { mutableStateOf(initialRule?.untilDateString) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val summary = remember(frequency, interval, daysOfWeek, hasEndDate, untilDateString) {
        buildRepeatSummary(
            frequency = frequency,
            interval = interval,
            daysOfWeek = daysOfWeek,
            anchorDate = anchorDate,
            untilDateString = if (hasEndDate) untilDateString else null
        )
    }

    val divider = @Composable {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }

    EmberrBottomSheet(expanded = true, onDismiss = onDismiss, title = "Custom Repeat") { closeAnd ->
        CompositionLocalProvider(
            LocalIndication provides NoRippleIndicationNodeFactory,
            LocalRippleConfiguration provides null
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                divider()

                // Interval
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Repeat every",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StepperButton(
                            icon = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            enabled = interval > 1
                        ) { interval = (interval - 1).coerceAtLeast(1) }
                        Text(
                            text = interval.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 36.dp)
                        )
                        StepperButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Increase",
                            enabled = interval < 99
                        ) { interval = (interval + 1).coerceAtMost(99) }
                    }
                }

                // Frequency
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecurrenceFrequency.entries.forEach { option ->
                        CategoryChip(
                            label = when (option) {
                                RecurrenceFrequency.DAILY -> "Days"
                                RecurrenceFrequency.WEEKLY -> "Weeks"
                                RecurrenceFrequency.MONTHLY -> "Months"
                                RecurrenceFrequency.YEARLY -> "Years"
                            },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            hasCategory = false,
                            isSelected = frequency == option,
                            onClick = { frequency = option }
                        )
                    }
                }

                // Weekdays
                if (frequency == RecurrenceFrequency.WEEKLY) {
                    divider()

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("On these days")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WeekdayOrder.forEach { day ->
                                val isSelected = day in daysOfWeek
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(EmberrCorner)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            daysOfWeek = if (isSelected) {
                                                (daysOfWeek - day).ifEmpty { setOf(day) }
                                            } else {
                                                daysOfWeek + day
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.name.take(1),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                divider()

                // End condition
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ends",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryChip(
                            label = "Never",
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            hasCategory = false,
                            isSelected = !hasEndDate,
                            onClick = { hasEndDate = false }
                        )
                        CategoryChip(
                            label = "On date",
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            hasCategory = false,
                            isSelected = hasEndDate,
                            onClick = {
                                hasEndDate = true
                                if (untilDateString == null) showEndDatePicker = true
                            }
                        )
                    }
                }

                if (hasEndDate) {
                    EventFieldRow(
                        icon = painterResource(Res.drawable.calendar),
                        label = untilDateString?.let { formatFullDate(LocalDate.parse(it)) }
                            ?: "Choose end date",
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showEndDatePicker) {
                    val initialMillis = untilDateString?.let { LocalDate.parse(it) }
                        ?.let {
                            LocalDateTime(it.year, it.month.number, it.day, 0, 0)
                                .toInstant(TimeZone.UTC).toEpochMilliseconds()
                        }
                        ?: System.currentTimeMillis()
                    MinimalDatePickerDialog(
                        initialTimestamp = initialMillis,
                        onDismiss = { showEndDatePicker = false },
                        onConfirm = { millis ->
                            untilDateString = Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC).date.toString()
                            showEndDatePicker = false
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmberrButtonSecondary(text = "Cancel", onClick = { closeAnd(onDismiss) }, modifier = Modifier.weight(1f))
                    EmberrButtonPrimary(
                        text = "Done",
                        onClick = {
                            closeAnd {
                                onConfirm(
                                    RecurrenceRule(
                                        frequency = frequency,
                                        interval = interval,
                                        daysOfWeek = if (frequency == RecurrenceFrequency.WEEKLY) daysOfWeek else emptySet(),
                                        untilDateString = if (hasEndDate) untilDateString else null
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun RecurrenceScopeChooser(
    onDismiss: () -> Unit,
    onScopeSelected: (RecurrenceEditScope) -> Unit
) {
    EmberrBottomSheet(expanded = true, onDismiss = onDismiss, title = "Which events?") { closeAnd ->
        CompositionLocalProvider(
            LocalIndication provides NoRippleIndicationNodeFactory,
            LocalRippleConfiguration provides null
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                SheetOptionRow("This event") { closeAnd { onScopeSelected(RecurrenceEditScope.THIS_EVENT) } }
                SheetOptionRow("All future events") { closeAnd { onScopeSelected(RecurrenceEditScope.ALL_FUTURE_EVENTS) } }
                SheetOptionRow("All past events") { closeAnd { onScopeSelected(RecurrenceEditScope.ALL_PAST_EVENTS) } }
                SheetOptionRow("All events") { closeAnd { onScopeSelected(RecurrenceEditScope.ALL_EVENTS) } }

                EmberrButtonPrimary(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 20.dp)
                )
            }
        }
    }
}

data class PositionedEvent(
    val event: CalendarEvent,
    val columnIndex: Int,
    val columnCount: Int
)

private fun CalendarEvent.endTimestamp(): Long = reminderTimestamp + durationMinutes * 60_000L
fun layoutEventsForColumn(events: List<CalendarEvent>): List<PositionedEvent> {
    if (events.isEmpty()) return emptyList()
    val sorted = events.sortedBy { it.reminderTimestamp }

    val result = mutableListOf<PositionedEvent>()
    var clusterColumns = mutableListOf<MutableList<CalendarEvent>>()
    var clusterEnd = Long.MIN_VALUE

    fun flushCluster() {
        val columnCount = clusterColumns.size
        if (columnCount == 0) return
        clusterColumns.forEachIndexed { columnIndex, column ->
            column.forEach { event -> result.add(PositionedEvent(event, columnIndex, columnCount)) }
        }
        clusterColumns = mutableListOf()
        clusterEnd = Long.MIN_VALUE
    }

    for (event in sorted) {
        if (clusterColumns.isNotEmpty() && event.reminderTimestamp >= clusterEnd) {
            flushCluster()
        }
        val targetColumn = clusterColumns.firstOrNull { column -> column.last().endTimestamp() <= event.reminderTimestamp }
        if (targetColumn != null) {
            targetColumn.add(event)
        } else {
            clusterColumns.add(mutableListOf(event))
        }
        clusterEnd = maxOf(clusterEnd, event.endTimestamp())
    }
    flushCluster()

    return result
}
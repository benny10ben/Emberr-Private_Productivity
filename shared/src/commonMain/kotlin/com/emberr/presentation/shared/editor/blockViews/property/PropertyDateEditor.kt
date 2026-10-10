package com.emberr.presentation.shared.editor.blockViews.property

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.emberr.domain.model.DEFAULT_PROPERTY_TIME
import com.emberr.domain.model.PropertyDateRange
import com.emberr.presentation.shared.components.EmberrSwitch
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.components.MinimalTimePickerDialog
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseMenu
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseMenuOption
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseMenuSectionDivider
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseMenuSectionLabel
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseOptionIcon
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.calendar_day
import emberr.shared.generated.resources.clock_circle
import emberr.shared.generated.resources.x
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal enum class PropertyDatePart { START_DATE, START_TIME, END_DATE, END_TIME }

@Composable
internal fun PropertyDateEditor(
    expanded: Boolean,
    title: String,
    range: PropertyDateRange,
    onRangeChange: (PropertyDateRange) -> Unit,
    onPickPart: (PropertyDatePart) -> Unit,
    onDismiss: () -> Unit
) {
    DatabaseMenu(expanded = expanded, title = title, onDismiss = onDismiss) { closeAnd ->
        val start = range.start
        if (start != null) {
            DatabaseMenuSectionLabel(text = if (range.hasEnd) "Start" else "Date")
            PropertyDatePartOptions(
                date = start,
                time = range.startTime,
                onPickDate = { onPickPart(PropertyDatePart.START_DATE) },
                onPickTime = { onPickPart(PropertyDatePart.START_TIME) }
            )
        }
        val end = range.end
        if (start != null && end != null) {
            DatabaseMenuSectionLabel(text = "End")
            PropertyDatePartOptions(
                date = end,
                time = range.endTime,
                onPickDate = { onPickPart(PropertyDatePart.END_DATE) },
                onPickTime = { onPickPart(PropertyDatePart.END_TIME) }
            )
        }

        DatabaseMenuSectionDivider()
        DatabaseMenuOption(
            label = "End date",
            trailing = { EmberrSwitch(isOn = range.hasEnd) },
            onClick = { onRangeChange(range.withEndDateShown(!range.hasEnd)) }
        )
        DatabaseMenuOption(
            label = "Include time",
            trailing = { EmberrSwitch(isOn = range.includesTime) },
            onClick = { onRangeChange(range.withTimeIncluded(!range.includesTime)) }
        )

        DatabaseMenuSectionDivider()
        DatabaseMenuOption(
            label = "Clear",
            icon = { DatabaseOptionIcon(Res.drawable.x, tint = MaterialTheme.colorScheme.error) },
            labelColor = MaterialTheme.colorScheme.error,
            onClick = { closeAnd { onRangeChange(PropertyDateRange()) } }
        )
    }
}

@Composable
private fun PropertyDatePartOptions(
    date: LocalDate,
    time: LocalTime?,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit
) {
    DatabaseMenuOption(
        label = formatPropertyDate(date),
        icon = { DatabaseOptionIcon(Res.drawable.calendar_day) },
        onClick = onPickDate
    )
    if (time != null) {
        DatabaseMenuOption(
            label = formatPropertyTime(time),
            icon = { DatabaseOptionIcon(Res.drawable.clock_circle) },
            onClick = onPickTime
        )
    }
}

@Composable
internal fun PropertyDatePartPicker(
    part: PropertyDatePart,
    range: PropertyDateRange,
    onPicked: (PropertyDateRange) -> Unit,
    onDismiss: () -> Unit
) {
    val timeZone = TimeZone.currentSystemDefault()
    val shownDate = (if (part == PropertyDatePart.END_DATE || part == PropertyDatePart.END_TIME) range.end else null) ?: range.start

    when (part) {
        PropertyDatePart.START_DATE, PropertyDatePart.END_DATE -> MinimalDatePickerDialog(
            initialTimestamp = shownDate?.atStartOfDayIn(timeZone)?.toEpochMilliseconds(),
            onDismiss = onDismiss,
            onConfirm = { selectedMillis ->
                val pickedDate = Instant.fromEpochMilliseconds(selectedMillis).toLocalDateTime(timeZone).date
                onPicked(if (part == PropertyDatePart.START_DATE) range.copy(start = pickedDate) else range.copy(end = pickedDate))
            }
        )
        PropertyDatePart.START_TIME, PropertyDatePart.END_TIME -> {
            val shownTime = (if (part == PropertyDatePart.END_TIME) range.endTime else range.startTime) ?: DEFAULT_PROPERTY_TIME
            MinimalTimePickerDialog(
                initialTimestamp = shownDate?.let { LocalDateTime(it, shownTime).toInstant(timeZone).toEpochMilliseconds() },
                onDismiss = onDismiss,
                onConfirm = { hour, minute ->
                    val pickedTime = LocalTime(hour, minute)
                    onPicked(if (part == PropertyDatePart.START_TIME) range.copy(startTime = pickedTime) else range.copy(endTime = pickedTime))
                }
            )
        }
    }
}

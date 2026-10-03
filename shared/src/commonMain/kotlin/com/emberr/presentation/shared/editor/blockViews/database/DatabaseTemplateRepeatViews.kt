package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.localNow
import com.emberr.domain.database.repeatStartingNow
import com.emberr.domain.database.withTime
import com.emberr.domain.model.DEFAULT_PROPERTY_TIME
import com.emberr.domain.model.DatabaseRepeatFrequency
import com.emberr.domain.model.DatabaseTemplateRepeat
import com.emberr.domain.model.isoDayNumberToDayOfWeek
import com.emberr.presentation.shared.components.MinimalTimePickerDialog
import com.emberr.presentation.shared.editor.blockViews.formatPropertyTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant

private val RepeatDayButtonSize = 34.dp

internal fun DatabaseTemplateRepeat.summary(): String {
    val daysText = when (frequency) {
        DatabaseRepeatFrequency.WEEKLY -> {
            val dayNames = isoDaysOfWeek.sorted().mapNotNull { isoDayNumberToDayOfWeek(it)?.shortName() }
            if (dayNames.isEmpty()) frequency.label else "${frequency.label} on ${dayNames.joinToString(", ")}"
        }
        DatabaseRepeatFrequency.MONTHLY -> "${frequency.label} on day ${startsOn.day}"
        else -> frequency.label
    }
    val repeatTime = time ?: return daysText
    return "$daysText at ${formatPropertyTime(repeatTime)}"
}

private fun DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

@Composable
internal fun DatabaseTemplateRepeatChoices(
    repeat: DatabaseTemplateRepeat?,
    onRepeatChange: (DatabaseTemplateRepeat?) -> Unit
) {
    var showsTimePicker by remember { mutableStateOf(false) }

    DatabaseMenuMessage(
        text = "Emberr adds a row from this template on each repeat day at the chosen time, while the app is open. " +
            "If the app was closed then, the row is added the next time you open it that day.",
        color = MaterialTheme.colorScheme.outline
    )
    DatabaseMenuOption(label = "Don't repeat", isSelected = repeat == null, onClick = { onRepeatChange(null) })
    DatabaseRepeatFrequency.entries.forEach { frequency ->
        DatabaseMenuOption(
            label = frequency.label,
            isSelected = repeat?.frequency == frequency,
            onClick = {
                if (repeat?.frequency != frequency) {
                    val now = localNow()
                    val days = if (frequency == DatabaseRepeatFrequency.WEEKLY) listOf(now.date.dayOfWeek.isoDayNumber) else emptyList()
                    onRepeatChange(repeatStartingNow(frequency, days, repeat?.time, now))
                }
            }
        )
    }
    if (repeat?.frequency == DatabaseRepeatFrequency.WEEKLY) {
        DatabaseMenuSectionLabel(text = "On these days")
        DatabaseRepeatDayButtons(
            selectedIsoDays = repeat.isoDaysOfWeek,
            onSelectedIsoDaysChange = { days -> onRepeatChange(repeat.copy(isoDaysOfWeek = days)) }
        )
    }
    if (repeat != null) {
        DatabaseMenuSectionDivider()
        DatabaseMenuSectionLabel(text = "Time")
        DatabaseMenuOption(
            label = "At the start of the day",
            isSelected = repeat.time == null,
            onClick = { onRepeatChange(repeat.withTime(null, localNow())) }
        )
        DatabaseMenuOption(
            label = repeat.time?.let { "At ${formatPropertyTime(it)}" } ?: "At a set time",
            isSelected = repeat.time != null,
            onClick = { showsTimePicker = true }
        )
    }

    if (showsTimePicker && repeat != null) {
        val shownTime = repeat.time ?: DEFAULT_PROPERTY_TIME
        val timeZone = TimeZone.currentSystemDefault()
        MinimalTimePickerDialog(
            initialTimestamp = LocalDateTime(repeat.startsOn, shownTime).toInstant(timeZone).toEpochMilliseconds(),
            onDismiss = { showsTimePicker = false },
            onConfirm = { hour, minute ->
                showsTimePicker = false
                onRepeatChange(repeat.withTime(LocalTime(hour, minute), localNow()))
            }
        )
    }
}

@Composable
private fun DatabaseRepeatDayButtons(selectedIsoDays: List<Int>, onSelectedIsoDaysChange: (List<Int>) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DatabaseMenuTextInset, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..7).forEach { isoDay ->
            val isSelected = isoDay in selectedIsoDays
            val dayLetter = isoDayNumberToDayOfWeek(isoDay)?.name?.take(1).orEmpty()
            Box(
                modifier = Modifier
                    .size(RepeatDayButtonSize)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable {
                        val updatedDays = if (isSelected) selectedIsoDays - isoDay else selectedIsoDays + isoDay
                        if (updatedDays.isNotEmpty()) onSelectedIsoDaysChange(updatedDays.sorted())
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayLetter,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

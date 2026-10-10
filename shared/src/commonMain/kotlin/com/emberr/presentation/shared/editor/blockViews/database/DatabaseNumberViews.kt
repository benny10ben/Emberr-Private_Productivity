package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.formulaNumberText
import com.emberr.domain.database.numberFormatOf
import com.emberr.domain.database.progressFor
import com.emberr.domain.database.textFor
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseNumberDisplay
import com.emberr.domain.model.DatabaseNumberFormat
import com.emberr.domain.model.DatabaseNumberStyle
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.isNumberBeingTyped
import com.emberr.domain.model.numberOrNull
import com.emberr.presentation.shared.components.EmberrSwitch
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.property.PropertyTextValue
import com.emberr.presentation.shared.editor.blockViews.property.toTextAlign
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.hash

private val ProgressBarHeight = 6.dp
private val ProgressRingSize = 18.dp
private val ProgressRingStroke = 2.5.dp
private const val MOST_DECIMAL_PLACES = 4

@Composable
internal fun DatabaseNumberValue(
    number: Double,
    format: DatabaseNumberFormat,
    textColor: Color?,
    alignment: TextAlignment?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val numberText = format.textFor(number)
    val color = textColor ?: MaterialTheme.colorScheme.onBackground
    val progress = format.progressFor(number)

    when (format.display) {
        DatabaseNumberDisplay.NUMBER -> Text(
            text = numberText,
            style = textStyle,
            color = color,
            textAlign = alignment.toTextAlign(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        DatabaseNumberDisplay.BAR -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            if (format.showsNumberWithProgress) {
                Text(text = numberText, style = textStyle, color = color, maxLines = 1)
                Spacer(modifier = Modifier.width(8.dp))
            }
            DatabaseProgressBar(progress = progress, modifier = Modifier.weight(1f))
        }
        DatabaseNumberDisplay.RING -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            DatabaseProgressRing(progress = progress)
            if (format.showsNumberWithProgress) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = numberText, style = textStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DatabaseProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(ProgressBarHeight)
            .clip(RoundedCornerShape(ProgressBarHeight / 2))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun DatabaseProgressRing(progress: Float) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val progressColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(ProgressRingSize)) {
        val strokeWidth = ProgressRingStroke.toPx()
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        drawArc(trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokeWidth))
        drawArc(
            progressColor,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
internal fun DatabaseFormattedNumberCell(
    cell: PropertyBlock,
    format: DatabaseNumberFormat,
    inSelectionMode: Boolean,
    textColor: Color?,
    alignment: TextAlignment?,
    historyStepsApplied: Int,
    onUpdateText: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    val number = cell.numberOrNull()

    if (isEditing) {
        key(historyStepsApplied) {
            PropertyTextValue(
                block = cell,
                inSelectionMode = inSelectionMode,
                onUpdateText = onUpdateText,
                widthModifier = Modifier.fillMaxWidth(),
                textColor = textColor,
                alignment = alignment,
                takesFocusAtStart = true,
                onFocusLost = { isEditing = false }
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !inSelectionMode) { isEditing = true }
                .padding(horizontal = DatabaseCellHorizontalPadding, vertical = DatabaseCellVerticalPadding)
        ) {
            if (number == null) {
                Text(
                    text = "Empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = alignment.toTextAlign(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                DatabaseNumberValue(number = number, format = format, textColor = textColor, alignment = alignment, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun DatabaseNumberFormatOption(
    block: DatabaseBlock,
    column: DatabaseColumnTarget,
    editor: DatabaseBlockEditor
) {
    val format = block.numberFormatOf(column)
    fun save(changedFormat: DatabaseNumberFormat) = editor.setNumberFormat(block.id, column, changedFormat)

    DatabaseMenuLayer(
        title = "Number format",
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = "Number format",
                icon = { DatabaseOptionIcon(Res.drawable.hash) },
                trailing = { DatabaseMenuTrailingText(text = format.style.label) },
                onClick = openLayer
            )
        }
    ) { _ ->
        DatabaseMenuSectionLabel(text = "Format")
        DatabaseNumberStyle.entries.forEach { style ->
            DatabaseMenuOption(
                label = style.label,
                isSelected = style == format.style,
                trailing = { DatabaseMenuTrailingText(text = format.copy(style = style, display = DatabaseNumberDisplay.NUMBER).textFor(1234.5)) },
                onClick = { save(format.copy(style = style)) }
            )
        }

        DatabaseMenuSectionDivider()
        DatabaseMenuLayer(
            title = "Decimal places",
            anchor = { openLayer ->
                DatabaseMenuOption(
                    label = "Decimal places",
                    trailing = { DatabaseMenuTrailingText(text = format.decimalPlaces?.toString() ?: "Default") },
                    onClick = openLayer
                )
            }
        ) { closeLayerAnd ->
            DatabaseMenuOption(
                label = "Default",
                isSelected = format.decimalPlaces == null,
                onClick = { closeLayerAnd { save(format.copy(decimalPlaces = null)) } }
            )
            (0..MOST_DECIMAL_PLACES).forEach { places ->
                DatabaseMenuOption(
                    label = places.toString(),
                    isSelected = format.decimalPlaces == places,
                    onClick = { closeLayerAnd { save(format.copy(decimalPlaces = places)) } }
                )
            }
        }

        DatabaseMenuSectionDivider()
        DatabaseMenuSectionLabel(text = "Show as")
        DatabaseNumberDisplay.entries.forEach { display ->
            DatabaseMenuOption(
                label = display.label,
                isSelected = display == format.display,
                onClick = { save(format.copy(display = display)) }
            )
        }
        if (format.display != DatabaseNumberDisplay.NUMBER) {
            DatabaseMenuSectionLabel(text = "Full at")
            DatabaseMenuContent {
                DatabaseProgressGoalField(goal = format.progressGoal, onGoalChange = { save(format.copy(progressGoal = it)) })
            }
            DatabaseMenuOption(
                label = "Show number",
                trailing = { EmberrSwitch(isOn = format.showsNumberWithProgress) },
                onClick = { save(format.copy(showsNumberWithProgress = !format.showsNumberWithProgress)) }
            )
        }
    }
}

@Composable
private fun DatabaseProgressGoalField(goal: Double, onGoalChange: (Double) -> Unit) {
    var goalText by remember { mutableStateOf(formulaNumberText(goal)) }

    EmberrTextField(
        value = goalText,
        onValueChange = { typedText ->
            if (!isNumberBeingTyped(typedText)) return@EmberrTextField
            goalText = typedText
            typedText.toDoubleOrNull()?.takeIf { it > 0 }?.let(onGoalChange)
        },
        placeholder = "100",
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun DatabaseMenuTrailingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        maxLines = 1
    )
}

package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.isNumberBeingTyped
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.editor.DefaultBlockShape
import com.emberr.presentation.shared.editor.rememberWebLinkActions
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.calendar_clock
import emberr.shared.generated.resources.calendar_day
import emberr.shared.generated.resources.doc_text
import emberr.shared.generated.resources.flag
import emberr.shared.generated.resources.hash
import emberr.shared.generated.resources.link
import emberr.shared.generated.resources.mail
import emberr.shared.generated.resources.phone
import emberr.shared.generated.resources.square_arrow_out_up_right
import emberr.shared.generated.resources.square_check
import emberr.shared.generated.resources.tags
import emberr.shared.generated.resources.text_type
import emberr.shared.generated.resources.x
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Instant

private val PropertyValueHorizontalPadding = 12.dp
private val PropertyValueVerticalPadding = 9.dp

fun PropertyType.iconResource(): DrawableResource = when (this) {
    PropertyType.NAME -> Res.drawable.text_type
    PropertyType.PHONE -> Res.drawable.phone
    PropertyType.EMAIL -> Res.drawable.mail
    PropertyType.DATE -> Res.drawable.calendar_day
    PropertyType.STATUS -> Res.drawable.flag
    PropertyType.TAGS -> Res.drawable.tags
    PropertyType.LINK -> Res.drawable.link
    PropertyType.DESCRIPTION -> Res.drawable.doc_text
    PropertyType.DUE_DATE -> Res.drawable.calendar_clock
    PropertyType.CHECKBOX -> Res.drawable.square_check
    PropertyType.NUMBER -> Res.drawable.hash
}

fun PropertyValueType.iconResource(): DrawableResource = when (this) {
    PropertyValueType.TEXT -> Res.drawable.text_type
    PropertyValueType.PHONE -> Res.drawable.phone
    PropertyValueType.EMAIL -> Res.drawable.mail
    PropertyValueType.LINK -> Res.drawable.link
    PropertyValueType.DATE -> Res.drawable.calendar_day
    PropertyValueType.SINGLE_CHOICE -> Res.drawable.flag
    PropertyValueType.TAGS -> Res.drawable.tags
    PropertyValueType.CHECKBOX -> Res.drawable.square_check
    PropertyValueType.NUMBER -> Res.drawable.hash
}

@Composable
fun PropertyBlockView(
    block: PropertyBlock,
    inSelectionMode: Boolean,
    onUpdateText: (String) -> Unit,
    onUpdateDate: (LocalDate?) -> Unit,
    onUpdateTags: (List<String>) -> Unit,
    onUpdateChecked: (Boolean) -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit = { action -> action() }
) {
    val labelWidth = if (isDesktopPlatform) 150.dp else 120.dp
    val labelToValueGap = if (isDesktopPlatform) 16.dp else 0.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        val valueWidthModifier = if (isDesktopPlatform) {
            val spaceNextToLabel = (maxWidth - labelWidth - labelToValueGap).coerceAtLeast(0.dp)
            Modifier.widthIn(min = spaceNextToLabel / 2)
        } else {
            Modifier.fillMaxWidth()
        }

        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = block.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(labelWidth).padding(vertical = PropertyValueVerticalPadding)
            )
            Spacer(modifier = Modifier.width(labelToValueGap))

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(DefaultBlockShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                when {
                    block.valueType.holdsDate ->
                        PropertyDateValue(block, inSelectionMode, onUpdateDate, runAfterKeyboardCloses, valueWidthModifier)
                    block.valueType.holdsTags ->
                        PropertyTagsValue(block, inSelectionMode, onUpdateTags, runAfterKeyboardCloses, valueWidthModifier)
                    block.valueType.holdsCheck ->
                        PropertyCheckboxValue(block, inSelectionMode, onUpdateChecked, valueWidthModifier)
                    else -> PropertyTextValue(block, inSelectionMode, onUpdateText, valueWidthModifier)
                }
            }
        }
    }
}

@Composable
internal fun PropertyTextValue(
    block: PropertyBlock,
    inSelectionMode: Boolean,
    onUpdateText: (String) -> Unit,
    widthModifier: Modifier
) {
    val focusManager = LocalFocusManager.current
    val webLinkActions = rememberWebLinkActions()
    var fieldValue by remember { mutableStateOf(TextFieldValue(block.text, TextRange(block.text.length))) }
    val textsSentButNotYetEchoed = remember { mutableListOf<String>() }

    LaunchedEffect(block.text) {
        if (fieldValue.text == block.text) {
            textsSentButNotYetEchoed.clear()
            return@LaunchedEffect
        }
        if (block.text in textsSentButNotYetEchoed) return@LaunchedEffect
        textsSentButNotYetEchoed.clear()
        fieldValue = TextFieldValue(block.text, TextRange(block.text.length))
    }

    val keyboardType = when (block.valueType) {
        PropertyValueType.PHONE -> KeyboardType.Phone
        PropertyValueType.EMAIL -> KeyboardType.Email
        PropertyValueType.LINK -> KeyboardType.Uri
        PropertyValueType.NUMBER -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
    val capitalization = when {
        block.propertyType == PropertyType.NAME -> KeyboardCapitalization.Words
        block.valueType == PropertyValueType.TEXT -> KeyboardCapitalization.Sentences
        else -> KeyboardCapitalization.None
    }
    val openValue: ((String) -> Unit)? = when (block.valueType) {
        PropertyValueType.PHONE -> webLinkActions.openPhone
        PropertyValueType.EMAIL -> webLinkActions.openEmail
        PropertyValueType.LINK -> webLinkActions.openLink
        else -> null
    }
    val openIcon = when (block.valueType) {
        PropertyValueType.PHONE -> Res.drawable.phone
        PropertyValueType.EMAIL -> Res.drawable.mail
        else -> Res.drawable.square_arrow_out_up_right
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            val cleanedValue = newValue.copy(text = newValue.text.replace('\n', ' ').replace('\r', ' '))
            if (block.valueType.holdsNumber && !isNumberBeingTyped(cleanedValue.text)) return@BasicTextField
            val textChanged = cleanedValue.text != fieldValue.text
            fieldValue = cleanedValue
            if (textChanged) {
                textsSentButNotYetEchoed += cleanedValue.text
                onUpdateText(cleanedValue.text)
            }
        },
        enabled = !inSelectionMode,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = widthModifier.onPreviewKeyEvent { event ->
            val isEnterPress = event.key == Key.Enter && event.type == KeyEventType.KeyDown
            if (isEnterPress) focusManager.clearFocus()
            isEnterPress
        },
        decorationBox = { innerTextField ->
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = PropertyValueHorizontalPadding, vertical = PropertyValueVerticalPadding)
                ) {
                    if (fieldValue.text.isEmpty()) {
                        Text(
                            text = "Empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()
                }

                if (openValue != null && block.text.isNotBlank() && !inSelectionMode) {
                    Icon(
                        painter = painterResource(openIcon),
                        contentDescription = "Open ${block.label.lowercase()}",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .clickable { openValue(block.text.trim()) }
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }
        }
    )
}

@Composable
internal fun PropertyDateValue(
    block: PropertyBlock,
    inSelectionMode: Boolean,
    onUpdateDate: (LocalDate?) -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit,
    widthModifier: Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val date = block.date
    val timeZone = TimeZone.currentSystemDefault()
    val isPastDueDate = block.propertyType == PropertyType.DUE_DATE &&
        date != null &&
        date < Clock.System.todayIn(timeZone)

    Box {
        Row(
            modifier = widthModifier
                .clickable(enabled = !inSelectionMode) { runAfterKeyboardCloses { showDatePicker = true } }
                .padding(horizontal = PropertyValueHorizontalPadding, vertical = PropertyValueVerticalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = date?.let { formatPropertyDate(it) } ?: "Empty",
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    date == null -> MaterialTheme.colorScheme.outline
                    isPastDueDate -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier.weight(1f, fill = false)
            )
            if (date != null && !inSelectionMode) {
                Icon(
                    painter = painterResource(Res.drawable.x),
                    contentDescription = "Clear date",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .clickable { onUpdateDate(null) }
                        .padding(4.dp)
                        .size(14.dp)
                )
            }
        }

        if (showDatePicker) {
            MinimalDatePickerDialog(
                initialTimestamp = date?.atStartOfDayIn(timeZone)?.toEpochMilliseconds(),
                onDismiss = { showDatePicker = false },
                onConfirm = { selectedMillis ->
                    onUpdateDate(Instant.fromEpochMilliseconds(selectedMillis).toLocalDateTime(timeZone).date)
                    showDatePicker = false
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PropertyTagsValue(
    block: PropertyBlock,
    inSelectionMode: Boolean,
    onUpdateTags: (List<String>) -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit,
    widthModifier: Modifier,
    tagTextStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    var showTagPicker by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = widthModifier
                .clickable(enabled = !inSelectionMode) { runAfterKeyboardCloses { showTagPicker = true } }
                .padding(horizontal = PropertyValueHorizontalPadding, vertical = PropertyValueVerticalPadding)
        ) {
            if (block.tags.isEmpty()) {
                Text(
                    text = "Empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    block.tags.forEach { tagName -> PropertyTagChip(tagName = tagName, textStyle = tagTextStyle) }
                }
            }
        }

        if (showTagPicker) {
            PropertyTagPicker(
                tagPoolKey = block.tagPoolKey,
                title = block.label,
                allowsManyTags = block.valueType.allowsManyTags,
                selectedTags = block.tags,
                onDismiss = { showTagPicker = false },
                onSelectedTagsChange = onUpdateTags
            )
        }
    }
}

@Composable
internal fun PropertyCheckboxValue(
    block: PropertyBlock,
    inSelectionMode: Boolean,
    onUpdateChecked: (Boolean) -> Unit,
    widthModifier: Modifier
) {
    Box(
        modifier = widthModifier
            .clickable(enabled = !inSelectionMode) { onUpdateChecked(!block.isChecked) }
            .padding(horizontal = PropertyValueHorizontalPadding, vertical = PropertyValueVerticalPadding)
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Checkbox(
                    checked = block.isChecked,
                    onCheckedChange = null,
                    modifier = Modifier.scale(0.9f).size(16.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.surface,
                        checkmarkColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    }
}

internal fun formatPropertyDate(date: LocalDate): String {
    val monthName = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$monthName ${date.day}, ${date.year}"
}

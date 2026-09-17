package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.data.local.room.TagEntity
import com.emberr.domain.model.CellData
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.TableCellStyle
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.displayText
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.domain.util.system.triggerHapticFeedback
import com.emberr.presentation.shared.editor.RichTextVisualTransformation
import com.emberr.presentation.shared.components.smoothWheelScroll
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.file_text
import emberr.shared.generated.resources.microphone
import emberr.shared.generated.resources.paperclip
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.square_arrow_out_up_right
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TableCell(
    cell: CellData?,
    cellKey: String,
    cellStyle: TableCellStyle,
    cellSpans: List<InlineSpan>,
    columnType: ColumnType,
    cellWidth: Dp,
    globalTags: List<TagEntity>,
    inSelectionMode: Boolean,
    currencySymbol: String = "$",
    isFormulaCurrency: Boolean = false,
    onValueChange: (CellData) -> Unit,
    onDateClick: () -> Unit,
    onTagClick: () -> Unit,
    onFileClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onStatusClick: () -> Unit,
    onNoteClick: () -> Unit,
    onNoteLinkClick: (String) -> Unit,
    onGetNoteTitle: suspend (String) -> String,
    allLinkableNotes: List<NoteMetadataEntity>,
    onCreateLinkedNote: (String) -> String,
    onLongPress: () -> Unit = {}
) {
    when (columnType) {
        ColumnType.TEXT, ColumnType.NUMBER, ColumnType.PHONE, ColumnType.EMAIL, ColumnType.URL, ColumnType.MONEY ->
            EditableTextCell(
                cell = cell,
                cellKey = cellKey,
                cellStyle = cellStyle,
                cellSpans = cellSpans,
                columnType = columnType,
                cellWidth = cellWidth,
                inSelectionMode = inSelectionMode,
                currencySymbol = currencySymbol,
                allLinkableNotes = allLinkableNotes,
                onValueChange = onValueChange,
                onNoteLinkClick = onNoteLinkClick,
                onCreateLinkedNote = onCreateLinkedNote
            )

        ColumnType.CHECKBOX -> CheckboxCell(cell, inSelectionMode, onValueChange)
        ColumnType.DATE -> DateCell(cell, inSelectionMode, onDateClick)
        ColumnType.FORMULA -> FormulaCell(cell, currencySymbol, isFormulaCurrency)
        ColumnType.TAGS -> TagsCell(cell, globalTags, inSelectionMode, onTagClick, onLongPress)
        ColumnType.FILES, ColumnType.AUDIO -> MediaCell(cell, columnType, inSelectionMode, onFileClick, onLongPress)
        ColumnType.PRIORITY -> ColoredChipCell(cell, inSelectionMode, ::priorityAccentColor, onPriorityClick, onLongPress)
        ColumnType.STATUS -> ColoredChipCell(cell, inSelectionMode, ::statusAccentColor, onStatusClick, onLongPress)
        ColumnType.NOTES -> NoteRelationCell(
            cell = cell,
            cellWidth = cellWidth,
            inSelectionMode = inSelectionMode,
            allLinkableNotes = allLinkableNotes,
            onGetNoteTitle = onGetNoteTitle,
            onNoteClick = onNoteClick,
            onLongPress = onLongPress
        )
    }
}

private fun ColumnType.isExternallyOpenable() =
    this == ColumnType.EMAIL || this == ColumnType.PHONE || this == ColumnType.URL

@Composable
private fun EditableTextCell(
    cell: CellData?,
    cellKey: String,
    cellStyle: TableCellStyle,
    cellSpans: List<InlineSpan>,
    columnType: ColumnType,
    cellWidth: Dp,
    inSelectionMode: Boolean,
    currencySymbol: String,
    allLinkableNotes: List<NoteMetadataEntity>,
    onValueChange: (CellData) -> Unit,
    onNoteLinkClick: (String) -> Unit,
    onCreateLinkedNote: (String) -> String
) {
    val uriHandler = LocalUriHandler.current
    val validNoteIds = remember(allLinkableNotes) { allLinkableNotes.map { it.noteId }.toSet() }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isNumeric = columnType == ColumnType.NUMBER || columnType == ColumnType.MONEY
    val value = if (isNumeric) {
        (cell as? CellData.Number)?.value
            ?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            ?: ""
    } else {
        (cell as? CellData.Text)?.value ?: ""
    }

    Box(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            if (!inSelectionMode) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (columnType == ColumnType.MONEY && (value.isNotBlank() || isFocused)) {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            TableCellTextEditor(
                initialText = value,
                columnType = columnType,
                allLinkableNotes = allLinkableNotes,
                visualTransformation = if (columnType == ColumnType.TEXT) {
                    RichTextVisualTransformation(
                        linkColor = MaterialTheme.colorScheme.primary,
                        fadedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        validNoteIds = validNoteIds,
                        inlineSpans = cellSpans
                    )
                } else VisualTransformation.None,
                inSelectionMode = inSelectionMode,
                cellKey = cellKey,
                cellStyle = cellStyle,
                focusRequester = focusRequester,
                onValueChange = { raw ->
                    onValueChange(
                        if (isNumeric) CellData.Number(raw.toDoubleOrNull()) else CellData.Text(raw)
                    )
                },
                onFocusChanged = { isFocused = it },
                onNoteLinkClick = onNoteLinkClick,
                onCreateLinkedNote = onCreateLinkedNote,
                modifier = Modifier.weight(1f).defaultMinSize(minWidth = cellWidth - 24.dp)
            )

            if (columnType.isExternallyOpenable() && value.isNotBlank() && !isFocused) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(Res.drawable.square_arrow_out_up_right),
                    contentDescription = "Open Link",
                    modifier = Modifier.size(16.dp).clickable {
                        val uri = when (columnType) {
                            ColumnType.EMAIL -> "mailto:$value"
                            ColumnType.PHONE -> "tel:$value"
                            ColumnType.URL ->
                                if (!value.startsWith("http://") && !value.startsWith("https://")) "https://$value"
                                else value
                            else -> null
                        }
                        if (uri != null) {
                            try {
                                uriHandler.openUri(uri)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CheckboxCell(cell: CellData?, inSelectionMode: Boolean, onValueChange: (CellData) -> Unit) {
    val isChecked = (cell as? CellData.Boolean)?.value ?: false
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = {
                    if (!inSelectionMode) {
                        triggerHapticFeedback()
                        onValueChange(CellData.Boolean(it))
                    }
                },
                modifier = Modifier.scale(0.9f).size(18.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.surface,
                    checkmarkColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun DateCell(cell: CellData?, inSelectionMode: Boolean, onDateClick: () -> Unit) {
    val value = cell.displayText()
    Text(
        text = value.ifEmpty { "—" },
        style = MaterialTheme.typography.bodyLarge,
        color = if (value.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !inSelectionMode) { onDateClick() }
    )
}

@Composable
private fun FormulaCell(cell: CellData?, currencySymbol: String, isFormulaCurrency: Boolean) {
    val value = (cell as? CellData.Formula)?.result ?: ""
    val formulaScrollState = rememberScrollState()
    val isInvalid = value.equals("NaN", ignoreCase = true) || value.startsWith("Error", ignoreCase = true)

    val displayValue = when {
        isInvalid -> ""
        isFormulaCurrency && value.toDoubleOrNull() != null -> {
            val number = value.toDouble()
            val formatted =
                if (number == number.toLong().toDouble()) number.toLong().toString()
                else ((number * 100.0).toLong() / 100.0).toString()
            "$currencySymbol$formatted"
        }
        else -> value
    }

    Text(
        text = displayValue,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.horizontalScroll(formulaScrollState).smoothWheelScroll(formulaScrollState, horizontal = true)
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TagsCell(
    cell: CellData?,
    globalTags: List<TagEntity>,
    inSelectionMode: Boolean,
    onTagClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val activeTagIds = (cell as? CellData.TagList)?.tagIds ?: emptyList()
    val activeTags = activeTagIds.mapNotNull { id -> globalTags.find { it.tagId == id } }

    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(
            onClick = { if (!inSelectionMode) onTagClick() },
            onLongClick = { if (!inSelectionMode) onLongPress() }
        )
    ) {
        if (activeTags.isEmpty()) {
            EmptyCellHint("Empty")
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
            ) {
                activeTags.forEach { tag ->
                    val tagColor = parseTagColor(tag.colorHex)
                    Surface(shape = RoundedCornerShape(4.dp), color = tagColor.copy(alpha = 0.15f)) {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = tagColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MediaCell(
    cell: CellData?,
    columnType: ColumnType,
    inSelectionMode: Boolean,
    onFileClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val resources = (cell as? CellData.MediaList)?.files ?: emptyList()

    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(
            onClick = { if (!inSelectionMode) onFileClick() },
            onLongClick = { if (!inSelectionMode) onLongPress() }
        )
    ) {
        if (resources.isEmpty()) {
            EmptyCellHint("Empty")
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                resources.forEach { resourceEntry ->
                    val cleanFileName = resourceEntry.fileName.substringAfterLast("/")
                    val resourceName = resourceEntry.originalName.ifBlank { cleanFileName }

                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painterResource(
                                    if (columnType == ColumnType.AUDIO) Res.drawable.microphone
                                    else Res.drawable.paperclip
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = resourceName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 100.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColoredChipCell(
    cell: CellData?,
    inSelectionMode: Boolean,
    accentColorFor: (String) -> Color?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val value = (cell as? CellData.Text)?.value ?: ""

    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(
            onClick = { if (!inSelectionMode) onClick() },
            onLongClick = { if (!inSelectionMode) onLongPress() }
        )
    ) {
        if (value.isBlank()) {
            EmptyCellHint("—", alpha = 0.65f)
        } else {
            val chipColor = accentColorFor(value) ?: MaterialTheme.colorScheme.outline
            Surface(shape = RoundedCornerShape(4.dp), color = chipColor.copy(alpha = 0.15f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = chipColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRelationCell(
    cell: CellData?,
    cellWidth: Dp,
    inSelectionMode: Boolean,
    allLinkableNotes: List<NoteMetadataEntity>,
    onGetNoteTitle: suspend (String) -> String,
    onNoteClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val noteId = (cell as? CellData.NoteRelation)?.noteIds?.firstOrNull() ?: ""
    val reactiveNote = allLinkableNotes.find { it.noteId == noteId }
    var noteTitle by remember(noteId) { mutableStateOf("Loading...") }

    LaunchedEffect(noteId, reactiveNote) {
        if (noteId.isNotBlank()) {
            noteTitle = (reactiveNote?.title ?: onGetNoteTitle(noteId)).ifBlank { "Untitled Note" }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(
            onClick = { if (!inSelectionMode) onNoteClick() },
            onLongClick = { if (!inSelectionMode) onLongPress() }
        ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (noteId.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(Res.drawable.plus),
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(4.dp))
                EmptyCellHint("New Note", alpha = 0.6f)
            }
        } else {
            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painterResource(Res.drawable.file_text),
                        null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = noteTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = cellWidth - 45.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCellHint(text: String, alpha: Float = 0.5f) {
    Text(
        text,
        color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
        style = MaterialTheme.typography.labelSmall
    )
}

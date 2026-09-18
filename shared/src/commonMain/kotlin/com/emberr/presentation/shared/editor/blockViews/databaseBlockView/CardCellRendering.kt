package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.displayText
import com.emberr.domain.util.system.triggerHapticFeedback
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.file_text
import org.jetbrains.compose.resources.painterResource

internal fun cardCellText(
    cell: CellData?,
    columnType: ColumnType,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>
): String = when (columnType) {
    ColumnType.TAGS -> (cell as? CellData.TagList)?.tagIds
        ?.mapNotNull { id -> globalTags.find { it.tagId == id }?.name }
        ?.joinToString(", ")
        .orEmpty()
    ColumnType.NOTES -> (cell as? CellData.NoteRelation)?.noteIds?.firstOrNull()
        ?.let { noteId -> allLinkableNotes.find { it.noteId == noteId }?.title }
        .orEmpty()
    else -> cell.displayText()
}

internal val NOTE_LINK_REGEX = """\[([^\]]+)\]\(emberr://note/([^)]+)\)""".toRegex()
private const val NOTE_LINK_TAG = "NOTE_LINK"

fun buildNoteLinkAnnotatedString(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in NOTE_LINK_REGEX.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        val (title, noteId) = match.destructured
        pushStringAnnotation(NOTE_LINK_TAG, noteId)
        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
            append("@$title")
        }
        pop()
        lastIndex = match.range.last + 1
    }
    append(text.substring(lastIndex))
}

@Composable
internal fun NoteLinkText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    maxLines: Int,
    onNoteLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration? = null
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { buildNoteLinkAnnotatedString(text, linkColor) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize, fontWeight = fontWeight),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textDecoration = textDecoration,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures { tapOffset ->
                val result = layoutResult ?: return@detectTapGestures
                val charOffset = result.getOffsetForPosition(tapOffset)
                result.layoutInput.text
                    .getStringAnnotations(NOTE_LINK_TAG, charOffset, charOffset)
                    .firstOrNull()
                    ?.let { onNoteLinkClick(it.item) }
            }
        }
    )
}

@Composable
internal fun NoteRelationChip(
    noteId: String,
    allLinkableNotes: List<NoteMetadataEntity>,
    getNoteTitle: suspend (String) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reactiveNote = allLinkableNotes.find { it.noteId == noteId }
    var noteTitle by remember(noteId, reactiveNote) {
        mutableStateOf(reactiveNote?.title?.ifBlank { "Untitled Note" } ?: "Loading...")
    }

    LaunchedEffect(noteId, reactiveNote) {
        if (reactiveNote == null) {
            noteTitle = getNoteTitle(noteId).ifBlank { "Untitled Note" }
        }
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        modifier = modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.file_text),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = noteTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CheckboxCellValue(
    checked: Boolean,
    inSelectionMode: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                if (!inSelectionMode) {
                    triggerHapticFeedback()
                    onCheckedChange(it)
                }
            },
            modifier = modifier.scale(0.9f).size(18.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.surface,
                checkmarkColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

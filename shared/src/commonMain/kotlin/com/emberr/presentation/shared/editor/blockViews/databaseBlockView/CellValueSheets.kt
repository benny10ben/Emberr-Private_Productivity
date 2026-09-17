@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import com.emberr.presentation.shared.components.SelectedOptionBackground
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DEFAULT_STATUS_OPTIONS
import com.emberr.presentation.shared.components.EmberrTextField
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.check
import emberr.shared.generated.resources.chevron_right
import emberr.shared.generated.resources.link
import emberr.shared.generated.resources.microphone
import emberr.shared.generated.resources.pause
import emberr.shared.generated.resources.play
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.square
import emberr.shared.generated.resources.x
import org.jetbrains.compose.resources.painterResource

private val NEW_TAG_PALETTE = listOf(
    "#E03E3E", "#D9730D", "#DFAB01", "#0F7B6C", "#0B6E99", "#6940A5", "#9065B0"
)

@Composable
internal fun PickTagsSheet(context: DatabaseSheetContext) {
    val state = context.state
    val row = context.block.rows.find { it.id == state.activeRowId } ?: return
    var tagSearchQuery by remember { mutableStateOf("") }

    val currentTagIds = (row.cells[state.activeColId] as? CellData.TagList)?.tagIds?.toMutableSet() ?: mutableSetOf()

    fun commitTags() {
        val columnId = state.activeColId ?: return
        context.actions.onUpdateDbCell(context.block.id, row.id, columnId, CellData.TagList(currentTagIds.toList()))
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = tagSearchQuery,
            onValueChange = { tagSearchQuery = it },
            placeholder = "Search or create a tag...",
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(12.dp))

    val filteredTags = context.globalTags.filter { it.name.contains(tagSearchQuery, ignoreCase = true) }
    val exactMatchExists = context.globalTags.any { it.name.equals(tagSearchQuery.trim(), ignoreCase = true) }

    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        if (tagSearchQuery.isNotBlank() && !exactMatchExists) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (state.activeColId == null) return@clickable
                        val newTagId = context.actions.onCreateGlobalTag(tagSearchQuery.trim(), NEW_TAG_PALETTE.random())
                        currentTagIds.add(newTagId)
                        commitTags()
                        tagSearchQuery = ""
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(Res.drawable.plus),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Create \"${tagSearchQuery.trim()}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        filteredTags.forEach { tag ->
            val isSelected = currentTagIds.contains(tag.tagId)
            val tagColor = parseTagColor(tag.colorHex)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isSelected) currentTagIds.remove(tag.tagId) else currentTagIds.add(tag.tagId)
                        commitTags()
                    }
                    .padding(vertical = 10.dp)
                    .padding(end = if (isSelected) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(shape = RoundedCornerShape(4.dp), color = tagColor.copy(alpha = 0.15f)) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = tagColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun AttachedFilesSheet(context: DatabaseSheetContext) {
    val state = context.state
    val row = context.block.rows.find { it.id == state.activeRowId } ?: return
    val column = context.activeColumn ?: return
    val actions = context.actions
    val blockId = context.block.id

    val currentFiles = (row.cells[state.activeColId] as? CellData.MediaList)?.files?.toMutableList() ?: mutableListOf()
    val isAudioColumn = column.type == ColumnType.AUDIO

    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        if (isAudioColumn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (state.isRecording) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(if (state.isRecording) Res.drawable.square else Res.drawable.microphone),
                    contentDescription = null,
                    tint = if (state.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp).clickable {
                        if (state.isRecording) {
                            state.isRecording = false
                            actions.onStopDbAudioRecording(blockId, row.id, column.id, false)
                        } else {
                            state.isRecording = true
                            actions.onStartRecording()
                        }
                    }
                )
                Spacer(Modifier.width(12.dp))
                if (state.isRecording) {
                    val minutes = state.recordingDuration / 60
                    val seconds = state.recordingDuration % 60
                    Text(
                        text = "Recording... $minutes:${seconds.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "Tap mic to record audio",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            SheetDivider()
        }

        currentFiles.forEach { resourceEntry ->
            val cleanFileName = resourceEntry.fileName.substringAfterLast("/")
            val resourceName = resourceEntry.originalName.ifBlank { cleanFileName }
            val isPlaying = state.playingFileUri == cleanFileName

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isAudioColumn) {
                            actions.onOpenFile(cleanFileName, "*/*")
                        } else if (isPlaying) {
                            state.playingFileUri = null
                            actions.onStopAudio()
                        } else {
                            if (state.playingFileUri != null) actions.onStopAudio()
                            state.playingFileUri = cleanFileName
                            actions.onPlayAudio(cleanFileName) {
                                if (state.playingFileUri == cleanFileName) state.playingFileUri = null
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon: Painter = when {
                        !isAudioColumn -> painterResource(Res.drawable.link)
                        isPlaying -> painterResource(Res.drawable.pause)
                        else -> painterResource(Res.drawable.play)
                    }

                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = resourceName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                }

                Icon(
                    painterResource(Res.drawable.x),
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp).clickable {
                        if (isPlaying) {
                            state.playingFileUri = null
                            actions.onStopAudio()
                        }
                        currentFiles.remove(resourceEntry)
                        actions.onUpdateDbCell(blockId, row.id, column.id, CellData.MediaList(currentFiles.toList()))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    state.applyAction { actions.onRequestDbFilePicker(blockId, row.id, column.id, isAudioColumn) }
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(Res.drawable.plus),
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isAudioColumn) "Upload audio track" else "Attach a new file",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun PickPrioritySheet(context: DatabaseSheetContext) {
    ColoredValuePickerSheet(context, PRIORITY_LEVELS) { priorityAccentColor(it) }
}

@Composable
internal fun PickStatusSheet(context: DatabaseSheetContext) {
    ColoredValuePickerSheet(context, DEFAULT_STATUS_OPTIONS) { statusAccentColor(it) }
}

@Composable
private fun ColoredValuePickerSheet(
    context: DatabaseSheetContext,
    options: List<String>,
    accentColorFor: (String) -> Color?
) {
    val state = context.state
    val row = context.block.rows.find { it.id == state.activeRowId } ?: return
    val current = (row.cells[state.activeColId] as? CellData.Text)?.value ?: ""

    fun commit(value: String) {
        val columnId = state.activeColId ?: return
        state.applyAction { context.actions.onUpdateDbCell(context.block.id, row.id, columnId, CellData.Text(value)) }
    }

    options.forEach { label ->
        val color = accentColorFor(label) ?: MaterialTheme.colorScheme.outline
        Row(
            modifier = Modifier.fillMaxWidth().clickable { commit(label) }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            if (current == label) {
                Icon(
                    painterResource(Res.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (current.isNotBlank()) {
        SheetDivider()
        SheetMenuRow(
            painterResource(Res.drawable.x),
            text = "Clear",
            color = MaterialTheme.colorScheme.error
        ) { commit("") }
    }
    Spacer(Modifier.height(8.dp))
}

private val COUNT_AGGREGATIONS = listOf("Count all", "Count values", "Count unique", "Count empty", "Count not empty")
private val PERCENT_AGGREGATIONS = listOf("Percent empty", "Percent not empty")
private val NUMERIC_AGGREGATIONS = listOf("Sum", "Average", "Min", "Max", "Median", "Range")

@Composable
internal fun CalculateSheet(context: DatabaseSheetContext) {
    val state = context.state
    val column = context.activeColumn ?: return
    val supportsNumericAggregations =
        column.type == ColumnType.NUMBER || column.type == ColumnType.FORMULA || column.type == ColumnType.MONEY
    val currentAggregation = column.aggregationType ?: "None"

    fun commit(aggregation: String?) {
        state.close()
        context.actions.onUpdateDbAggregation(context.block.id, column.id, aggregation)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize()
    ) {
        val isNoneSelected = currentAggregation == "None"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isNoneSelected) SelectedOptionBackground else Color.Transparent)
                .clickable { commit(null) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "None",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isNoneSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }

        val groups = buildList {
            add("Count" to COUNT_AGGREGATIONS)
            add("Percent" to PERCENT_AGGREGATIONS)
            if (supportsNumericAggregations) add("More options" to NUMERIC_AGGREGATIONS)
        }

        groups.forEach { (groupName, options) ->
            val isExpanded = state.aggregationExpandedSection == groupName

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.aggregationExpandedSection = if (isExpanded) null else groupName }
                    .padding(vertical = 12.dp)
                    .padding(end = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(groupName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                val rotation by animateFloatAsState(if (isExpanded) -90f else 90f)
                Icon(
                    painterResource(Res.drawable.chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp).rotate(rotation)
                )
            }

            if (isExpanded) {
                options.forEach { option ->
                    val isSelected = currentAggregation == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SelectedOptionBackground else Color.Transparent)
                            .clickable { commit(option) }
                            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

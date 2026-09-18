@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.DatabaseTemplateEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DEFAULT_STATUS_OPTIONS
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseRow
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.ViewType
import com.emberr.domain.model.displayText
import com.emberr.domain.model.propertyLabel
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.components.SelectedOptionBackground
import com.emberr.presentation.shared.components.rememberShowAfterKeyboardCloses
import com.emberr.presentation.shared.editor.EditorActions
import dev.chrisbanes.haze.HazeState
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_left
import emberr.shared.generated.resources.arrow_right
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.arrow_up_down
import emberr.shared.generated.resources.badge_dollar_sign
import emberr.shared.generated.resources.bookmark
import emberr.shared.generated.resources.check
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.chevron_left
import emberr.shared.generated.resources.chevron_right
import emberr.shared.generated.resources.files
import emberr.shared.generated.resources.funnel
import emberr.shared.generated.resources.group
import emberr.shared.generated.resources.hash
import emberr.shared.generated.resources.link
import emberr.shared.generated.resources.maximize_2
import emberr.shared.generated.resources.microphone
import emberr.shared.generated.resources.minimize_2
import emberr.shared.generated.resources.minus
import emberr.shared.generated.resources.move_left
import emberr.shared.generated.resources.move_right
import emberr.shared.generated.resources.pause
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.play
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.sigma
import emberr.shared.generated.resources.slider_h2
import emberr.shared.generated.resources.sliders_horizontal
import emberr.shared.generated.resources.square
import emberr.shared.generated.resources.square_check
import emberr.shared.generated.resources.square_kanban
import emberr.shared.generated.resources.table
import emberr.shared.generated.resources.transfer_h
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.widget
import emberr.shared.generated.resources.x
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DatabaseBlockView(
    block: DatabaseBlock,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val sheetState = rememberDatabaseSheetState(block.id, actions)
    var showDatePicker by remember { mutableStateOf(false) }

    val visibleColumns = remember(block.columns) { block.columns.filter { !it.isDeleted } }

    val activeView = remember(block.views, block.activeViewId) {
        block.views.find { it.id == block.activeViewId }
            ?: block.views.firstOrNull()
            ?: DatabaseView(id = "", name = "Table", type = ViewType.TABLE)
    }

    val visibleRows = remember(block.rows, block.columns, activeView.activeSorts, activeView.activeFilters) {
        applyFiltersAndSorts(block.rows, block.columns, activeView)
    }

    val sheetContext = DatabaseSheetContext(
        block = block,
        activeView = activeView,
        visibleColumns = visibleColumns,
        globalTags = globalTags,
        actions = actions,
        state = sheetState
    )

    val desktopDropdown = @Composable { visible: Boolean ->
        if (isDesktopPlatform && visible) {
            DesktopOptionMenu(sheetContext)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (inSelectionMode) actions.onToggleSelection(block.id) },
                onLongClick = { actions.onToggleSelection(block.id) }
            )
    ) {
        DatabaseTitleField(block, inSelectionMode, actions)

        DatabaseViewTabsRow(
            block = block,
            activeView = activeView,
            visibleColumns = visibleColumns,
            inSelectionMode = inSelectionMode,
            state = sheetState,
            actions = actions,
            desktopDropdown = desktopDropdown
        )

        DatabaseActiveFilterChips(block, activeView, visibleColumns, inSelectionMode, actions)

        when (activeView.type) {
            ViewType.TABLE -> TableView(
                block = block,
                activeView = activeView,
                visibleColumns = visibleColumns,
                visibleRows = visibleRows,
                inSelectionMode = inSelectionMode,
                globalTags = globalTags,
                allLinkableNotes = allLinkableNotes,
                actions = actions,
                hazeState = hazeState,
                scrollState = scrollState,
                coroutineScope = coroutineScope,
                focusManager = focusManager,
                currentSheet = sheetState.currentSheet,
                activeColId = sheetState.activeColId,
                activeRowId = sheetState.activeRowId,
                onOpenSheet = { sheet, rowId, colId ->
                    sheetState.activeRowId = rowId
                    sheetState.activeColId = colId
                    sheetState.open(sheet)
                },
                onOpenDatePicker = { rowId, colId ->
                    sheetState.close()
                    sheetState.activeRowId = rowId
                    sheetState.activeColId = colId
                    showDatePicker = true
                },
                desktopDropdown = desktopDropdown
            )

            ViewType.KANBAN -> KanbanView(
                blockId = block.id,
                activeView = activeView,
                visibleColumns = visibleColumns,
                visibleRows = visibleRows,
                inSelectionMode = inSelectionMode,
                globalTags = globalTags,
                allLinkableNotes = allLinkableNotes,
                actions = actions,
                onOpenGroupBySheet = { sheetState.open(DatabaseSheet.GROUP_BY) }
            )

            ViewType.GALLERY -> GalleryView(
                blockId = block.id,
                cardSize = activeView.galleryCardSize,
                visibleColumns = visibleColumns,
                visibleRows = visibleRows,
                inSelectionMode = inSelectionMode,
                globalTags = globalTags,
                allLinkableNotes = allLinkableNotes,
                actions = actions
            )
        }
    }

    if (!isDesktopPlatform) {
        MobileSheetStack(sheetContext)
    }

    val datePickerRowId = sheetState.activeRowId
    val datePickerColId = sheetState.activeColId
    if (showDatePicker && datePickerRowId != null && datePickerColId != null) {
        val initialTimestamp =
            (block.rows.find { it.id == datePickerRowId }?.cells?.get(datePickerColId) as? CellData.Date)?.timestamp

        MinimalDatePickerDialog(
            expanded = true,
            initialTimestamp = initialTimestamp,
            onDismiss = { showDatePicker = false },
            onConfirm = { millis ->
                showDatePicker = false
                coroutineScope.launch {
                    try {
                        delay(150.milliseconds)
                    } finally {
                        actions.onUpdateDbCell(block.id, datePickerRowId, datePickerColId, CellData.Date(millis))
                    }
                }
            }
        )
    }
}

@Composable
private fun DesktopOptionMenu(context: DatabaseSheetContext) {
    EmberrDesktopMenu(expanded = true, onDismissRequest = { context.state.close() }) {
        AnimatedContent(
            targetState = context.state.currentSheet,
            transitionSpec = {
                val isGoingDeeper = targetState !in listOf(
                    DatabaseSheet.COLUMN_OPTIONS,
                    DatabaseSheet.CELL_OPTIONS,
                    DatabaseSheet.NONE
                )
                if (isGoingDeeper) {
                    (slideInHorizontally(tween(200)) { it } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 2 } + fadeOut(tween(200))) using
                            SizeTransform(clip = false)
                } else {
                    (slideInHorizontally(tween(200)) { -it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200))) using
                            SizeTransform(clip = false)
                }
            },
            label = "DesktopDbTransition"
        ) { target ->
            Box(modifier = Modifier.widthIn(min = 280.dp, max = 340.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
                OptionSheetBody(context, target)
            }
        }
    }
}

private val SHEET_TYPES_WITH_OWN_DISMISS_BUTTON = setOf(
    DatabaseSheet.RENAME,
    DatabaseSheet.FORMULA,
    DatabaseSheet.RENAME_VIEW,
    DatabaseSheet.SAVE_AS_TEMPLATE,
    DatabaseSheet.FILTER,
    DatabaseSheet.SORT
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSheetStack(context: DatabaseSheetContext) {
    context.state.openSheets.forEachIndexed { index, sheetType ->
        key(index) {
            EmberrBottomSheet(
                expanded = true,
                onDismiss = { context.state.pop() },
                title = context.sheetTitleFor(sheetType)
            ) { _ ->
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    OptionSheetBody(context, sheetType)

                    if (sheetType !in SHEET_TYPES_WITH_OWN_DISMISS_BUTTON) {
                        EmberrButtonPrimary(
                            text = "Close",
                            onClick = { context.state.pop() },
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

internal enum class DatabaseSheet {
    NONE,

    CELL_OPTIONS,
    COLUMN_OPTIONS,
    RENAME,
    FORMULA,
    CURRENCY_SELECTION,
    AGGREGATION,

    RENAME_VIEW,
    ADD_VIEW,
    TABLE_SETTINGS,
    SORT,
    FILTER,
    GROUP_BY,
    CARD_SIZE,
    SAVE_AS_TEMPLATE,

    TAG_SELECTION,
    FILE_OPTIONS,
    PRIORITY_SELECTION,
    STATUS_SELECTION
}

@Stable
private class DatabaseSheetState(
    private val blockId: String,
    private val scope: CoroutineScope,
    private val latestActions: () -> EditorActions
) {
    private val sheetStack = mutableStateListOf<DatabaseSheet>()

    val openSheets: List<DatabaseSheet> get() = sheetStack
    val currentSheet: DatabaseSheet get() = sheetStack.lastOrNull() ?: DatabaseSheet.NONE

    var activeColId by mutableStateOf<String?>(null)
    var activeRowId by mutableStateOf<String?>(null)
    var renamingViewId by mutableStateOf<String?>(null)

    var textInput by mutableStateOf("")
    var textInputMax by mutableStateOf("")
    var filterOperator by mutableStateOf("contains")
    var filterPriority by mutableStateOf("")
    var aggregationExpandedSection by mutableStateOf<String?>(null)

    var isRecording by mutableStateOf(false)
    var recordingDuration by mutableIntStateOf(0)
    var playingFileUri by mutableStateOf<String?>(null)

    fun open(sheet: DatabaseSheet) {
        sheetStack.add(sheet)
    }

    fun close() {
        stopMediaSideEffects()
        sheetStack.clear()
        clearTransientSelection()
    }

    fun pop() {
        if (currentSheet == DatabaseSheet.FILE_OPTIONS) stopMediaSideEffects()
        if (sheetStack.isNotEmpty()) sheetStack.removeAt(sheetStack.lastIndex)
        if (sheetStack.isEmpty()) clearTransientSelection()
    }

    fun dismissCurrentSheet() {
        if (isDesktopPlatform) close() else pop()
    }

    fun applyAction(action: () -> Unit) {
        close()
        scope.launch {
            try {
                delay(250.milliseconds)
            } finally {
                action()
            }
        }
    }

    fun openForColumn(columnId: String, sheet: DatabaseSheet) {
        activeColId = columnId
        open(sheet)
    }

    private fun clearTransientSelection() {
        activeRowId = null
        renamingViewId = null
        aggregationExpandedSection = null
    }

    private fun stopMediaSideEffects() {
        val rowId = activeRowId
        val colId = activeColId
        if (isRecording && rowId != null && colId != null) {
            isRecording = false
            latestActions().onStopDbAudioRecording(blockId, rowId, colId, true)
        }
        if (playingFileUri != null) {
            playingFileUri = null
            latestActions().onStopAudio()
        }
    }
}

@Composable
private fun rememberDatabaseSheetState(blockId: String, actions: EditorActions): DatabaseSheetState {
    val scope = rememberCoroutineScope()
    val currentActions by rememberUpdatedState(actions)
    val state = remember(blockId) { DatabaseSheetState(blockId, scope) { currentActions } }

    LaunchedEffect(state.isRecording) {
        if (state.isRecording) {
            state.recordingDuration = 0
            while (state.isRecording) {
                delay(1000.milliseconds)
                state.recordingDuration++
            }
        }
    }

    return state
}

@Stable
private class DatabaseSheetContext(
    val block: DatabaseBlock,
    val activeView: DatabaseView,
    val visibleColumns: List<DatabaseColumn>,
    val globalTags: List<TagEntity>,
    val actions: EditorActions,
    val state: DatabaseSheetState
) {
    val activeColumn: DatabaseColumn? get() = visibleColumns.find { it.id == state.activeColId }
}

private fun DatabaseSheetContext.sheetTitleFor(sheet: DatabaseSheet): String? = when (sheet) {
    DatabaseSheet.CELL_OPTIONS -> "Cell Actions"
    DatabaseSheet.COLUMN_OPTIONS -> activeColumn?.name ?: "Column Options"
    DatabaseSheet.RENAME -> "Rename Column"
    DatabaseSheet.RENAME_VIEW -> "Rename View"
    DatabaseSheet.FORMULA -> "Edit Formula"
    DatabaseSheet.CURRENCY_SELECTION -> "Select Currency"
    DatabaseSheet.SORT -> "Sort by"
    DatabaseSheet.FILTER -> "Filter"
    DatabaseSheet.GROUP_BY -> "Group By"
    DatabaseSheet.CARD_SIZE -> "Card Size"
    DatabaseSheet.FILE_OPTIONS -> "Attached Files"
    DatabaseSheet.PRIORITY_SELECTION -> "Set Priority"
    DatabaseSheet.STATUS_SELECTION -> "Set Status"
    DatabaseSheet.AGGREGATION -> "Calculate"
    DatabaseSheet.TAG_SELECTION -> "Select Tag"
    DatabaseSheet.SAVE_AS_TEMPLATE -> "Save as Template"
    DatabaseSheet.ADD_VIEW -> "Add View"
    DatabaseSheet.TABLE_SETTINGS -> "Table Settings"
    else -> null
}

private fun parentSheetOnDesktop(sheet: DatabaseSheet): DatabaseSheet? = when (sheet) {
    DatabaseSheet.RENAME, DatabaseSheet.FORMULA, DatabaseSheet.CURRENCY_SELECTION ->
        DatabaseSheet.COLUMN_OPTIONS

    DatabaseSheet.FILTER, DatabaseSheet.SAVE_AS_TEMPLATE, DatabaseSheet.ADD_VIEW,
    DatabaseSheet.GROUP_BY, DatabaseSheet.CARD_SIZE ->
        DatabaseSheet.TABLE_SETTINGS

    else -> null
}

@Composable
private fun SheetCancelAndConfirmButtons(
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().sheetSidePadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EmberrButtonSecondary(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
        EmberrButtonPrimary(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OptionSheetBody(context: DatabaseSheetContext, targetSheet: DatabaseSheet) {
    MuteRippleOnMobile {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isDesktopPlatform) {
                DesktopSheetHeader(context, targetSheet)
            }

            when (targetSheet) {
                DatabaseSheet.CELL_OPTIONS -> CellActionsSheet(context)
                DatabaseSheet.COLUMN_OPTIONS -> ColumnOptionsSheet(context)
                DatabaseSheet.RENAME -> RenameColumnSheet(context)
                DatabaseSheet.FORMULA -> EditFormulaSheet(context)
                DatabaseSheet.CURRENCY_SELECTION -> PickCurrencySheet(context)

                DatabaseSheet.RENAME_VIEW -> RenameViewSheet(context)
                DatabaseSheet.SAVE_AS_TEMPLATE -> SaveAsTemplateSheet(context)
                DatabaseSheet.ADD_VIEW -> AddViewSheet(context)
                DatabaseSheet.TABLE_SETTINGS -> TableSettingsSheet(context)
                DatabaseSheet.SORT -> SortSheet(context)
                DatabaseSheet.FILTER -> FilterSheet(context)
                DatabaseSheet.GROUP_BY -> GroupBySheet(context)
                DatabaseSheet.CARD_SIZE -> CardSizeSheet(context)

                DatabaseSheet.TAG_SELECTION -> PickTagsSheet(context)
                DatabaseSheet.FILE_OPTIONS -> AttachedFilesSheet(context)
                DatabaseSheet.PRIORITY_SELECTION -> PickPrioritySheet(context)
                DatabaseSheet.STATUS_SELECTION -> PickStatusSheet(context)
                DatabaseSheet.AGGREGATION -> CalculateSheet(context)

                DatabaseSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun DesktopSheetHeader(context: DatabaseSheetContext, targetSheet: DatabaseSheet) {
    val backTarget = parentSheetOnDesktop(targetSheet)
    if (backTarget != null) {
        SheetMenuRow(painterResource(Res.drawable.chevron_left), "Back to Options") {
            context.state.open(backTarget)
        }
        SheetDivider()
    }

    val title = context.sheetTitleFor(targetSheet)
    if (!title.isNullOrBlank()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp, top = 8.dp).sheetSidePadding()
        )
    }
}

private object NoRippleIndicationNodeFactory : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode(): Int = -1
}

private val sheetSideInset get() = if (isDesktopPlatform) 12.dp else 0.dp

private fun Modifier.sheetSidePadding(): Modifier = padding(horizontal = sheetSideInset)

@Composable
private fun MuteRippleOnMobile(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides if (isDesktopPlatform) ripple() else NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides if (isDesktopPlatform) LocalRippleConfiguration.current else null,
        content = content
    )
}

@Composable
private fun SheetDivider(verticalPadding: Dp = 12.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = verticalPadding),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    )
}

@Composable
internal fun parseTagColor(colorHex: String): Color = try {
    Color(colorHex.removePrefix("#").toLong(16) or 0xFF000000)
} catch (_: Exception) {
    MaterialTheme.colorScheme.primary
}

@Composable
private fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = modifier
    )
}

@Composable
fun SheetMenuRow(
    icon: Painter,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SelectedOptionBackground else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DatabaseTitleField(block: DatabaseBlock, inSelectionMode: Boolean, actions: EditorActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var titleTfv by remember(block.id) {
            mutableStateOf(TextFieldValue(block.title, TextRange(block.title.length)))
        }
        var lastSentTitle by remember(block.id) { mutableStateOf(block.title) }

        LaunchedEffect(block.title) {
            if (titleTfv.text != block.title && block.title != lastSentTitle) {
                titleTfv = titleTfv.copy(
                    text = block.title,
                    selection = TextRange(titleTfv.selection.start.coerceAtMost(block.title.length))
                )
            }
        }

        LaunchedEffect(titleTfv.text) {
            if (titleTfv.text != block.title) {
                delay(400L.milliseconds)
                lastSentTitle = titleTfv.text
                actions.onUpdateDbTitle(block.id, titleTfv.text)
            }
        }

        BasicTextField(
            value = titleTfv,
            onValueChange = { titleTfv = it },
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (titleTfv.text.isEmpty()) {
                    Text(
                        text = "Untitled Database",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                inner()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !inSelectionMode,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DatabaseViewTabsRow(
    block: DatabaseBlock,
    activeView: DatabaseView,
    visibleColumns: List<DatabaseColumn>,
    inSelectionMode: Boolean,
    state: DatabaseSheetState,
    actions: EditorActions,
    desktopDropdown: @Composable (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val orderedViews = remember(block.views) { mutableStateListOf(*block.views.toTypedArray()) }
        var draggedViewId by remember { mutableStateOf<String?>(null) }
        var dragStartIndex by remember { mutableStateOf(-1) }
        var dragPointerX by remember { mutableStateOf(0f) }
        val viewBoundsInWindow = remember { mutableStateMapOf<String, Rect>() }

        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            orderedViews.forEach { view ->
                key(view.id) {
                    val isActive = view.id == activeView.id
                    val isDragged = draggedViewId == view.id

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { viewBoundsInWindow[view.id] = it.boundsInWindow() }
                            .graphicsLayer { alpha = if (isDragged) 0.6f else 1f }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                            modifier = Modifier
                                .clickable(enabled = !inSelectionMode) {
                                    if (isActive) {
                                        state.textInput = view.name
                                        state.renamingViewId = view.id
                                        state.open(DatabaseSheet.RENAME_VIEW)
                                    } else {
                                        actions.onSetActiveDatabaseView(block.id, view.id)
                                    }
                                }
                                .pointerInput(view.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedViewId = view.id
                                            dragStartIndex = block.views.indexOfFirst { it.id == view.id }
                                            dragPointerX = viewBoundsInWindow[view.id]?.center?.x ?: 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragPointerX += dragAmount.x
                                            val hovered = viewBoundsInWindow.entries
                                                .firstOrNull { (_, rect) -> dragPointerX in rect.left..rect.right }
                                                ?.key
                                            if (hovered != null && hovered != view.id) {
                                                val from = orderedViews.indexOfFirst { it.id == view.id }
                                                val to = orderedViews.indexOfFirst { it.id == hovered }
                                                if (from != -1 && to != -1) {
                                                    orderedViews.add(to, orderedViews.removeAt(from))
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedViewId = null
                                            val finalIndex = orderedViews.indexOfFirst { it.id == view.id }
                                            if (dragStartIndex != -1 && finalIndex != -1 && dragStartIndex != finalIndex) {
                                                actions.onReorderDatabaseViews(block.id, dragStartIndex, finalIndex)
                                            }
                                            dragStartIndex = -1
                                        },
                                        onDragCancel = {
                                            draggedViewId = null
                                            dragStartIndex = -1
                                        }
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(view.type.tabIcon()),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isActive) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = view.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        desktopDropdown(state.renamingViewId == view.id && state.currentSheet == DatabaseSheet.RENAME_VIEW)
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                val hasSort = activeView.activeSorts.isNotEmpty()
                ToolbarIconButton(
                    iconRes = Res.drawable.arrow_up_down,
                    contentDescription = null,
                    isHighlighted = hasSort,
                    enabled = !inSelectionMode
                ) {
                    val firstColumn = visibleColumns.firstOrNull() ?: return@ToolbarIconButton
                    state.openForColumn(
                        activeView.activeSorts.firstOrNull()?.columnId ?: firstColumn.id,
                        DatabaseSheet.SORT
                    )
                }
                desktopDropdown(state.currentSheet == DatabaseSheet.SORT)
            }

            Box {
                val hasFilter = activeView.activeFilters.isNotEmpty()
                val hasGroupBy = activeView.type == ViewType.KANBAN && activeView.groupByColumnId != null
                val hasCardSize =
                    activeView.type == ViewType.GALLERY && activeView.galleryCardSize != GalleryCardSize.MEDIUM
                ToolbarIconButton(
                    iconRes = Res.drawable.sliders_horizontal,
                    contentDescription = "Table settings",
                    isHighlighted = hasFilter || hasGroupBy || hasCardSize,
                    enabled = !inSelectionMode
                ) { state.open(DatabaseSheet.TABLE_SETTINGS) }

                desktopDropdown(
                    state.currentSheet in listOf(
                        DatabaseSheet.TABLE_SETTINGS,
                        DatabaseSheet.FILTER,
                        DatabaseSheet.SAVE_AS_TEMPLATE,
                        DatabaseSheet.ADD_VIEW,
                        DatabaseSheet.GROUP_BY,
                        DatabaseSheet.CARD_SIZE
                    )
                )
            }
        }
    }
}

private fun ViewType.tabIcon() = when (this) {
    ViewType.TABLE -> Res.drawable.table
    ViewType.KANBAN -> Res.drawable.square_kanban
    ViewType.GALLERY -> Res.drawable.widget
}

@Composable
private fun ToolbarIconButton(
    iconRes: DrawableResource,
    contentDescription: String?,
    isHighlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DatabaseActiveFilterChips(
    block: DatabaseBlock,
    activeView: DatabaseView,
    visibleColumns: List<DatabaseColumn>,
    inSelectionMode: Boolean,
    actions: EditorActions
) {
    if (activeView.activeFilters.isEmpty()) return

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        activeView.activeFilters.forEach { filter ->
            val columnName = visibleColumns.find { it.id == filter.columnId }?.name ?: "?"

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filterChipLabel(filter, columnName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(Res.drawable.x),
                        contentDescription = null,
                        modifier = Modifier
                            .size(13.dp)
                            .clickable(enabled = !inSelectionMode) { actions.onRemoveDbFilter(block.id, filter) },
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private val PRIORITY_ACCENT_COLORS = mapOf(
    "Low" to Color(0xFF7FB3D5),
    "Medium" to Color(0xFFF2C14E),
    "High" to Color(0xFFE8873A),
    "Urgent" to Color(0xFFE0574F)
)

private val PRIORITY_LEVELS = listOf("Low", "Medium", "High", "Urgent")

internal fun priorityAccentColor(priority: String): Color? = PRIORITY_ACCENT_COLORS[priority]

private val PRIORITY_SORT_WEIGHTS = mapOf("Low" to 1, "Medium" to 2, "High" to 3, "Urgent" to 4)

private fun DatabaseRow.matches(filter: FilterConfig): Boolean {
    val cellVal = cells[filter.columnId].displayText()
    return when (filter.operator) {
        "contains" -> cellVal.contains(filter.value, ignoreCase = true)
        "not_contains" -> !cellVal.contains(filter.value, ignoreCase = true)
        "equals" -> cellVal.equals(filter.value, ignoreCase = true)
        "not_equals" -> !cellVal.equals(filter.value, ignoreCase = true)
        "starts_with" -> cellVal.startsWith(filter.value, ignoreCase = true)
        "ends_with" -> cellVal.endsWith(filter.value, ignoreCase = true)

        "empty" -> cellVal.isBlank() || cellVal == "—"
        "not_empty" -> cellVal.isNotBlank() && cellVal != "—"
        "checked" -> cellVal == "true"
        "unchecked" -> cellVal != "true"

        "gt" -> (cellVal.toDoubleOrNull() ?: 0.0) > (filter.value.toDoubleOrNull() ?: 0.0)
        "gte" -> (cellVal.toDoubleOrNull() ?: 0.0) >= (filter.value.toDoubleOrNull() ?: 0.0)
        "lt" -> (cellVal.toDoubleOrNull() ?: 0.0) < (filter.value.toDoubleOrNull() ?: 0.0)
        "lte" -> (cellVal.toDoubleOrNull() ?: 0.0) <= (filter.value.toDoubleOrNull() ?: 0.0)

        "between" -> {
            val parts = filter.value.split("|")
            if (parts.size == 2 && cellVal.isNotBlank()) {
                val lo = parts[0].toDoubleOrNull()
                val hi = parts[1].toDoubleOrNull()
                val numeric = cellVal.toDoubleOrNull()
                if (lo != null && hi != null && numeric != null) numeric in lo..hi
                else cellVal >= parts[0] && cellVal <= parts[1]
            } else true
        }

        "priority" -> cellVal.equals(filter.value, ignoreCase = true)
        "before" -> cellVal.isNotBlank() && cellVal < filter.value
        "after" -> cellVal.isNotBlank() && cellVal > filter.value
        else -> true
    }
}

private fun compareByColumnType(type: ColumnType, first: String, second: String): Int = when (type) {
    ColumnType.NUMBER, ColumnType.MONEY -> {
        val left = first.toDoubleOrNull() ?: Double.MAX_VALUE
        val right = second.toDoubleOrNull() ?: Double.MAX_VALUE
        left.compareTo(right)
    }
    ColumnType.CHECKBOX -> (first == "true").compareTo(second == "true")
    ColumnType.PRIORITY -> (PRIORITY_SORT_WEIGHTS[first] ?: 0).compareTo(PRIORITY_SORT_WEIGHTS[second] ?: 0)
    else -> first.lowercase().compareTo(second.lowercase())
}

private fun applyFiltersAndSorts(
    rows: List<DatabaseRow>,
    columns: List<DatabaseColumn>,
    view: DatabaseView
): List<DatabaseRow> {
    var result = rows.filter { !it.isDeleted }

    view.activeFilters.forEach { filter ->
        result = result.filter { it.matches(filter) }
    }

    if (view.activeSorts.isEmpty()) return result

    return result.sortedWith { first, second ->
        var comparison = 0
        for (rule in view.activeSorts) {
            val column = columns.find { it.id == rule.columnId } ?: continue
            comparison = compareByColumnType(
                column.type,
                first.cells[rule.columnId].displayText(),
                second.cells[rule.columnId].displayText()
            )
            if (!rule.isAscending) comparison = -comparison
            if (comparison != 0) break
        }
        comparison
    }
}

private fun filterChipLabel(filter: FilterConfig, columnName: String): String = when (filter.operator) {
    "not_empty" -> "$columnName is not empty"
    "empty" -> "$columnName is empty"
    "checked" -> "$columnName is checked"
    "unchecked" -> "$columnName is unchecked"
    "priority" -> "$columnName = ${filter.value}"
    "gt" -> "$columnName > ${filter.value}"
    "lt" -> "$columnName < ${filter.value}"
    "gte" -> "$columnName ≥ ${filter.value}"
    "lte" -> "$columnName ≤ ${filter.value}"
    "between" -> filter.value.split("|").let { if (it.size == 2) "$columnName: ${it[0]} – ${it[1]}" else "$columnName between" }
    "before" -> "$columnName before ${filter.value}"
    "after" -> "$columnName after ${filter.value}"
    "starts_with" -> "$columnName starts with \"${filter.value}\""
    "ends_with" -> "$columnName ends with \"${filter.value}\""
    "not_contains" -> "$columnName does not contain \"${filter.value}\""
    "not_equals" -> "$columnName is not \"${filter.value}\""
    else -> "$columnName ${filter.operator} \"${filter.value}\""
}

private fun filterConditionsFor(type: ColumnType?): List<Pair<String, String>> = when (type) {
    ColumnType.CHECKBOX -> listOf(
        "unchecked" to "Hide Checked rows",
        "checked" to "Hide Unchecked rows"
    )

    ColumnType.NUMBER, ColumnType.MONEY -> listOf(
        "equals" to "Equals",
        "not_equals" to "Does not equal",
        "gt" to "Greater than (>)",
        "gte" to "Greater than or equal (≥)",
        "lt" to "Less than (<)",
        "lte" to "Less than or equal (≤)",
        "between" to "Between (range)",
        "not_empty" to "Is not empty",
        "empty" to "Is empty"
    )

    ColumnType.DATE -> listOf(
        "equals" to "On exactly date",
        "before" to "Is before date",
        "after" to "Is after date",
        "between" to "Between two dates",
        "not_empty" to "Is scheduled (Not empty)",
        "empty" to "Is unscheduled (Empty)"
    )

    else -> listOf(
        "contains" to "Contains text",
        "not_contains" to "Does not contain",
        "equals" to "Is exactly",
        "not_equals" to "Is not",
        "starts_with" to "Starts with",
        "ends_with" to "Ends with",
        "not_empty" to "Is not empty",
        "empty" to "Is empty",
        "priority" to "Priority status is"
    )
}

@Composable
private fun RenameViewSheet(context: DatabaseSheetContext) {
    val state = context.state

    val onConfirmRename: () -> Unit = {
        val viewId = state.renamingViewId
        if (viewId != null && state.textInput.isNotBlank()) {
            val newName = state.textInput.trim()
            state.applyAction { context.actions.onRenameDatabaseView(context.block.id, viewId, newName) }
        }
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = state.textInput,
            onValueChange = { state.textInput = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onSubmit = onConfirmRename
        )
    }
    SheetCancelAndConfirmButtons(
        confirmText = "Save",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = onConfirmRename,
        modifier = Modifier.padding(vertical = 12.dp)
    )

    if (context.block.views.size > 1) {
        SheetDivider()
        SheetMenuRow(
            icon = painterResource(Res.drawable.trash),
            text = "Delete View",
            color = MaterialTheme.colorScheme.error
        ) {
            val viewId = state.renamingViewId ?: return@SheetMenuRow
            state.applyAction { context.actions.onDeleteDatabaseView(context.block.id, viewId) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SaveAsTemplateSheet(context: DatabaseSheetContext) {
    val state = context.state

    val onConfirmSaveAsTemplate: () -> Unit = {
        val name = state.textInput.trim()
        if (name.isNotEmpty()) {
            state.applyAction { context.actions.onSaveDatabaseAsTemplate(context.block.id, name) }
        }
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = state.textInput,
            onValueChange = { state.textInput = it },
            placeholder = "Template name",
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onSubmit = onConfirmSaveAsTemplate
        )
    }
    SheetCancelAndConfirmButtons(
        confirmText = "Save",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = onConfirmSaveAsTemplate,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun AddViewSheet(context: DatabaseSheetContext) {
    val state = context.state
    val blockId = context.block.id

    SheetMenuRow(icon = painterResource(Res.drawable.table), text = "Table") {
        state.applyAction { context.actions.onAddDatabaseView(blockId, ViewType.TABLE) }
    }
    SheetMenuRow(icon = painterResource(Res.drawable.square_kanban), text = "Board") {
        state.applyAction { context.actions.onAddDatabaseView(blockId, ViewType.KANBAN) }
    }
    SheetMenuRow(icon = painterResource(Res.drawable.widget), text = "Gallery") {
        state.applyAction { context.actions.onAddDatabaseView(blockId, ViewType.GALLERY) }
    }
}

@Composable
private fun TableSettingsSheet(context: DatabaseSheetContext) {
    val state = context.state

    SheetMenuRow(icon = painterResource(Res.drawable.funnel), text = "Filter") {
        val firstColumn = context.visibleColumns.firstOrNull() ?: return@SheetMenuRow
        state.textInput = ""
        state.textInputMax = ""
        state.filterOperator = "contains"
        state.openForColumn(firstColumn.id, DatabaseSheet.FILTER)
    }
    SheetMenuRow(icon = painterResource(Res.drawable.bookmark), text = "Save as Template") {
        state.textInput = ""
        state.open(DatabaseSheet.SAVE_AS_TEMPLATE)
    }
    SheetMenuRow(icon = painterResource(Res.drawable.plus), text = "Add View") {
        state.open(DatabaseSheet.ADD_VIEW)
    }

    if (context.activeView.type == ViewType.KANBAN) {
        SheetMenuRow(icon = painterResource(Res.drawable.group), text = "Group By") {
            state.open(DatabaseSheet.GROUP_BY)
        }
    }
    if (context.activeView.type == ViewType.GALLERY) {
        SheetMenuRow(icon = painterResource(Res.drawable.slider_h2), text = "Card Size") {
            state.open(DatabaseSheet.CARD_SIZE)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortSheet(context: DatabaseSheetContext) {
    val state = context.state
    val actions = context.actions
    val blockId = context.block.id
    val activeSorts = context.activeView.activeSorts
    val sortedColumnIds = activeSorts.map { it.columnId }
    val unsortedColumns = context.visibleColumns.filter { it.id !in sortedColumnIds }

    if (activeSorts.isNotEmpty()) {
        Text(
            text = "Sort order — top layer wins, lower layers break ties",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).sheetSidePadding()
        )

        activeSorts.forEachIndexed { index, rule ->
            val column = context.visibleColumns.find { it.id == rule.columnId } ?: return@forEachIndexed
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).sheetSidePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    column.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { actions.onUpdateDbSort(blockId, column.id, !rule.isAscending) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(if (rule.isAscending) Res.drawable.arrow_up else Res.drawable.arrow_down),
                            null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (rule.isAscending) "Asc" else "Desc",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(Res.drawable.x),
                    "Remove sort layer",
                    modifier = Modifier.size(18.dp).clickable { actions.onUpdateDbSort(blockId, column.id, null) },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        SheetDivider()
    }

    if (unsortedColumns.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth().sheetSidePadding()
        ) {
            unsortedColumns.forEach { column ->
                SuggestionChip(
                    onClick = { actions.onUpdateDbSort(blockId, column.id, true) },
                    label = { Text(column.name, style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(painterResource(Res.drawable.plus), null, modifier = Modifier.size(15.dp)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
            }
        }
    } else if (activeSorts.isNotEmpty()) {
        Text(
            text = "Every column is already in the sort.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp).sheetSidePadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activeSorts.isNotEmpty()) {
            EmberrButtonSecondary(
                text = "Clear all",
                onClick = {
                    sortedColumnIds.forEach { actions.onUpdateDbSort(blockId, it, null) }
                    state.dismissCurrentSheet()
                },
                modifier = Modifier.weight(1f)
            )
            EmberrButtonPrimary(text = "Done", onClick = { state.dismissCurrentSheet() }, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(context: DatabaseSheetContext) {
    val state = context.state
    val activeColumn = context.activeColumn
    val isCheckbox = activeColumn?.type == ColumnType.CHECKBOX
    val isNumber = activeColumn?.type == ColumnType.NUMBER || activeColumn?.type == ColumnType.MONEY
    val isDate = activeColumn?.type == ColumnType.DATE

    SheetSectionLabel("Column", Modifier.padding(top = 4.dp, bottom = 8.dp).sheetSidePadding())

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .sheetSidePadding()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                val index = context.visibleColumns.indexOfFirst { it.id == state.activeColId }
                state.activeColId = context.visibleColumns[(index + 1) % context.visibleColumns.size].id
                state.filterOperator = "contains"
                state.textInput = ""
                state.textInputMax = ""
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeColumn?.name ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                painterResource(Res.drawable.transfer_h),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    SheetDivider(verticalPadding = 22.dp)
    SheetSectionLabel("Condition", Modifier.padding(bottom = 8.dp).sheetSidePadding())

    val operatorOptions = filterConditionsFor(activeColumn?.type)
    if (operatorOptions.none { it.first == state.filterOperator }) {
        state.filterOperator = operatorOptions.first().first
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().sheetSidePadding()
    ) {
        operatorOptions.forEach { (operator, label) ->
            val isSelected = state.filterOperator == operator
            FilterChip(
                selected = isSelected,
                onClick = {
                    state.filterOperator = operator
                    state.textInput = ""
                    state.textInputMax = ""
                },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }

    fun onConfirmFilter() {
        val columnId = state.activeColId ?: return
        val canApply = when {
            isCheckbox -> true
            state.filterOperator in listOf("not_empty", "empty") -> true
            state.filterOperator == "priority" -> state.filterPriority.isNotBlank()
            state.filterOperator == "between" -> state.textInput.isNotBlank() && state.textInputMax.isNotBlank()
            else -> state.textInput.isNotBlank()
        }
        if (!canApply) return

        val operator = state.filterOperator
        val value = when (operator) {
            "priority" -> state.filterPriority.trim()
            "between" -> "${state.textInput.trim()}|${state.textInputMax.trim()}"
            else -> state.textInput.trim()
        }
        state.applyAction { context.actions.onAddDbFilter(context.block.id, columnId, operator, value) }
    }

    val needsTextInput = state.filterOperator in listOf(
        "contains", "equals", "not_equals", "gt", "gte", "lt", "lte", "before", "after", "starts_with", "ends_with"
    )

    if (needsTextInput) {
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.sheetSidePadding()) {
            EmberrTextField(
                value = state.textInput,
                onValueChange = { state.textInput = it },
                placeholder = if (isNumber) "Enter number…" else "Enter value…",
                modifier = Modifier.fillMaxWidth(),
                onSubmit = ::onConfirmFilter
            )
        }
    }

    if (state.filterOperator == "between") {
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.sheetSidePadding()) {
                EmberrTextField(
                    value = state.textInput,
                    onValueChange = { state.textInput = it },
                    placeholder = if (isDate) "Start" else "Min",
                    modifier = Modifier.weight(1f),
                    onSubmit = ::onConfirmFilter
                )
                EmberrTextField(
                    value = state.textInputMax,
                    onValueChange = { state.textInputMax = it },
                    placeholder = if (isDate) "End" else "Max",
                    modifier = Modifier.weight(1f),
                    onSubmit = ::onConfirmFilter
                )
            }
        }
    }

    if (state.filterOperator == "priority") {
        Spacer(Modifier.height(10.dp))
        SheetSectionLabel("Priority level", Modifier.padding(bottom = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.sheetSidePadding()
        ) {
            PRIORITY_LEVELS.forEach { priority ->
                val isSelected = state.filterPriority == priority
                val chipColor = priorityAccentColor(priority) ?: MaterialTheme.colorScheme.outline
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        state.filterPriority = priority
                        state.textInput = priority
                    },
                    label = { Text(priority, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) chipColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }

    SheetCancelAndConfirmButtons(
        confirmText = "Apply",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = ::onConfirmFilter,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
    )
}

@Composable
private fun GroupBySheet(context: DatabaseSheetContext) {
    val state = context.state
    val activeView = context.activeView
    val eligibleColumns = context.visibleColumns.filter {
        it.type == ColumnType.CHECKBOX || it.type == ColumnType.STATUS
    }
    val selectedGroupColumn = eligibleColumns.find { it.id == activeView.groupByColumnId }

    Column(modifier = Modifier.fillMaxWidth()) {
        val isNoneSelected = activeView.groupByColumnId == null
        SheetMenuRow(
            icon = painterResource(Res.drawable.x),
            text = "None",
            color = if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            selected = isNoneSelected
        ) { state.applyAction { context.actions.onUpdateDbGroupBy(context.block.id, null) } }

        eligibleColumns.forEach { column ->
            val isSelected = activeView.groupByColumnId == column.id
            val icon = when (column.type) {
                ColumnType.CHECKBOX -> painterResource(Res.drawable.square_check)
                ColumnType.STATUS -> painterResource(Res.drawable.check_square)
                else -> rememberVectorPainter(Icons.AutoMirrored.Filled.Subject)
            }
            SheetMenuRow(
                icon = icon,
                text = column.name,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                selected = isSelected
            ) { state.applyAction { context.actions.onUpdateDbGroupBy(context.block.id, column.id) } }
        }

        if (eligibleColumns.isEmpty()) {
            Text(
                text = "Add a Checkbox or Status column to group by.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (selectedGroupColumn != null) {
            SheetDivider()
            SheetSectionLabel("Visible boards", Modifier.padding(bottom = 4.dp))
            BoardVisibilityAndOrderList(context, selectedGroupColumn)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BoardVisibilityAndOrderList(
    context: DatabaseSheetContext,
    groupColumn: DatabaseColumn
) {
    val activeView = context.activeView
    val defaultBucketKeys = remember(groupColumn) { bucketKeysFor(groupColumn) }
    val orderedKeys = remember(activeView.id, defaultBucketKeys, activeView.groupOrder) {
        mutableStateListOf(*orderedBucketKeys(defaultBucketKeys, activeView.groupOrder).toTypedArray())
    }
    var draggedBucket by remember { mutableStateOf<String?>(null) }
    var dragPointerY by remember { mutableStateOf(0f) }
    val rowBoundsInWindow = remember { mutableStateMapOf<String, Rect>() }

    orderedKeys.forEach { bucketName ->
        key(bucketName) {
            val isVisible = bucketName !in activeView.hiddenGroups
            val isDragged = draggedBucket == bucketName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { rowBoundsInWindow[bucketName] = it.boundsInWindow() }
                    .graphicsLayer { alpha = if (isDragged) 0.5f else 1f }
                    .clickable {
                        context.actions.onToggleKanbanGroupVisibility(
                            context.block.id,
                            activeView.id,
                            bucketName,
                            isVisible
                        )
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Reorder $bucketName",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(18.dp)
                            .pointerInput(bucketName) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedBucket = bucketName
                                        dragPointerY = rowBoundsInWindow[bucketName]?.center?.y ?: 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragPointerY += dragAmount.y
                                        val hovered = rowBoundsInWindow.entries
                                            .firstOrNull { (_, rect) -> dragPointerY in rect.top..rect.bottom }
                                            ?.key
                                        if (hovered != null && hovered != bucketName) {
                                            val from = orderedKeys.indexOf(bucketName)
                                            val to = orderedKeys.indexOf(hovered)
                                            if (from != -1 && to != -1) {
                                                orderedKeys.add(to, orderedKeys.removeAt(from))
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedBucket = null
                                        context.actions.onReorderKanbanGroups(
                                            context.block.id,
                                            activeView.id,
                                            orderedKeys.toList()
                                        )
                                    },
                                    onDragCancel = { draggedBucket = null }
                                )
                            }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        bucketName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(checked = isVisible, onCheckedChange = null, modifier = Modifier.scale(0.8f))
            }
        }
    }
}

@Composable
private fun CardSizeSheet(context: DatabaseSheetContext) {
    val options = listOf(
        Triple(GalleryCardSize.SMALL, "Small", painterResource(Res.drawable.minimize_2)),
        Triple(GalleryCardSize.MEDIUM, "Medium", painterResource(Res.drawable.square)),
        Triple(GalleryCardSize.LARGE, "Large", painterResource(Res.drawable.maximize_2))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        options.forEach { (size, label, icon) ->
            val isSelected = context.activeView.galleryCardSize == size
            SheetMenuRow(
                icon = icon,
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                selected = isSelected
            ) { context.state.applyAction { context.actions.onUpdateDbGalleryCardSize(context.block.id, size) } }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CellActionsSheet(context: DatabaseSheetContext) {
    val state = context.state
    val column = context.activeColumn ?: return
    val row = context.block.rows.find { it.id == state.activeRowId } ?: return

    val colIndex = context.visibleColumns.indexOf(column)
    val rowIndex = context.block.rows.indexOf(row)
    val actions = context.actions
    val blockId = context.block.id

    SheetMenuRow(painterResource(Res.drawable.arrow_up), "Insert Row Above") {
        state.applyAction { actions.onAddDbRowAt(blockId, rowIndex) }
    }
    SheetMenuRow(painterResource(Res.drawable.arrow_down), "Insert Row Below") {
        state.applyAction { actions.onAddDbRowAt(blockId, rowIndex + 1) }
    }
    SheetMenuRow(painterResource(Res.drawable.arrow_left), "Insert Column Left") {
        state.applyAction { actions.onAddDbColumnAt(blockId, colIndex) }
    }
    SheetMenuRow(painterResource(Res.drawable.arrow_right), "Insert Column Right") {
        state.applyAction { actions.onAddDbColumnAt(blockId, colIndex + 1) }
    }

    SheetDivider()

    if (rowIndex > 0) {
        SheetMenuRow(rememberVectorPainter(Icons.Default.ArrowUpward), "Move Row Up") {
            state.applyAction { actions.onReorderDbRows(blockId, rowIndex, rowIndex - 1) }
        }
    }
    if (rowIndex < context.block.rows.lastIndex) {
        SheetMenuRow(rememberVectorPainter(Icons.Default.ArrowDownward), "Move Row Down") {
            state.applyAction { actions.onReorderDbRows(blockId, rowIndex, rowIndex + 1) }
        }
    }
    if (colIndex > 0) {
        SheetMenuRow(painterResource(Res.drawable.move_left), "Move Column Left") {
            state.applyAction { actions.onReorderDbColumns(blockId, colIndex, colIndex - 1) }
        }
    }
    if (colIndex < context.visibleColumns.lastIndex) {
        SheetMenuRow(painterResource(Res.drawable.move_right), "Move Column Right") {
            state.applyAction { actions.onReorderDbColumns(blockId, colIndex, colIndex + 1) }
        }
    }

    SheetDivider()

    SheetMenuRow(painterResource(Res.drawable.trash), "Delete Row", MaterialTheme.colorScheme.error) {
        state.applyAction { actions.onDeleteDbRow(blockId, row.id) }
    }
    SheetMenuRow(painterResource(Res.drawable.trash), "Delete Column", MaterialTheme.colorScheme.error) {
        state.applyAction { actions.onDeleteDbColumn(blockId, column.id) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnOptionsSheet(context: DatabaseSheetContext) {
    val state = context.state
    val column = context.activeColumn ?: return
    val actions = context.actions
    val blockId = context.block.id

    SheetMenuRow(painterResource(Res.drawable.pen), "Rename Column") {
        state.textInput = column.name
        state.open(DatabaseSheet.RENAME)
    }

    if (column.type == ColumnType.FORMULA) {
        SheetMenuRow(
            icon = painterResource(Res.drawable.sigma),
            text = "Edit Formula",
            color = MaterialTheme.colorScheme.primary
        ) {
            state.textInput = column.formulaExpression ?: ""
            state.open(DatabaseSheet.FORMULA)
        }

        val isCurrency = column.isFormulaCurrency
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.onUpdateDbFormulaCurrency(blockId, column.id, !isCurrency) }
                .padding(vertical = 6.dp).sheetSidePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(Res.drawable.badge_dollar_sign),
                    null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Format as currency",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(checked = isCurrency, onCheckedChange = null, modifier = Modifier.scale(0.8f))
        }

        if (isCurrency) {
            SheetMenuRow(
                icon = painterResource(Res.drawable.badge_dollar_sign),
                text = "Currency: ${column.currencySymbol ?: "$"}",
                color = MaterialTheme.colorScheme.primary
            ) { state.open(DatabaseSheet.CURRENCY_SELECTION) }
        }
    }

    if (column.type == ColumnType.MONEY) {
        SheetMenuRow(
            icon = painterResource(Res.drawable.badge_dollar_sign),
            text = "Format: ${column.currencySymbol ?: "$"}",
            color = MaterialTheme.colorScheme.primary
        ) { state.open(DatabaseSheet.CURRENCY_SELECTION) }
    }

    if (!isDesktopPlatform) {
        SheetDivider()
        SheetSectionLabel("Column Width", Modifier.padding(top = 4.dp, bottom = 8.dp))

        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ColumnWidthStepper(painterResource(Res.drawable.minus)) {
                actions.onUpdateDbColumnWidth(blockId, column.id, column.width - 20)
            }
            Text(
                text = "${column.width} px",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 50.dp),
                textAlign = TextAlign.Center
            )
            ColumnWidthStepper(painterResource(Res.drawable.plus)) {
                actions.onUpdateDbColumnWidth(blockId, column.id, column.width + 20)
            }
        }
    }

    SheetDivider()
    SheetSectionLabel("Property Type", Modifier.padding(bottom = 10.dp, top = 12.dp).sheetSidePadding())

    FlowRow(
        modifier = Modifier.sheetSidePadding(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ColumnType.entries.forEach { type ->
            val isSelected = column.type == type
            FilterChip(
                selected = isSelected,
                onClick = {
                    state.applyAction {
                        actions.onUpdateDbColumn(
                            blockId,
                            column.id,
                            if (column.isNameManuallySet) column.name else type.propertyLabel(),
                            type,
                            isManualNameChange = false
                        )
                    }
                },
                label = { Text(text = type.propertyLabel(), style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }

    SheetDivider()
    SheetMenuRow(
        icon = painterResource(Res.drawable.trash),
        text = "Delete Column",
        color = MaterialTheme.colorScheme.error
    ) { state.applyAction { actions.onDeleteDbColumn(blockId, column.id) } }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ColumnWidthStepper(icon: Painter, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(8.dp).size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RenameColumnSheet(context: DatabaseSheetContext) {
    val state = context.state

    fun onConfirmRenameColumn() {
        val column = context.activeColumn
        if (column != null && state.textInput.isNotBlank()) {
            val newName = state.textInput.trim()
            state.applyAction { context.actions.onUpdateDbColumn(context.block.id, column.id, newName, column.type) }
        }
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = state.textInput,
            onValueChange = { state.textInput = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onSubmit = ::onConfirmRenameColumn
        )
    }
    SheetCancelAndConfirmButtons(
        confirmText = "Save",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = ::onConfirmRenameColumn,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditFormulaSheet(context: DatabaseSheetContext) {
    val state = context.state

    SheetSectionLabel("Properties", Modifier.padding(bottom = 8.dp, top = 12.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).sheetSidePadding()
    ) {
        context.visibleColumns.filter { it.id != state.activeColId }.forEach { column ->
            SuggestionChip(
                onClick = { state.textInput += "prop(\"${column.name}\") " },
                label = {
                    Text(
                        column.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )
        }
    }

    SheetSectionLabel("Operators", Modifier.padding(bottom = 8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).sheetSidePadding()
    ) {
        listOf("+", "-", "*", "/", "(", ")").forEach { operator ->
            SuggestionChip(
                onClick = { state.textInput += "$operator " },
                label = {
                    Text(
                        operator,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )
        }
    }

    fun onConfirmFormula() {
        val columnId = state.activeColId ?: return
        val expression = state.textInput.trim()
        state.applyAction { context.actions.onUpdateDbFormula(context.block.id, columnId, expression) }
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = state.textInput,
            onValueChange = { state.textInput = it },
            placeholder = "e.g. prop(\"Price\") * 2",
            modifier = Modifier.fillMaxWidth(),
            onSubmit = ::onConfirmFormula
        )
    }
    SheetCancelAndConfirmButtons(
        confirmText = "Save",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = ::onConfirmFormula,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
    )
}

private val SUPPORTED_CURRENCIES = listOf(
    "$" to "US Dollar",
    "€" to "Euro",
    "£" to "British Pound",
    "¥" to "Yen",
    "₹" to "Rupee",
    "A$" to "Australian Dollar",
    "C$" to "Canadian Dollar"
)

@Composable
private fun PickCurrencySheet(context: DatabaseSheetContext) {
    val column = context.activeColumn ?: return

    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())
    ) {
        SUPPORTED_CURRENCIES.forEach { (symbol, name) ->
            val isSelected = (column.currencySymbol ?: "$") == symbol
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.state.close()
                        context.actions.onUpdateDbCurrency(context.block.id, column.id, symbol)
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$name ($symbol)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Icon(
                        painterResource(Res.drawable.check),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private val NEW_TAG_PALETTE = listOf(
    "#E03E3E", "#D9730D", "#DFAB01", "#0F7B6C", "#0B6E99", "#6940A5", "#9065B0"
)

@Composable
private fun PickTagsSheet(context: DatabaseSheetContext) {
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
private fun AttachedFilesSheet(context: DatabaseSheetContext) {
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
private fun PickPrioritySheet(context: DatabaseSheetContext) {
    ColoredValuePickerSheet(context, PRIORITY_LEVELS) { priorityAccentColor(it) }
}

@Composable
private fun PickStatusSheet(context: DatabaseSheetContext) {
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
private fun CalculateSheet(context: DatabaseSheetContext) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseTemplatePickerSheet(
    expanded: Boolean,
    templates: List<DatabaseTemplateEntity>,
    onDismiss: () -> Unit,
    onCreateBlank: () -> Unit,
    onSelectTemplate: (DatabaseTemplateEntity) -> Unit
) {
    val isKeyboardOutOfTheWay = rememberShowAfterKeyboardCloses(expanded)

    EmberrBottomSheet(expanded = isKeyboardOutOfTheWay, onDismiss = onDismiss, title = "Add Database") { closeAnd ->
        MuteRippleOnMobile {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                SheetMenuRow(icon = painterResource(Res.drawable.hash), text = "Create Blank Database") {
                    closeAnd {
                        onDismiss()
                        onCreateBlank()
                    }
                }

                if (templates.isNotEmpty()) {
                    SheetDivider()
                    SheetSectionLabel("Saved Templates", Modifier.padding(vertical = 6.dp))
                    templates.forEach { template ->
                        SheetMenuRow(icon = painterResource(Res.drawable.files), text = template.name) {
                            closeAnd {
                                onDismiss()
                                onSelectTemplate(template)
                            }
                        }
                    }
                }

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

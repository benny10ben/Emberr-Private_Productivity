package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.CellData
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.ViewType
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.editor.EditorActions
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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

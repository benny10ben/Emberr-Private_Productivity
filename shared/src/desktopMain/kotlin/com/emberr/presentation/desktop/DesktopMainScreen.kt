package com.emberr.presentation.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.ViewType
import com.emberr.presentation.shared.editor.EditorActions
import com.emberr.presentation.settings.SettingsScreen
import com.emberr.presentation.settings.selfhost.SelfHostSetupScreen
import com.emberr.presentation.shared.UserSettings
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll
import com.emberr.presentation.sync.SyncViewModel
import com.emberr.presentation.calendar.CalendarScreen
import com.emberr.presentation.mobile.daily.CollapsedWeekStrip
import com.emberr.presentation.mobile.daily.DailyEditorPane
import com.emberr.presentation.mobile.daily.DailyEditorViewModel
import com.emberr.presentation.mobile.daily.DailyTimelineDialog
import com.emberr.presentation.mobile.home.DesktopSortMenu
import com.emberr.presentation.mobile.home.DropInsertPosition
import com.emberr.presentation.mobile.home.HomeViewModel
import com.emberr.presentation.mobile.home.HomeItem
import com.emberr.presentation.mobile.home.HomeItemKey
import com.emberr.presentation.mobile.home.TemplatesDesktopMenu
import com.emberr.presentation.mobile.home.ROOT_TREE_GUIDE_LINES
import com.emberr.presentation.mobile.home.TreeSelectionMenu
import com.emberr.presentation.mobile.home.buildTreeGuideLines
import com.emberr.presentation.mobile.home.flattenFolderTree
import com.emberr.presentation.mobile.home.rowsBetween
import com.emberr.presentation.mobile.home.treeSelectionMenu
import com.emberr.presentation.mobile.home.note.NoteScreen
import com.emberr.presentation.mobile.home.overview.bookmarks.BookmarksScreen
import com.emberr.presentation.mobile.home.overview.documents.DocumentsScreen
import com.emberr.presentation.mobile.home.overview.images.ImagesScreen
import com.emberr.presentation.mobile.home.overview.tasks.TasksScreen
import com.emberr.presentation.search.SearchDialog
import com.emberr.presentation.trash.TrashScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.EmberrPillShadowAmbientColor
import com.emberr.presentation.shared.components.EmberrPillShadowSpotColor
import com.emberr.presentation.shared.components.TopBarIconButtonGroup
import com.emberr.presentation.shared.components.TopBarIconButtonItem
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.LocalEmberrBlurSource
import com.emberr.presentation.shared.components.emberrBlur
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_up_down
import emberr.shared.generated.resources.astroid
import emberr.shared.generated.resources.bookmark
import emberr.shared.generated.resources.calendar
import emberr.shared.generated.resources.sidebar
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.ellipsis
import emberr.shared.generated.resources.history2
import emberr.shared.generated.resources.pen_square
import emberr.shared.generated.resources.folder_plus
import emberr.shared.generated.resources.images
import emberr.shared.generated.resources.notes2
import emberr.shared.generated.resources.search
import emberr.shared.generated.resources.template
import org.jetbrains.compose.resources.painterResource
import java.awt.Cursor

private val PANEL_PADDING = 16.dp
private val PANEL_TOP_MARGIN = 12.dp
private val DesktopPanelShape = RoundedCornerShape(18.dp)

// Right-panel state. Replaces Home's boolean flags.
sealed interface DetailPane {
    data class Daily(val date: LocalDate) : DetailPane
    data class Note(val noteId: String) : DetailPane
    data object Settings : DetailPane
    data object SelfHostSetup : DetailPane
    data object Trash : DetailPane
    data object Reminders : DetailPane
    data object Bookmarks : DetailPane
    data object Images : DetailPane
    data object Documents : DetailPane
    data object Calendar : DetailPane
}

private fun DetailPane.encode(): String = when (this) {
    is DetailPane.Daily -> "DAILY:${date}"
    is DetailPane.Note -> "NOTE:${noteId}"
    DetailPane.Settings -> "PANEL:SETTINGS"
    DetailPane.SelfHostSetup -> "PANEL:SELFHOST"
    DetailPane.Trash -> "PANEL:TRASH"
    DetailPane.Reminders -> "PANEL:REMINDERS"
    DetailPane.Bookmarks -> "PANEL:BOOKMARKS"
    DetailPane.Images -> "PANEL:IMAGES"
    DetailPane.Documents -> "PANEL:DOCUMENTS"
    DetailPane.Calendar -> "PANEL:CALENDAR"
}

private fun decodeDetailPane(raw: String, today: LocalDate): DetailPane = when {
    raw.startsWith("DAILY:") -> runCatching { DetailPane.Daily(LocalDate.parse(raw.removePrefix("DAILY:"))) }
        .getOrElse { DetailPane.Daily(today) }
    raw.startsWith("NOTE:") -> DetailPane.Note(raw.removePrefix("NOTE:"))
    raw == "PANEL:SETTINGS" -> DetailPane.Settings
    raw == "PANEL:SELFHOST" -> DetailPane.SelfHostSetup
    raw == "PANEL:TRASH" -> DetailPane.Trash
    raw == "PANEL:REMINDERS" -> DetailPane.Reminders
    raw == "PANEL:BOOKMARKS" -> DetailPane.Bookmarks
    raw == "PANEL:IMAGES" -> DetailPane.Images
    raw == "PANEL:DOCUMENTS" -> DetailPane.Documents
    raw == "PANEL:CALENDAR" -> DetailPane.Calendar
    else -> DetailPane.Daily(today)
}

@Composable
private fun Modifier.noRippleClickable(interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }, onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )

@Composable
private fun OverviewRow(
    icon: Painter,
    title: String,
    subtitle: String,
    isSelected: Boolean = false,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val rowBackground by animateColorAsState(
        sidebarRowBackground(isActive = isSelected, isSelected = false, isHovered = isHovered),
        tween(180, easing = FastOutSlowInEasing),
        label = "overview_bg_$title"
    )
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        SidebarActiveAccent(isActive = isSelected)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(rowBackground)
                .noRippleClickable(interactionSource, onClick)
                .heightIn(min = 42.dp)
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected || isHovered) 0.9f else 0.55f),
                    modifier = Modifier.size(iconSize)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = sidebarRowTextStyle,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.82f),
                modifier = Modifier.weight(1f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun SidebarGroupSeparator() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    )
}

private const val FAVORITE_ROW_PREFIX = "sb_fav_"

private val MIN_PANEL_WIDTH = 240.dp
private val MAX_PANEL_WIDTH = 520.dp
private val MIN_RAG_PANEL_WIDTH = 320.dp
private val MAX_RAG_PANEL_WIDTH = 640.dp
private val DEFAULT_RAG_PANEL_WIDTH = 400.dp

private const val NEARBY_DAILY_PREFETCH_RADIUS = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun DesktopMainScreen(
    homeViewModel: HomeViewModel = koinViewModel(),
    dailyViewModel: DailyEditorViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel(),
    settingsManager: SettingsManager = koinInject(),
    isSidebarVisible: Boolean = true,
    sidebarWidth: Dp = 340.dp,
    onToggleSidebar: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onExportMarkdown: (fileName: String, content: String) -> Unit = { _, _ -> },
    onExportPdf: (fileName: String, title: String, blocks: List<NoteBlock>) -> Unit = { _, _, _ -> },
    onExportBackup: () -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onAiIconTap: () -> Unit = {},
    isRagChatVisible: Boolean = false,
    ragViewModel: com.emberr.presentation.rag.RagViewModel? = null,
    onDismissRagChat: () -> Unit = {},
) {
    val hazeState = remember { HazeState() }
    val savedWidth by settingsManager.desktopSidebarWidthFlow.collectAsState(initial = sidebarWidth.value)
    val isAiDisabled by settingsManager.aiFeaturesDisabledFlow.collectAsState(
        initial = settingsManager.isAiFeaturesDisabled()
    )
    var panelWidth by remember { mutableStateOf(sidebarWidth) }
    var hasLoadedWidth by remember { mutableStateOf(false) }
    var ragPanelWidth by remember { mutableStateOf(DEFAULT_RAG_PANEL_WIDTH) }

    LaunchedEffect(savedWidth) {
        if (!hasLoadedWidth) {
            panelWidth = savedWidth.dp
            hasLoadedWidth = true
        }
    }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    // Home data
    val isLoading by homeViewModel.isLoading.collectAsState()
    val favoriteNotes by homeViewModel.favoriteNotes.collectAsState()
    val recentNotes by homeViewModel.recentNotes.collectAsState()
    val selectedNoteIds by homeViewModel.selectedNoteIds.collectAsState()
    val selectedFolderIds by homeViewModel.selectedFolderIds.collectAsState()
    val foldersByParent by homeViewModel.foldersByParent.collectAsState()
    val notesByFolder by homeViewModel.notesByFolder.collectAsState()
    val expandedFolderIds by homeViewModel.expandedFolderIds.collectAsState()
    val currentSortType by homeViewModel.sortType.collectAsState()
    val currentSortOrder by homeViewModel.sortOrder.collectAsState()
    val remindersCount by homeViewModel.remindersCount.collectAsState()
    val bookmarksCount by homeViewModel.bookmarksCount.collectAsState()
    val imagesCount by homeViewModel.imagesCount.collectAsState()
    val documentsCount by homeViewModel.documentsCount.collectAsState()
    val templates by homeViewModel.filteredTemplates.collectAsState()
    val templateSearchQuery by homeViewModel.templateSearchQuery.collectAsState()

    // Daily data (for strip + sheets)
    val selectedDate by dailyViewModel.selectedDate.collectAsState()

    LaunchedEffect(selectedDate) {
        val nearbyDateStrings = (-NEARBY_DAILY_PREFETCH_RADIUS..NEARBY_DAILY_PREFETCH_RADIUS).map { dayOffset ->
            selectedDate.plus(dayOffset, DateTimeUnit.DAY).toString()
        }
        dailyViewModel.evictPreviewCache(nearbyDateStrings.toSet())
        nearbyDateStrings.forEach { dateString -> dailyViewModel.prefetchDateIfNeeded(dateString) }
    }

    val isSelectionMode = selectedNoteIds.isNotEmpty() || selectedFolderIds.isNotEmpty()

    // Right panel state
    val lastOpenedState by settingsManager.lastOpenedDesktopStateFlow.collectAsState(initial = "")
    var detail by remember { mutableStateOf<DetailPane?>(null) }
    var hasRestored by remember { mutableStateOf(false) }

    LaunchedEffect(lastOpenedState, isLoading) {
        if (!hasRestored && !isLoading) {
            val restored = if (lastOpenedState.isBlank()) DetailPane.Daily(today)
            else decodeDetailPane(lastOpenedState, today)
            if (restored is DetailPane.Daily) dailyViewModel.selectDate(restored.date)
            detail = restored
            hasRestored = true
        }
    }

    LaunchedEffect(detail) {
        detail?.let { settingsManager.saveLastOpenedDesktopState(it.encode()) }
    }

    // Menus / popups
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddNotePopup by remember { mutableStateOf(false) }
    var showAddFolderPopup by remember { mutableStateOf(false) }
    var addNoteInput by remember { mutableStateOf("") }
    var addFolderInput by remember { mutableStateOf("") }
    var showTemplatesMenu by remember { mutableStateOf(false) }

    var isFavoritesExpanded by remember { mutableStateOf(true) }
    var isNotesExpanded by remember { mutableStateOf(true) }
    var isRecentsExpanded by remember { mutableStateOf(true) }
    var isPeeking by remember { mutableStateOf(false) }

    // Sheets
    var showTimelineDialog by remember { mutableStateOf(false) }
    val timelineDays by dailyViewModel.timelineDays.collectAsState()
    val isTimelineLoading by dailyViewModel.isTimelineLoading.collectAsState()
    var showSearchDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        DesktopSearchShortcutBus.requests.collect { showSearchDialog = true }
    }

    // Sync
    val snackbarHostState = remember { SnackbarHostState() }
    val syncState by syncViewModel.syncStatus.collectAsState()

    LaunchedEffect(isSidebarVisible) {
        if (isSidebarVisible) isPeeking = false
    }

    val openDaily: (LocalDate) -> Unit = { date ->
        dailyViewModel.selectDate(date)
        detail = DetailPane.Daily(date)
        isPeeking = false
    }
    val openNote: (String) -> Unit = { id ->
        detail = DetailPane.Note(id)
        isPeeking = false
    }

    LaunchedEffect(syncState) {
        if (syncState != "Idle" && syncState != "Syncing...") {
            snackbarHostState.showSnackbar(message = syncState)
            syncViewModel.resetSyncStatus()
        }
    }

    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }

    val handleCreateNote = { title: String ->
        homeViewModel.createNewNote(title = title, forceHomeFolder = false) { newId -> openNote(newId) }
    }
    val handleCreateFolder = { name: String -> homeViewModel.createNewFolder(name) }

    // Re-seeds any missing predefined template every time the templates menu opens.
    val handleOpenTemplates = {
        homeViewModel.onTemplatesMenuOpened()
        showAddNotePopup = false
        showTemplatesMenu = true
    }
    val handleTemplateClick = { templateId: String ->
        homeViewModel.createNoteFromTemplate(templateId) { newId -> openNote(newId) }
    }
    // Opens the template's own note directly (no clone) - the editor shows an "Editing
    // Template" pill for any note with isTemplate = true, so no separate mode is needed here.
    val handleEditTemplate = { templateId: String -> openNote(templateId) }
    val handleCreateNewTemplate = {
        homeViewModel.saveAsTemplate(title = "", content = NoteContent(blocks = emptyList())) { newId -> openNote(newId) }
    }

    var selectionAnchorKey by remember { mutableStateOf<String?>(null) }

    // Drag
    val dragState = rememberDesktopListDragState()
    val sidebarListState = rememberLazyListState()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { SIDEBAR_ROW_HEIGHT.toPx() }

    val settingsMenuSlot = @Composable {
        UserSettings(
            expanded = showSettingsMenu,
            onDismiss = { showSettingsMenu = false },
            onNavigateToSettings = { showSettingsMenu = false; detail = DetailPane.Settings },
            onNavigateToTrash = { showSettingsMenu = false; detail = DetailPane.Trash }
        )
    }

    // LEFT PANEL
    val leftPanel = @Composable { startPadding: Dp, endPadding: Dp ->
        val treeRows = if (isNotesExpanded) flattenFolderTree(
            null, 0, foldersByParent, notesByFolder, expandedFolderIds,
            sortType = currentSortType,
            sortOrder = currentSortOrder
        ) else emptyList()

        val treeGuideLines = remember(treeRows) { treeRows.buildTreeGuideLines() }

        val favoriteNoteIds = remember(favoriteNotes) { favoriteNotes.map { it.noteId }.toSet() }

        val favoriteRows = remember(favoriteNotes) { favoriteNotes.map { HomeItem.Note(it) } }

        fun handleRowClick(
            rowsInSection: List<HomeItem>,
            rowKey: String,
            modifiers: SidebarClickModifiers,
            openRow: () -> Unit
        ) {
            val anchorKeyInSection =
                selectionAnchorKey?.takeIf { anchor -> rowsInSection.any { it.key == anchor } } ?: rowKey
            val rowsBetweenAnchorAndClick =
                if (modifiers.extendSelection) rowsInSection.rowsBetween(anchorKeyInSection, rowKey)
                else emptyList()

            when {
                modifiers.extendSelection && rowsBetweenAnchorAndClick.isNotEmpty() -> {
                    homeViewModel.addToSelection(
                        noteIds = rowsBetweenAnchorAndClick.filterIsInstance<HomeItem.Note>().map { it.note.noteId },
                        folderIds = rowsBetweenAnchorAndClick.filterIsInstance<HomeItem.Folder>().map { it.folder.folderId }
                    )
                }

                modifiers.addToSelection -> {
                    selectionAnchorKey = rowKey
                    if (HomeItemKey.isFolder(rowKey)) homeViewModel.toggleFolderSelection(HomeItemKey.folderIdOf(rowKey))
                    else homeViewModel.toggleNoteSelection(HomeItemKey.noteIdOf(rowKey))
                }

                else -> {
                    selectionAnchorKey = rowKey
                    if (isSelectionMode) homeViewModel.clearSelection()
                    openRow()
                }
            }
        }

        val menuForRow = { rowKey: String ->
            val isRowSelected = when {
                HomeItemKey.isFolder(rowKey) -> selectedFolderIds.contains(HomeItemKey.folderIdOf(rowKey))
                else -> selectedNoteIds.contains(HomeItemKey.noteIdOf(rowKey))
            }
            val targetNoteIds = when {
                isRowSelected -> selectedNoteIds
                HomeItemKey.isNote(rowKey) -> setOf(HomeItemKey.noteIdOf(rowKey))
                else -> emptySet<String>()
            }
            val targetFolderIds = when {
                isRowSelected -> selectedFolderIds
                HomeItemKey.isFolder(rowKey) -> setOf(HomeItemKey.folderIdOf(rowKey))
                else -> emptySet<String>()
            }
            TreeRowMenuTarget(
                noteIds = targetNoteIds,
                folderIds = targetFolderIds,
                usesSelection = isRowSelected,
                menu = treeSelectionMenu(targetNoteIds, targetFolderIds) { noteId -> noteId in favoriteNoteIds }
            )
        }

        val rowKeys: List<String?> = buildList {
            add(null)

            if (favoriteNotes.isNotEmpty()) {
                add(null) // Favorites header
                if (isFavoritesExpanded) favoriteNotes.forEach { add("$FAVORITE_ROW_PREFIX${it.noteId}") }
            }

            add(null) // Notes header

            if (isNotesExpanded) treeRows.forEach { add(it.key) }

            if (recentNotes.isNotEmpty()) {
                add(null) // Recents header
                if (isRecentsExpanded) recentNotes.forEach { add("sb_recent_${it.noteId}") }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(start = startPadding, end = endPadding)) {

                // top: icon row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleSidebar) {
                        Icon(painterResource(Res.drawable.sidebar), "Collapse sidebar", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        TopBarIconButton(
                            icon = painterResource(Res.drawable.history2),
                            contentDescription = "Open timeline",
                            bgColor = MaterialTheme.colorScheme.background,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = {
                                dailyViewModel.loadTimeline()
                                showTimelineDialog = true
                            }
                        )
                    }
                    Box {
                        TopBarIconButtonGroup(
                            bgColor = MaterialTheme.colorScheme.background,
                            tint = MaterialTheme.colorScheme.primary,
                            items = listOf(
                                TopBarIconButtonItem(
                                    icon = painterResource(Res.drawable.calendar),
                                    contentDescription = "Calendar",
                                    onClick = { detail = DetailPane.Calendar; isPeeking = false }
                                ),
                                TopBarIconButtonItem(
                                    icon = painterResource(Res.drawable.ellipsis),
                                    contentDescription = "Settings",
                                    onClick = { showSettingsMenu = true }
                                )
                            )
                        )
                        settingsMenuSlot()
                    }
                }

                // calendar strip
                CollapsedWeekStrip(
                    selectedDate = selectedDate,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
                    onDateSelected = { openDaily(it) }
                )

                // Scrolling: overview rows + favorites + notes tree + recents
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .desktopListDragTracker(
                            dragState = dragState,
                            listState = sidebarListState,
                            rowKeys = rowKeys,
                            rowHeightPx = rowHeightPx,
                            payloadForKey = { key ->
                                when {
                                    key == null -> null
                                    HomeItemKey.isFolder(key) -> "$DRAG_PREFIX_FOLDER${HomeItemKey.folderIdOf(key)}"
                                    HomeItemKey.isNote(key) -> "$DRAG_PREFIX_NOTE${HomeItemKey.noteIdOf(key)}"
                                    key.startsWith(FAVORITE_ROW_PREFIX) -> "$DRAG_PREFIX_NOTE${key.removePrefix(FAVORITE_ROW_PREFIX)}"
                                    key.startsWith("sb_recent_") -> "$DRAG_PREFIX_NOTE${key.removePrefix("sb_recent_")}"
                                    else -> null
                                }
                            },
                            isDropTarget = { key, payload ->
                                if (key == null) false
                                else when {
                                    key.startsWith(FAVORITE_ROW_PREFIX) ->
                                        payload.startsWith(DRAG_PREFIX_NOTE) &&
                                                payload.removePrefix(DRAG_PREFIX_NOTE) in favoriteNoteIds
                                    key.startsWith("sb_recent_") -> false
                                    HomeItemKey.isFolder(key) &&
                                            payload == "$DRAG_PREFIX_FOLDER${HomeItemKey.folderIdOf(key)}" -> false
                                    else -> true
                                }
                            },
                            onDrop = { payload, targetKey, insertBefore ->
                                when {
                                    targetKey.startsWith(FAVORITE_ROW_PREFIX) &&
                                            payload.startsWith(DRAG_PREFIX_NOTE) -> {
                                        homeViewModel.reorderFavoriteNotes(
                                            draggedNoteId = payload.removePrefix(DRAG_PREFIX_NOTE),
                                            targetNoteId = targetKey.removePrefix(FAVORITE_ROW_PREFIX),
                                            insertBefore = insertBefore,
                                            orderedNoteIds = favoriteNotes.map { it.noteId }
                                        )
                                    }

                                    !insertBefore && HomeItemKey.isFolder(targetKey) &&
                                            dragState.dropPosition == DropInsertPosition.INTO -> {
                                        val folderId = HomeItemKey.folderIdOf(targetKey)
                                        when {
                                            payload.startsWith(DRAG_PREFIX_NOTE) -> homeViewModel.moveNote(payload.removePrefix(DRAG_PREFIX_NOTE), folderId)
                                            payload.startsWith(DRAG_PREFIX_FOLDER) -> homeViewModel.moveFolder(payload.removePrefix(DRAG_PREFIX_FOLDER), folderId)
                                        }
                                    }
                                    else -> {
                                        val draggedKey = when {
                                            payload.startsWith(DRAG_PREFIX_NOTE) -> HomeItemKey.forNote(payload.removePrefix(DRAG_PREFIX_NOTE))
                                            payload.startsWith(DRAG_PREFIX_FOLDER) -> HomeItemKey.forFolder(payload.removePrefix(DRAG_PREFIX_FOLDER))
                                            else -> return@desktopListDragTracker
                                        }
                                        homeViewModel.reorderItems(
                                            draggedKey = draggedKey,
                                            targetKey = targetKey,
                                            insertBefore = insertBefore,
                                            orderedKeys = treeRows.map { it.key }
                                        )
                                    }
                                }
                            }
                        )
                ) {
                    LazyColumn(
                        state = sidebarListState,
                        modifier = Modifier.fillMaxSize().smoothWheelScroll(sidebarListState),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item {
                            Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                                OverviewRow(
                                    painterResource(Res.drawable.check_square),
                                    "Tasks",
                                    "$remindersCount left",
                                    isSelected = detail == DetailPane.Reminders
                                ) { detail = DetailPane.Reminders; isPeeking = false }
                                OverviewRow(
                                    painterResource(Res.drawable.bookmark),
                                    "Bookmarks",
                                    "$bookmarksCount saved",
                                    isSelected = detail == DetailPane.Bookmarks
                                ) { detail = DetailPane.Bookmarks; isPeeking = false }
                                OverviewRow(
                                    painterResource(Res.drawable.images),
                                    "Images",
                                    "$imagesCount saved",
                                    isSelected = detail == DetailPane.Images
                                ) { detail = DetailPane.Images; isPeeking = false }
                                OverviewRow(
                                    painterResource(Res.drawable.notes2),
                                    "Documents",
                                    "$documentsCount attached",
                                    isSelected = detail == DetailPane.Documents
                                ) { detail = DetailPane.Documents; isPeeking = false }
                                SidebarGroupSeparator()
                            }
                        }

                        if (favoriteNotes.isNotEmpty()) {
                            item { SidebarSectionHeader("Favorites", isFavoritesExpanded, { isFavoritesExpanded = !isFavoritesExpanded }) }
                            if (isFavoritesExpanded) {
                                items(favoriteNotes, key = { "$FAVORITE_ROW_PREFIX${it.noteId}" }) { note ->
                                    val favoriteRowKey = HomeItemKey.forNote(note.noteId)
                                    val rowMenuTarget = menuForRow(favoriteRowKey)
                                    SidebarNoteRow(
                                        note = note, level = 0,
                                        isActive = (detail as? DetailPane.Note)?.noteId == note.noteId,
                                        isSelected = selectedNoteIds.contains(note.noteId),
                                        dragState = dragState,
                                        menu = rowMenuTarget.menu,
                                        onClick = { modifiers ->
                                            handleRowClick(favoriteRows, favoriteRowKey, modifiers) { openNote(note.noteId) }
                                        },
                                        onToggleFavorite = {
                                            homeViewModel.setNotesFavorite(rowMenuTarget.noteIds, rowMenuTarget.menu.makeFavorite)
                                        },
                                        onRename = { newTitle -> homeViewModel.renameNote(note.noteId, newTitle) },
                                        onDelete = {
                                            if (rowMenuTarget.usesSelection) homeViewModel.deleteSelectedItems()
                                            else homeViewModel.trashNote(note.noteId)
                                        },
                                        rowKey = "$FAVORITE_ROW_PREFIX${note.noteId}"
                                    )
                                }
                            }
                        }

                        item {
                            SidebarSectionHeader(
                                title = "Notes", isExpanded = isNotesExpanded,
                                onToggle = { isNotesExpanded = !isNotesExpanded },
                                trailing = {
                                    Box {
                                        TopBarIconButtonGroup(
                                            bgColor = MaterialTheme.colorScheme.background,
                                            tint = MaterialTheme.colorScheme.primary,
                                            hazeState = hazeState,
                                            hazeStyle = EmberrBlur.Regular,
                                            iconSize = 20.dp,
                                            shadowSpotColor = EmberrPillShadowSpotColor,
                                            shadowAmbientColor = EmberrPillShadowAmbientColor,
                                            items = listOf(
                                                TopBarIconButtonItem(
                                                    icon = painterResource(Res.drawable.arrow_up_down),
                                                    contentDescription = "Sort",
                                                    onClick = { showSortMenu = true }
                                                ),
                                                TopBarIconButtonItem(
                                                    icon = painterResource(Res.drawable.folder_plus),
                                                    contentDescription = "New folder",
                                                    onClick = { addFolderInput = ""; showAddFolderPopup = true }
                                                ),
                                                TopBarIconButtonItem(
                                                    icon = painterResource(Res.drawable.pen_square),
                                                    contentDescription = "New note",
                                                    onClick = { addNoteInput = ""; showAddNotePopup = true }
                                                )
                                            )
                                        )
                                        EmberrDesktopMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false }) {
                                            DesktopSortMenu(
                                                currentSortType = currentSortType,
                                                currentSortOrder = currentSortOrder,
                                                onDismiss = { showSortMenu = false },
                                                onSortChanged = { type, order ->
                                                    homeViewModel.updateSort(
                                                        type,
                                                        order
                                                    ); showSortMenu = false
                                                })
                                        }
                                        EmberrDesktopMenu(
                                            expanded = showAddNotePopup,
                                            onDismissRequest = { showAddNotePopup = false },
                                            modifier = Modifier.width(280.dp)
                                        ) {
                                            Column(
                                                Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 12.dp
                                                )
                                            ) {
                                                Row(
                                                    Modifier.fillMaxWidth()
                                                        .padding(bottom = 18.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "New Note",
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Icon(
                                                        painter = painterResource(Res.drawable.template),
                                                        contentDescription = "Templates",
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(22.dp)
                                                            .noRippleClickable { handleOpenTemplates() }
                                                    )
                                                }
                                                EmberrTextField(
                                                    value = addNoteInput,
                                                    onValueChange = { addNoteInput = it },
                                                    placeholder = "Note title...",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    onSubmit = {
                                                        if (addNoteInput.isNotBlank()) {
                                                            handleCreateNote(addNoteInput.trim())
                                                            showAddNotePopup = false
                                                        }
                                                    }
                                                )
                                                Row(
                                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(
                                                        8.dp
                                                    )
                                                ) {
                                                    EmberrButtonSecondary(
                                                        text = "Cancel",
                                                        onClick = { showAddNotePopup = false },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    EmberrButtonPrimary(
                                                        text = "Create",
                                                        onClick = {
                                                            if (addNoteInput.isNotBlank()) {
                                                                handleCreateNote(addNoteInput.trim()); showAddNotePopup =
                                                                    false
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                        TemplatesDesktopMenu(
                                            expanded = showTemplatesMenu,
                                            templates = templates,
                                            searchQuery = templateSearchQuery,
                                            onSearchQueryChange = {
                                                homeViewModel.updateTemplateSearchQuery(
                                                    it
                                                )
                                            },
                                            onDismissRequest = { showTemplatesMenu = false },
                                            onTemplateClick = { id ->
                                                showTemplatesMenu = false; handleTemplateClick(
                                                id
                                            )
                                            },
                                            onEditTemplate = { id ->
                                                showTemplatesMenu =
                                                    false; handleEditTemplate(id)
                                            },
                                            onDeleteTemplate = { id ->
                                                homeViewModel.deleteTemplate(
                                                    id
                                                )
                                            },
                                            onCreateNewTemplate = {
                                                showTemplatesMenu =
                                                    false; handleCreateNewTemplate()
                                            }
                                        )
                                        EmberrDesktopMenu(
                                            expanded = showAddFolderPopup,
                                            onDismissRequest = { showAddFolderPopup = false },
                                            modifier = Modifier.width(280.dp)
                                        ) {
                                            Column(
                                                Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 12.dp
                                                )
                                            ) {
                                                Text(
                                                    "New Folder",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(bottom = 18.dp)
                                                )
                                                EmberrTextField(
                                                    value = addFolderInput,
                                                    onValueChange = { addFolderInput = it },
                                                    placeholder = "e.g. Personal, Work...",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    onSubmit = {
                                                        if (addFolderInput.isNotBlank()) {
                                                            handleCreateFolder(addFolderInput.trim())
                                                            showAddFolderPopup = false
                                                        }
                                                    }
                                                )
                                                Row(
                                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(
                                                        8.dp
                                                    )
                                                ) {
                                                    EmberrButtonSecondary(
                                                        text = "Cancel",
                                                        onClick = {
                                                            showAddFolderPopup = false
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    EmberrButtonPrimary(
                                                        text = "Create",
                                                        onClick = {
                                                            if (addFolderInput.isNotBlank()) {
                                                                handleCreateFolder(
                                                                    addFolderInput.trim()
                                                                ); showAddFolderPopup = false
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        if (isNotesExpanded) {
                            itemsIndexed(treeRows, key = { _, row -> row.key }) { index, row ->
                                val rowMenuTarget = menuForRow(row.key)
                                when (row) {
                                    is HomeItem.Folder -> SidebarFolderRow(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                            fadeOutSpec = tween(180, easing = FastOutSlowInEasing),
                                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        ),
                                        folder = row.folder,
                                        level = row.level,
                                        guideLines = treeGuideLines.getOrElse(index) { ROOT_TREE_GUIDE_LINES },
                                        isExpanded = expandedFolderIds.contains(row.folder.folderId),
                                        isSelected = selectedFolderIds.contains(row.folder.folderId),
                                        dragState = dragState,
                                        menu = rowMenuTarget.menu,
                                        onClick = { modifiers ->
                                            handleRowClick(treeRows, row.key, modifiers) {
                                                homeViewModel.toggleFolderExpansion(row.folder.folderId)
                                            }
                                        },
                                        onAddNote = { title -> homeViewModel.createNoteInParent(row.folder.folderId, title = title, autoExpand = true) { newId -> openNote(newId) } },
                                        onAddSubfolder = { name -> homeViewModel.createFolderInParent(row.folder.folderId, name = name, autoExpand = true) },
                                        onRename = { newName -> homeViewModel.renameFolder(row.folder.folderId, newName) },
                                        onDelete = {
                                            if (rowMenuTarget.usesSelection) homeViewModel.deleteSelectedItems()
                                            else homeViewModel.trashFolder(row.folder.folderId)
                                        }
                                    )
                                    is HomeItem.Note -> SidebarNoteRow(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                            fadeOutSpec = tween(180, easing = FastOutSlowInEasing),
                                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        ),
                                        note = row.note,
                                        level = row.level,
                                        guideLines = treeGuideLines.getOrElse(index) { ROOT_TREE_GUIDE_LINES },
                                        isActive = (detail as? DetailPane.Note)?.noteId == row.note.noteId,
                                        isSelected = selectedNoteIds.contains(row.note.noteId),
                                        dragState = dragState,
                                        menu = rowMenuTarget.menu,
                                        onClick = { modifiers ->
                                            handleRowClick(treeRows, row.key, modifiers) { openNote(row.note.noteId) }
                                        },
                                        onToggleFavorite = {
                                            homeViewModel.setNotesFavorite(rowMenuTarget.noteIds, rowMenuTarget.menu.makeFavorite)
                                        },
                                        onRename = { newTitle -> homeViewModel.renameNote(row.note.noteId, newTitle) },
                                        onDelete = {
                                            if (rowMenuTarget.usesSelection) homeViewModel.deleteSelectedItems()
                                            else homeViewModel.trashNote(row.note.noteId)
                                        }
                                    )
                                }
                            }
                        }

                        if (recentNotes.isNotEmpty()) {
                            item { SidebarSectionHeader("Recents", isRecentsExpanded, { isRecentsExpanded = !isRecentsExpanded }) }
                            if (isRecentsExpanded) {
                                items(recentNotes, key = { "sb_recent_${it.noteId}" }) { note ->
                                    SidebarNoteRow(
                                        note = note, level = 0,
                                        isActive = (detail as? DetailPane.Note)?.noteId == note.noteId,
                                        isSelected = false,
                                        dragState = dragState,
                                        onClick = { openNote(note.noteId) },
                                        onRename = { newTitle -> homeViewModel.renameNote(note.noteId, newTitle) },
                                        onDelete = { homeViewModel.trashNote(note.noteId) },
                                        rowKey = "sb_recent_${note.noteId}"
                                    )
                                }
                            }
                        }
                    }

                    DesktopListDragChip(
                        dragState = dragState,
                        labelForPayload = { payload ->
                            when {
                                payload.startsWith(DRAG_PREFIX_NOTE) -> {
                                    val id = payload.removePrefix(DRAG_PREFIX_NOTE)
                                    (notesByFolder.values.flatten() + favoriteNotes + recentNotes)
                                        .find { it.noteId == id }?.title?.ifEmpty { "Untitled" } ?: "Note"
                                }
                                payload.startsWith(DRAG_PREFIX_FOLDER) -> {
                                    val id = payload.removePrefix(DRAG_PREFIX_FOLDER)
                                    foldersByParent.values.flatten().find { it.folderId == id }?.name ?: "Folder"
                                }
                                else -> ""
                            }
                        }
                    )
                }

                    val hasScrolledList by remember { derivedStateOf { sidebarListState.canScrollBackward } }
                    val topEdgeAlpha by animateFloatAsState(
                        if (hasScrolledList) 0.09f else 0f,
                        tween(180, easing = FastOutSlowInEasing),
                        label = "sidebar_top_edge"
                    )
                    HorizontalDivider(
                        modifier = Modifier.align(Alignment.TopCenter),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = topEdgeAlpha)
                    )

                    EmberrVerticalScrollbar(
                        listState = sidebarListState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp)
                    )
                }

            }

            // floating search + AI assistant buttons, mirrors mobile's EmberrBottomBar circles
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endPadding + 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TopBarIconButton(
                    icon = painterResource(Res.drawable.search),
                    contentDescription = "Search",
                    bgColor = MaterialTheme.colorScheme.background,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showSearchDialog = true }
                )
                if (!isAiDisabled) {
                    TopBarIconButton(
                        icon = painterResource(Res.drawable.astroid),
                        contentDescription = "Ask AI",
                        bgColor = MaterialTheme.colorScheme.background,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onAiIconTap
                    )
                }
            }
        }
    }

    // RIGHT PANEL
    val rightPanel = @Composable {
        Box(Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            when (val d = detail) {
                null -> Box(Modifier.fillMaxSize())
                is DetailPane.Daily -> Box(Modifier.fillMaxSize()) {
                    DailyEditorPane(
                        viewModel = dailyViewModel,
                        hazeState = hazeState,
                        isSidebarVisible = isSidebarVisible,
                        onPickImage = onPickImage,
                        onTakePhoto = onTakePhoto,
                        onPickDocument = onPickDocument,
                        onOpenFile = onOpenFile,
                        onNavigateToEditor = { openNote(it) },
                        onExportMarkdown = onExportMarkdown,
                        onExportPdf = onExportPdf,
                        onSelectionModeChange = onSelectionModeChange
                    )
                }
                is DetailPane.Note -> key(d.noteId) {
                    NoteScreen(
                        noteId = d.noteId,
                        onNavigateBack = { detail = DetailPane.Daily(selectedDate) },
                        showBackButton = isSidebarVisible,
                        onSelectionModeChange = onSelectionModeChange,
                        onPickImage = onPickImage, onTakePhoto = onTakePhoto, onPickDocument = onPickDocument,
                        onOpenFile = onOpenFile, onExportMarkdown = onExportMarkdown, onExportPdf = onExportPdf,
                        onNavigateToEditor = { openNote(it) },
                        desktopTopMargin = 0.dp
                    )
                }
                DetailPane.Settings -> key("settings") {
                    Box(Modifier.fillMaxSize()) {
                        SettingsScreen(
                            onNavigateBack = { detail = DetailPane.Daily(selectedDate) },
                            onExportReady = onExportBackup,
                            onImportClick = onImportBackupClick,
                            onNavigateToSelfHostSetup = { detail = DetailPane.SelfHostSetup },
                            showBackButton = isSidebarVisible,
                            syncViewModel = syncViewModel
                        )
                    }
                }
                DetailPane.SelfHostSetup -> key("selfhost_setup") {
                    Box(Modifier.fillMaxSize()) {
                        SelfHostSetupScreen(onNavigateBack = { detail = DetailPane.Settings })
                    }
                }
                DetailPane.Trash -> key("trash") {
                    Box(Modifier.fillMaxSize()) {
                        TrashScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) })
                    }
                }
                DetailPane.Reminders -> key("reminders") {
                    Box(Modifier.fillMaxSize()) {
                        TasksScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }, onOpenFile = onOpenFile, onNavigateToEditor = { openNote(it) })
                    }
                }
                DetailPane.Images -> key("images") {
                    Box(Modifier.fillMaxSize()) {
                        ImagesScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }, onTriggerImagePicker = { onPickImage { } })
                    }
                }
                DetailPane.Documents -> key("documents") {
                    Box(Modifier.fillMaxSize()) {
                        DocumentsScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }, onTriggerDocumentPicker = { onPickDocument { } }, onOpenFile = onOpenFile)
                    }
                }
                DetailPane.Bookmarks -> key("bookmarks") {
                    Box(Modifier.fillMaxSize()) {
                        BookmarksScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) })
                    }
                }
                DetailPane.Calendar -> key("calendar") {
                    Box(Modifier.fillMaxSize()) {
                        CalendarScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) })
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalEmberrBlurSource provides hazeState) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0)) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues).consumeWindowInsets(paddingValues)) {
                Row(modifier = Modifier.fillMaxSize()) {

                    AnimatedVisibility(
                        visible = isSidebarVisible,
                        enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = tween(280, easing = FastOutSlowInEasing)),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = tween(280, easing = FastOutSlowInEasing))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(panelWidth)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            leftPanel(5.dp, 5.dp)
                        }
                    }

                    AnimatedVisibility(
                        visible = isSidebarVisible,
                        enter = fadeIn(tween(280)),
                        exit = fadeOut(tween(280))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(PANEL_PADDING)
                                .background(Color.Transparent)
                                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            settingsManager.saveDesktopSidebarWidth(panelWidth.value)
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val deltaDp = with(density) { dragAmount.toDp() }
                                        panelWidth = (panelWidth + deltaDp).coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH)
                                    }
                                }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 2.dp)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (!isSidebarVisible) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 26.dp, top = PANEL_TOP_MARGIN + 2.dp)
                                    .zIndex(10f)
                            ) {
                                TopBarIconButton(
                                    icon = painterResource(Res.drawable.sidebar),
                                    contentDescription = "Expand sidebar",
                                    bgColor = Color.Transparent,
                                    tint = MaterialTheme.colorScheme.primary,
                                    hazeState = hazeState,
                                    hazeStyle = EmberrBlur.Regular,
                                    onClick = onToggleSidebar
                                )
                            }
                        }
                        rightPanel()
                    }

                    val isRagPanelVisible = isRagChatVisible && ragViewModel != null

                    AnimatedVisibility(
                        visible = isRagPanelVisible,
                        enter = fadeIn(tween(280)),
                        exit = fadeOut(tween(280))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(PANEL_PADDING)
                                .background(Color.Transparent)
                                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaDp = with(density) { dragAmount.toDp() }
                                        ragPanelWidth = (ragPanelWidth - deltaDp).coerceIn(MIN_RAG_PANEL_WIDTH, MAX_RAG_PANEL_WIDTH)
                                    }
                                }
                        )
                    }

                    AnimatedVisibility(
                        visible = isRagPanelVisible,
                        enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(280, easing = FastOutSlowInEasing)),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(280, easing = FastOutSlowInEasing))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(ragPanelWidth)
                                .fillMaxHeight()
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                        ) {
                            if (ragViewModel != null) {
                                com.emberr.presentation.rag.RagChatPanel(
                                    onDismiss = onDismissRagChat,
                                    viewModel = ragViewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    onPickDocument = onPickDocument
                                )
                            }
                        }
                    }
                }

                // Hover-to-peek: thin left-edge trigger (only when collapsed)
                if (!isSidebarVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxHeight()
                            .width(16.dp)
                            .zIndex(20f)
                            .onPointerEvent(PointerEventType.Enter) { isPeeking = true }
                    )
                }

                // Hover-to-peek: invisible boundary to detect when cursor leaves the panel area
                if (!isSidebarVisible && isPeeking) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = panelWidth + PANEL_PADDING)
                            .zIndex(24f)
                            .onPointerEvent(PointerEventType.Enter) { isPeeking = false }
                    )
                }

                // Hover-to-peek: floating sidebar overlay
                AnimatedVisibility(
                    visible = !isSidebarVisible && isPeeking,
                    enter = slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it },
                    exit = slideOutHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { -it },
                    modifier = Modifier.align(Alignment.TopStart).zIndex(25f)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = PANEL_PADDING, top = PANEL_TOP_MARGIN, bottom = PANEL_TOP_MARGIN)
                            .width(panelWidth)
                            .fillMaxHeight()
                            .clip(DesktopPanelShape)
                            .emberrBlur(
                                hazeState,
                                EmberrBlur.Thick.copy(
                                    backgroundColor = MaterialTheme.colorScheme.surface,
                                    tints = listOf(HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))),
                                    fallbackTint = HazeTint(MaterialTheme.colorScheme.surface)
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { }
                    ) {
                        leftPanel(5.dp, 5.dp)
                    }
                }

                // Sheets
                if (showTimelineDialog) {
                    val timelineGlobalTags by dailyViewModel.globalTags.collectAsState()
                    val timelineAllLinkableNotes by dailyViewModel.allLinkableNotes.collectAsState()

                    // Mirrors DailyEditorPane's own EditorActions (same viewModel, same picker
                    // callbacks) so the timeline dialog's real block views - table/database edits,
                    // audio playback, file opening, linked-note lookups - work against the same
                    // daily editor state. Note-link navigation closes the dialog first since it's
                    // opening a different note, not just jumping within this same day's timeline.
                    val timelineEditorActions = remember(dailyViewModel, onOpenFile) {
                        object : EditorActions {
                            override fun onClearSlashQuery() = dailyViewModel.clearActiveSlashQuery()
                            override fun onClearFocusRequest() = dailyViewModel.clearFocusRequest()
                            override fun onUpdateText(id: String, text: String) = dailyViewModel.updateBlockText(id, text)
                            override fun onToggleCheckbox(id: String, checked: Boolean) = dailyViewModel.toggleCheckbox(id, checked)
                            override fun onToggleExpand(id: String) = dailyViewModel.toggleToggleBlock(id)
                            override fun onFocusBlock(id: String) = dailyViewModel.setFocusedBlock(id)
                            override fun onRequestCursorPosition(id: String, offset: Int) = dailyViewModel.requestCursorPosition(id, offset)
                            override fun onChangeBlockType(type: String) = dailyViewModel.changeFocusedBlockType(type)
                            override fun onToggleFormat(format: String) = dailyViewModel.toggleFormat(format)
                            override fun onAdjustIndentation(increase: Boolean) = dailyViewModel.adjustIndentation(increase)
                            override fun onSetBlockAlignment(alignment: TextAlignment) = dailyViewModel.setFocusedBlockAlignment(alignment)
                            override fun onEnterPressed(id: String, before: String, after: String) = dailyViewModel.handleEnter(id, before, after)
                            override fun onBackspaceOnEmpty(id: String) = dailyViewModel.handleBackspaceOnEmpty(id)
                            override fun onToggleSelection(id: String) = dailyViewModel.toggleSelection(id)
                            override fun onUpdateReminder(id: String, timestamp: Long?) = dailyViewModel.updateReminder(id, timestamp)
                            override fun onUrlSubmit(id: String, url: String) = dailyViewModel.handleUrlSubmit(id, url)
                            override fun onImagePicked(id: String, uri: String) = dailyViewModel.handleImagePicked(id, uri)
                            override fun onDocumentPicked(id: String, uri: String) = dailyViewModel.handleDocumentPicked(id, uri)
                            override fun onAddBlankBlock() = dailyViewModel.addBlankBlockBelowFocused()
                            override fun onInsertMediaBlock(type: String) = dailyViewModel.insertNewMediaBlock(type)
                            override fun onSaveDatabaseAsTemplate(blockId: String, templateName: String) =
                                dailyViewModel.saveDatabaseAsTemplate(blockId, templateName)
                            override fun onOutsideTap() {}
                            override fun onUpdateDbTitle(id: String, title: String) = dailyViewModel.updateDbTitle(id, title)
                            override fun onAddDbRow(id: String) = dailyViewModel.addDbRow(id)
                            override fun onAddDbColumn(id: String) = dailyViewModel.addDbColumn(id)
                            override fun onUpdateDbCell(blockId: String, rowId: String, colId: String, value: CellData) = dailyViewModel.updateDbCell(blockId, rowId, colId, value)
                            override fun onUpdateDbColumn(blockId: String, colId: String, name: String, type: ColumnType, isManualNameChange: Boolean) = dailyViewModel.updateDbColumn(blockId, colId, name, type, isManualNameChange)
                            override fun onUpdateDbSort(blockId: String, colId: String, isAscending: Boolean?) = dailyViewModel.updateDbSort(blockId, colId, isAscending)
                            override fun onUpdateDbGroupBy(blockId: String, colId: String?) = dailyViewModel.updateDbGroupBy(blockId, colId)
                            override fun onUpdateDbGalleryCardSize(blockId: String, size: GalleryCardSize) = dailyViewModel.updateDbGalleryCardSize(blockId, size)
                            override fun onToggleKanbanGroupVisibility(blockId: String, viewId: String, groupName: String, isHidden: Boolean) = dailyViewModel.toggleKanbanGroupVisibility(blockId, viewId, groupName, isHidden)
                            override fun onReorderKanbanGroups(blockId: String, viewId: String, orderedGroupKeys: List<String>) = dailyViewModel.reorderKanbanGroups(blockId, viewId, orderedGroupKeys)
                            override fun onAddDbFilter(blockId: String, colId: String, operator: String, value: String) = dailyViewModel.addDbFilter(blockId, colId, operator, value)
                            override fun onRemoveDbFilter(blockId: String, config: FilterConfig) = dailyViewModel.removeDbFilter(blockId, config)
                            override fun onReorderDbColumns(blockId: String, from: Int, to: Int) = dailyViewModel.reorderDbColumns(blockId, from, to)
                            override fun onReorderDbRows(blockId: String, from: Int, to: Int) = dailyViewModel.reorderDbRows(blockId, from, to)
                            override fun onReorderDatabaseViews(blockId: String, from: Int, to: Int) = dailyViewModel.reorderDatabaseViews(blockId, from, to)
                            override fun onUpdateDbFormula(blockId: String, colId: String, expression: String) = dailyViewModel.updateDbFormula(blockId, colId, expression)
                            override fun onDeleteDbColumn(blockId: String, colId: String) = dailyViewModel.deleteDbColumn(blockId, colId)
                            override fun onDeleteDbRow(blockId: String, rowId: String) = dailyViewModel.deleteDbRow(blockId, rowId)
                            override fun onAddDbRowAt(blockId: String, index: Int) = dailyViewModel.addDbRowAt(blockId, index)
                            override fun onAddDbColumnAt(blockId: String, index: Int) = dailyViewModel.addDbColumnAt(blockId, index)
                            override fun onUpdateDbColumnWidth(blockId: String, colId: String, width: Int) = dailyViewModel.updateDbColumnWidth(blockId, colId, width)
                            override fun onVoiceRecorded(id: String, filePath: String, duration: Int) = dailyViewModel.handleVoiceRecorded(id, filePath, duration)
                            override fun onRemoveVoice(id: String) = dailyViewModel.handleRemoveVoice(id)
                            override fun onDeleteImageBlock(id: String) = dailyViewModel.deleteImageBlock(id)
                            override fun onCreateGlobalTag(name: String, colorHex: String): String = dailyViewModel.createGlobalTag(name, colorHex)
                            override fun onRequestImagePicker(blockId: String) {
                                onPickImage { path -> dailyViewModel.handleImagePicked(blockId, path) }
                            }
                            override fun onRequestCamera(blockId: String) {
                                onTakePhoto { path -> dailyViewModel.handleImagePicked(blockId, path) }
                            }
                            override fun onRequestDocumentPicker(blockId: String) {
                                onPickDocument { path -> dailyViewModel.handleDocumentPicked(blockId, path) }
                            }
                            override fun onRequestDbFilePicker(blockId: String, rowId: String, colId: String, isAudio: Boolean) {
                                onPickDocument { path -> dailyViewModel.handleDbFilePicked(blockId, rowId, colId, path) }
                            }
                            override fun onStopDbAudioRecording(blockId: String, rowId: String, colId: String, cancel: Boolean) {
                                dailyViewModel.stopDbHardwareRecording(blockId, rowId, colId, cancel)
                            }
                            override fun onOpenFile(filePath: String, mimeType: String) {
                                onOpenFile(filePath, mimeType)
                            }
                            override fun onStartRecording() = dailyViewModel.startHardwareRecording()
                            override fun onStopRecording(blockId: String, cancel: Boolean) = dailyViewModel.stopHardwareRecording(blockId, cancel)
                            override fun onPlayAudio(filePath: String, onComplete: () -> Unit) = dailyViewModel.playAudio(filePath, onComplete)
                            override fun onStopAudio() = dailyViewModel.stopAudio()
                            override fun onTogglePin() = dailyViewModel.togglePinSelectedBlocks()
                            override fun onUpdateTable(id: String, rows: List<List<String>>) = dailyViewModel.updateTable(id, rows)
                            override fun onUpdateTableColumnWidth(id: String, columnIndex: Int, width: Int) = dailyViewModel.updateTableColumnWidth(id, columnIndex, width)
                            override fun onUpdateTableStyle(
                                id: String,
                                cellStyles: Map<String, com.emberr.domain.model.TableCellStyle>,
                                rowStyles: Map<String, com.emberr.domain.model.TableCellStyle>,
                                columnStyles: Map<String, com.emberr.domain.model.TableCellStyle>
                            ) = dailyViewModel.updateTableStyle(id, cellStyles, rowStyles, columnStyles)
                            override fun onAddBlockAbove(id: String) = dailyViewModel.addBlockAbove(id)
                            override fun onAddBlockBelow(id: String) = dailyViewModel.addBlockBelow(id)
                            override fun onUpdateDbAggregation(blockId: String, colId: String, aggregationType: String?) = dailyViewModel.updateDbAggregation(blockId, colId, aggregationType)
                            override fun onUpdateDbCurrency(blockId: String, colId: String, symbol: String) = dailyViewModel.updateDbCurrency(blockId, colId, symbol)
                            override fun onUpdateDbFormulaCurrency(blockId: String, colId: String, enabled: Boolean) = dailyViewModel.updateDbFormulaCurrency(blockId, colId, enabled)
                            override fun onAddDatabaseView(blockId: String, type: ViewType) = dailyViewModel.addDatabaseView(blockId, type)
                            override fun onDeleteDatabaseView(blockId: String, viewId: String) = dailyViewModel.deleteDatabaseView(blockId, viewId)
                            override fun onSetActiveDatabaseView(blockId: String, viewId: String) = dailyViewModel.setActiveDatabaseView(blockId, viewId)
                            override fun onRenameDatabaseView(blockId: String, viewId: String, newName: String) = dailyViewModel.renameDatabaseView(blockId, viewId, newName)
                            override fun onNoteLinkClick(noteId: String) {
                                showTimelineDialog = false
                                dailyViewModel.clearTimeline()
                                openNote(noteId)
                            }
                            override fun onCreateLinkedNote(title: String): String = dailyViewModel.createLinkedNote(title)
                            override fun onOpenDatabaseNote(blockId: String, rowId: String, colId: String, existingNoteId: String?) {
                                dailyViewModel.openDatabaseNote(blockId, rowId, colId, existingNoteId) { resolvedNoteId ->
                                    showTimelineDialog = false
                                    dailyViewModel.clearTimeline()
                                    openNote(resolvedNoteId)
                                }
                            }
                            override suspend fun getNoteTitle(noteId: String): String = dailyViewModel.getNoteTitle(noteId)
                            override suspend fun getNoteMetadata(noteId: String) = dailyViewModel.getNoteMetadata(noteId)
                            override fun onUpdateLinkedNoteOptions(id: String, showIcon: Boolean, showCoverImage: Boolean) =
                                dailyViewModel.updateLinkedNoteOptions(id, showIcon, showCoverImage)
                        }
                    }

                    DailyTimelineDialog(
                        days = timelineDays,
                        isLoading = isTimelineLoading,
                        anchorDate = selectedDate,
                        today = today,
                        editorActions = timelineEditorActions,
                        globalTags = timelineGlobalTags,
                        allLinkableNotes = timelineAllLinkableNotes,
                        onDismiss = {
                            showTimelineDialog = false
                            dailyViewModel.clearTimeline()
                        },
                        onBlockClick = { date, blockId ->
                            showTimelineDialog = false
                            dailyViewModel.clearTimeline()
                            openDaily(date)
                            dailyViewModel.openTimelineBlock(date, blockId)
                        }
                    )
                }

                if (showSearchDialog) {
                    SearchDialog(
                        onDismiss = { showSearchDialog = false },
                        onNoteClick = { noteId -> showSearchDialog = false; openNote(noteId) },
                        onDailyNoteClick = { dateString -> showSearchDialog = false; openDaily(LocalDate.parse(dateString)) }
                    )
                }

                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 66.dp)) { data ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(horizontal = 24.dp).wrapContentWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class TreeRowMenuTarget(
    val noteIds: Set<String>,
    val folderIds: Set<String>,
    val usesSelection: Boolean,
    val menu: TreeSelectionMenu
)

package com.emberr.presentation.mobile.home

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.emberr.presentation.shared.rememberStableStatusBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.emberr.data.local.prefs.SyncConstants
import com.emberr.data.local.room.FolderEntity
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.domain.model.NoteContent
import com.emberr.domain.util.eventbus.WidgetComposeRequest
import com.emberr.domain.util.eventbus.WidgetComposeRequestBus
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.UserSettings
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.SelectedOptionBackground
import com.emberr.presentation.shared.components.EmberrBottomSheetAction
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.components.EmberrPillShadowAmbientColor
import com.emberr.presentation.shared.components.EmberrPillShadowSpotColor
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.TopHeaderTitlePlacement
import com.emberr.presentation.shared.components.TopBarIconButtonGroup
import com.emberr.presentation.shared.components.topHeaderBarPadding
import com.emberr.presentation.shared.components.TopBarIconButtonItem
import com.emberr.presentation.shared.components.smoothWheelScroll
import com.emberr.presentation.sync.SyncViewModel
import com.emberr.domain.util.system.showNativeToast
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.customEmberrShadow
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.presentation.space.SpaceOptionsSheets
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_up_down
import emberr.shared.generated.resources.calendar
import emberr.shared.generated.resources.circle_plus
import emberr.shared.generated.resources.ellipsis
import emberr.shared.generated.resources.file_text
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.pen_square
import emberr.shared.generated.resources.star
import emberr.shared.generated.resources.folder_plus
import emberr.shared.generated.resources.template
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.x
import org.jetbrains.compose.resources.painterResource

private val HORIZONTAL_PADDING = 16.dp
private val DefaultCornerShape = RoundedCornerShape(12.dp)

@Composable
private fun SectionToggleIcon(isExpanded: Boolean, contentDescription: String) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sectionToggleRotation"
    )
    Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = contentDescription,
        modifier = Modifier.padding(start = 4.dp).size(20.dp).graphicsLayer { rotationZ = rotation },
        tint = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.cardGestures(
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = if (!enabled) this else this.combinedClickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
    onLongClick = onLongClick
)

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onSelectionModeChange: (Boolean) -> Unit = {},
    onNavigateToEditor: (String) -> Unit,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToReminders: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToImages: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    onNavigateToTrash: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onToggleSidebar: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    syncViewModel: SyncViewModel = koinViewModel(),
    spaceViewModel: com.emberr.presentation.space.SpaceViewModel = koinViewModel(),
) {
    val hazeState = remember { HazeState() }

    var showUserSettingsMenu by remember { mutableStateOf(false) }
    var showSpaceOptions by remember { mutableStateOf(false) }

    val spaces by spaceViewModel.spaces.collectAsState()
    val activeSpaceId by spaceViewModel.activeSpaceId.collectAsState()
    val activeSpace = spaces.firstOrNull { it.spaceId == activeSpaceId }

    val isLoading by viewModel.isLoading.collectAsState()
    val foldersByParent by viewModel.foldersByParent.collectAsState()
    val notesByFolder by viewModel.notesByFolder.collectAsState()
    val expandedFolderIds by viewModel.expandedFolderIds.collectAsState()
    val recentNotes by viewModel.recentNotes.collectAsState()
    val selectedNoteIds by viewModel.selectedNoteIds.collectAsState()
    val selectedFolderIds by viewModel.selectedFolderIds.collectAsState()
    val favoriteNotes by viewModel.favoriteNotes.collectAsState()

    val noteCountsByFolder by viewModel.noteCountsByFolder.collectAsState()

    val remindersCount by viewModel.remindersCount.collectAsState()
    val bookmarksCount by viewModel.bookmarksCount.collectAsState()
    val imagesCount by viewModel.imagesCount.collectAsState()
    val documentsCount by viewModel.documentsCount.collectAsState()

    val currentSortType by viewModel.sortType.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()

    val treeRows: List<HomeItem> = remember(
        foldersByParent, notesByFolder, expandedFolderIds, currentSortType, currentSortOrder
    ) {
        flattenFolderTree(
            parentId = null,
            level = 0,
            foldersByParent = foldersByParent,
            notesByFolder = notesByFolder,
            expandedFolderIds = expandedFolderIds,
            sortType = currentSortType,
            sortOrder = currentSortOrder
        )
    }

    val treeGuideLines = remember(treeRows) { treeRows.buildTreeGuideLines() }

    val gridState = rememberLazyStaggeredGridState()
    val favListState = rememberLazyListState()
    val treeDragState = rememberMobileTreeDragState()
    val favoriteDragState = rememberMobileFavoriteDragState()
    var listOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    val blockedDropKeys = remember(treeRows, treeDragState.draggedKey) {
        treeRows.subtreeKeys(treeDragState.draggedKey)
    }
    val currentBlockedDropKeys by rememberUpdatedState(blockedDropKeys)

    val edgeScrollZonePx = with(LocalDensity.current) { 96.dp.toPx() }
    val edgeScrollStepPx = with(LocalDensity.current) { 12.dp.toPx() }

    LaunchedEffect(treeDragState.isDragging) {
        if (!treeDragState.isDragging) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val delta = treeDragState.edgeScrollDelta(gridState, edgeScrollZonePx, edgeScrollStepPx)
            if (delta != 0f) {
                gridState.scrollBy(delta)
                treeDragState.refreshDropTarget(gridState, currentBlockedDropKeys)
            }
        }
    }

    LaunchedEffect(favoriteDragState.isDragging) {
        if (!favoriteDragState.isDragging) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val delta = favoriteDragState.edgeScrollDelta(favListState, edgeScrollZonePx, edgeScrollStepPx)
            if (delta != 0f) {
                favListState.scrollBy(delta)
                favoriteDragState.refreshDropTarget(favListState)
            }
        }
    }

    val handleTreeDrop: (String, String?, DropInsertPosition) -> Unit = { draggedKey, targetKey, position ->
        if (targetKey != null && targetKey != draggedKey) {
            if (position == DropInsertPosition.INTO && HomeItemKey.isFolder(targetKey)) {
                val destinationFolderId = HomeItemKey.folderIdOf(targetKey)
                when {
                    HomeItemKey.isNote(draggedKey) ->
                        viewModel.moveNote(HomeItemKey.noteIdOf(draggedKey), destinationFolderId)

                    HomeItemKey.isFolder(draggedKey) ->
                        viewModel.moveFolder(HomeItemKey.folderIdOf(draggedKey), destinationFolderId)
                }
            } else {
                viewModel.reorderItems(
                    draggedKey = draggedKey,
                    targetKey = targetKey,
                    insertBefore = position == DropInsertPosition.BEFORE,
                    orderedKeys = treeRows.map { it.key }
                )
            }
        }
    }

    val templates by viewModel.filteredTemplates.collectAsState()
    val templateSearchQuery by viewModel.templateSearchQuery.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var addFolderParentId by remember { mutableStateOf<String?>(null) }

    var showAddNotePopup by remember { mutableStateOf(false) }
    var showAddFolderPopup by remember { mutableStateOf(false) }
    var addNoteInput by remember { mutableStateOf("") }
    var addNoteTargetFolderId by remember { mutableStateOf<String?>(null) }
    var addNoteMenuFolderId by remember { mutableStateOf<String?>(null) }
    var addFolderInput by remember { mutableStateOf("") }

    // Mobile sheet + desktop popup toggles for the Templates menu opened from the New Note flow.
    var showTemplatesSheet by remember { mutableStateOf(false) }
    var showTemplatesMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (WidgetComposeRequestBus.consume(WidgetComposeRequest.NEW_NOTE)) {
            if (isDesktopPlatform) {
                addNoteInput = ""
                showAddNotePopup = true
            } else {
                showAddNoteDialog = true
            }
        }
    }

    val isFavoritesExpanded by viewModel.isFavoritesSectionExpanded.collectAsState()
    val isNotesExpanded by viewModel.isNotesSectionExpanded.collectAsState()
    val isRecentsExpanded by viewModel.isRecentsSectionExpanded.collectAsState()


    val isSelectionMode = selectedNoteIds.isNotEmpty() || selectedFolderIds.isNotEmpty()

    val favoriteNoteIds = remember(favoriteNotes) { favoriteNotes.map { it.noteId }.toSet() }
    val selectionMenu = remember(selectedNoteIds, selectedFolderIds, favoriteNoteIds) {
        treeSelectionMenu(selectedNoteIds, selectedFolderIds) { noteId -> noteId in favoriteNoteIds }
    }

    var showRenameSheet by remember { mutableStateOf(false) }
    val renameTargetNoteId = if (selectedFolderIds.isEmpty()) selectedNoteIds.singleOrNull() else null
    val renameTargetFolderId = if (selectedNoteIds.isEmpty()) selectedFolderIds.singleOrNull() else null
    val renameCurrentName = when {
        renameTargetNoteId != null ->
            (notesByFolder.values.flatten() + favoriteNotes + recentNotes)
                .find { it.noteId == renameTargetNoteId }?.title.orEmpty()

        renameTargetFolderId != null ->
            foldersByParent.values.flatten().find { it.folderId == renameTargetFolderId }?.name.orEmpty()

        else -> ""
    }

    val syncState by syncViewModel.syncStatus.collectAsState()

    LaunchedEffect(syncState) {
        if (syncState != "Idle" && syncState != "Syncing...") {
            showNativeToast(syncState)
            syncViewModel.resetSyncStatus()
        }
    }

    KmpBackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }

    val handleCreateFolder = { name: String ->
        val parentFolderId = addFolderParentId
        if (parentFolderId == null) viewModel.createNewFolder(name)
        else viewModel.createFolderInParent(parentFolderId, name = name, autoExpand = true)
        addFolderParentId = null
        showAddFolderDialog = false
    }

    val handleCreateNote = { title: String ->
        viewModel.createNoteInParent(addNoteTargetFolderId, title = title) { newNoteId ->
            onNavigateToEditor(newNoteId)
        }
        showAddNoteDialog = false
    }

    // Re-seeds any missing predefined template every time either Templates entry point opens.
    // Also closes the mobile New Note sheet it's invoked from - closeAnd() only runs the hide
    // animation, it doesn't flip showAddNoteDialog itself (see AddNoteBottomSheet's onCreate,
    // which does that inline), so this has to be the one to reset it, or the sheet is left
    // mounted (hidden but expanded = true) after the templates sheet opens on top of it.
    val handleOpenTemplates = {
        viewModel.onTemplatesMenuOpened()
        showAddNoteDialog = false
        if (isDesktopPlatform) showTemplatesMenu = true else showTemplatesSheet = true
    }
    val handleTemplateClick = { templateId: String ->
        viewModel.createNoteFromTemplate(templateId) { newNoteId -> onNavigateToEditor(newNoteId) }
    }
    // Opens the template's own note directly - unlike handleTemplateClick, this does NOT clone
    // it into a new note. The editor already renders the "Editing Template" pill for any note
    // with isTemplate = true, so no separate "template edit mode" is needed here.
    val handleEditTemplate = { templateId: String -> onNavigateToEditor(templateId) }
    val handleCreateNewTemplate = {
        viewModel.saveAsTemplate(title = "", content = NoteContent(blocks = emptyList())) { newTemplateId ->
            onNavigateToEditor(newTemplateId)
        }
    }

    val homeGridContent = @Composable {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { listOriginInRoot = it.positionInRoot() }
        ) {
            val cardWidth = (maxWidth - (HORIZONTAL_PADDING * 2) - 10.dp) / 2

            Crossfade(
                targetState = isLoading,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "homeContentLoading"
            ) { loading ->
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        top = (if (isDesktopPlatform) 64.dp else 76.dp) + rememberStableStatusBarsPadding().calculateTopPadding(),
                        bottom = bottomContentPadding + 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
                    verticalItemSpacing = 0.dp,
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(MaterialTheme.colorScheme.background)
                ) {
                    item {
                        Box(Modifier.padding(start = HORIZONTAL_PADDING).padding(bottom = 10.dp)) {
                            OverviewCard("Tasks", "$remindersCount left", onClick = { onNavigateToReminders() })
                        }
                    }
                    item {
                        Box(Modifier.padding(end = HORIZONTAL_PADDING).padding(bottom = 10.dp)) {
                            OverviewCard("Bookmarks", "$bookmarksCount saved", onClick = { onNavigateToBookmarks() })
                        }
                    }
                    item {
                        Box(Modifier.padding(start = HORIZONTAL_PADDING).padding(bottom = 10.dp)) {
                            OverviewCard("Images", "$imagesCount saved", onClick = { onNavigateToImages() })
                        }
                    }
                    item {
                        Box(Modifier.padding(end = HORIZONTAL_PADDING).padding(bottom = 10.dp)) {
                            OverviewCard(
                                "Documents",
                                "$documentsCount attached",
                                onClick = { onNavigateToDocuments() })
                        }
                    }

                    if (favoriteNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(
                                    start = HORIZONTAL_PADDING,
                                    end = HORIZONTAL_PADDING,
                                    top = 14.dp,
                                    bottom = 8.dp
                                ), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                        .noRippleClickable {
                                            viewModel.toggleHomeSection(SyncConstants.HOME_SECTION_FAVORITES)
                                        }.padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Favorites",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    SectionToggleIcon(isFavoritesExpanded, "Toggle Favorites")
                                }
                            }
                        }
                        if (isFavoritesExpanded) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                LazyRow(
                                    state = favListState,
                                    modifier = Modifier.fillMaxWidth()
                                        .smoothWheelScroll(favListState, horizontal = true),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = HORIZONTAL_PADDING)
                                ) {
                                    items(
                                        favoriteNotes,
                                        key = { "$FAVORITE_CARD_KEY_PREFIX${it.noteId}" }
                                    ) { note ->
                                        Box(
                                            modifier = Modifier
                                                .width(cardWidth)
                                                .mobileFavoriteDragSource(
                                                    noteId = note.noteId,
                                                    dragState = favoriteDragState,
                                                    rowState = favListState,
                                                    dragEnabled = !isSelectionMode,
                                                    onClick = {
                                                        if (isSelectionMode) viewModel.toggleNoteSelection(note.noteId)
                                                        else onNavigateToEditor(note.noteId)
                                                    },
                                                    onLongPress = { viewModel.toggleNoteSelection(note.noteId) },
                                                    onDrop = { targetNoteId, insertBefore ->
                                                        if (targetNoteId != null) {
                                                            viewModel.reorderFavoriteNotes(
                                                                draggedNoteId = note.noteId,
                                                                targetNoteId = targetNoteId,
                                                                insertBefore = insertBefore,
                                                                orderedNoteIds = favoriteNotes.map { it.noteId }
                                                            )
                                                        }
                                                    }
                                                )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .mobileFavoriteDraggedCard(favoriteDragState, note.noteId)
                                            ) {
                                                NoteCard(
                                                    note = note,
                                                    isSelected = selectedNoteIds.contains(note.noteId),
                                                    onClick = {},
                                                    onLongClick = {},
                                                    handlesGestures = false
                                                )
                                            }
                                            MobileFavoriteInsertLine(
                                                visible = favoriteDragState.isInsertBefore(note.noteId),
                                                atStart = true
                                            )
                                            MobileFavoriteInsertLine(
                                                visible = favoriteDragState.isInsertAfter(note.noteId),
                                                atStart = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (treeRows.isNotEmpty() || !isSelectionMode) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = HORIZONTAL_PADDING, end = HORIZONTAL_PADDING, top = 26.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(modifier = Modifier.clip(RoundedCornerShape(4.dp)).noRippleClickable { viewModel.toggleHomeSection(SyncConstants.HOME_SECTION_NOTES) }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Notes",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    SectionToggleIcon(isNotesExpanded, "Toggle Notes")
                                }
                                if (!isSelectionMode && isDesktopPlatform) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box {
                                            Icon(
                                                painterResource(Res.drawable.arrow_up_down),
                                                "Sort",
                                                modifier = Modifier.size(20.dp).clip(CircleShape)
                                                    .noRippleClickable { showSortMenu = true },
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isDesktopPlatform) {
                                                EmberrDesktopMenu(
                                                    expanded = showSortMenu,
                                                    onDismissRequest = { showSortMenu = false }) {
                                                    DesktopSortMenu(
                                                        currentSortType = currentSortType,
                                                        currentSortOrder = currentSortOrder,
                                                        onDismiss = { showSortMenu = false },
                                                        onSortChanged = { type, order ->
                                                            viewModel.updateSort(
                                                                type,
                                                                order
                                                            ); showSortMenu = false
                                                        })
                                                }
                                            }
                                        }
                                        Box {
                                            Icon(
                                                painterResource(Res.drawable.folder_plus),
                                                "New Folder",
                                                modifier = Modifier.size(20.dp)
                                                    .noRippleClickable {
                                                        addFolderParentId = null
                                                        if (isDesktopPlatform) {
                                                            addFolderInput = ""; showAddFolderPopup = true
                                                        } else showAddFolderDialog = true
                                                    },
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isDesktopPlatform) {
                                                EmberrDesktopMenu(
                                                    expanded = showAddFolderPopup,
                                                    onDismissRequest = { showAddFolderPopup = false },
                                                    modifier = Modifier.width(280.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(
                                                            horizontal = 16.dp,
                                                            vertical = 12.dp
                                                        )
                                                    ) {
                                                        Text(
                                                            "New Folder",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.padding(bottom = 10.dp)
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
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            EmberrButtonSecondary(
                                                                text = "Cancel",
                                                                onClick = { showAddFolderPopup = false },
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            EmberrButtonPrimary(
                                                                text = "Create",
                                                                onClick = {
                                                                    if (addFolderInput.isNotBlank()) {
                                                                        handleCreateFolder(addFolderInput.trim())
                                                                        showAddFolderPopup = false
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Box {
                                            Icon(painterResource(Res.drawable.pen_square), "New Note", modifier = Modifier.size(22.dp).noRippleClickable { addNoteTargetFolderId = null; if (isDesktopPlatform) { addNoteInput = ""; showAddNotePopup = true } else showAddNoteDialog = true }, tint = MaterialTheme.colorScheme.onSurface)
                                            if (isDesktopPlatform) {
                                                EmberrDesktopMenu(expanded = showAddNotePopup, onDismissRequest = { showAddNotePopup = false }, modifier = Modifier.width(280.dp)) {
                                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth()
                                                                .padding(bottom = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                "New Note",
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Icon(
                                                                painter = painterResource(Res.drawable.template),
                                                                contentDescription = "Templates",
                                                                tint = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier.size(24.dp)
                                                                    .noRippleClickable {
                                                                        showAddNotePopup = false
                                                                        handleOpenTemplates()
                                                                    }
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
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(
                                                                8.dp
                                                            )
                                                        ) {
                                                            EmberrButtonSecondary(
                                                                text = "Cancel",
                                                                onClick = {
                                                                    showAddNotePopup = false
                                                                },
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            EmberrButtonPrimary(
                                                                text = "Create",
                                                                onClick = {
                                                                    if (addNoteInput.isNotBlank()) {
                                                                        handleCreateNote(
                                                                            addNoteInput.trim()
                                                                        ); showAddNotePopup = false
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            TemplatesDesktopMenu(
                                                expanded = showTemplatesMenu,
                                                templates = templates,
                                                searchQuery = templateSearchQuery,
                                                onSearchQueryChange = { viewModel.updateTemplateSearchQuery(it) },
                                                onDismissRequest = { showTemplatesMenu = false },
                                                onTemplateClick = { id -> showTemplatesMenu = false; handleTemplateClick(id) },
                                                onEditTemplate = { id -> showTemplatesMenu = false; handleEditTemplate(id) },
                                                onDeleteTemplate = { id -> viewModel.deleteTemplate(id) },
                                                onCreateNewTemplate = { showTemplatesMenu = false; handleCreateNewTemplate() }
                                            )
                                        }
                                    }
                                }
                                if (!isSelectionMode && !isDesktopPlatform) {
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
                                                contentDescription = "New Folder",
                                                onClick = {
                                                    addFolderParentId = null
                                                    showAddFolderDialog = true
                                                }
                                            ),
                                            TopBarIconButtonItem(
                                                icon = painterResource(Res.drawable.pen_square),
                                                contentDescription = "New Note",
                                                onClick = {
                                                    addNoteTargetFolderId = null
                                                    showAddNoteDialog = true
                                                }
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (isNotesExpanded) {
                        if (treeRows.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "home_empty_state") {
                                HomeEmptyState(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(200, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(160, easing = FastOutSlowInEasing),
                                        placementSpec = null
                                    )
                                )
                            }
                        }

                        itemsIndexed(
                            treeRows,
                            key = { _, row -> row.key },
                            span = { _, _ -> StaggeredGridItemSpan.FullLine }
                        ) { index, row ->
                            Box(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(200, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(160, easing = FastOutSlowInEasing),
                                        placementSpec = null
                                    )
                                    .padding(horizontal = HORIZONTAL_PADDING)
                                    .mobileTreeDragSource(
                                        itemKey = row.key,
                                        dragState = treeDragState,
                                        gridState = gridState,
                                        blockedTargetKeys = blockedDropKeys,
                                        dragEnabled = !isSelectionMode,
                                        onClick = {
                                            when (row) {
                                                is HomeItem.Folder ->
                                                    if (isSelectionMode) viewModel.toggleFolderSelection(row.folder.folderId)
                                                    else viewModel.toggleFolderExpansion(row.folder.folderId)

                                                is HomeItem.Note ->
                                                    if (isSelectionMode) viewModel.toggleNoteSelection(row.note.noteId)
                                                    else onNavigateToEditor(row.note.noteId)
                                            }
                                        },
                                        onLongPress = {
                                            when (row) {
                                                is HomeItem.Folder -> viewModel.toggleFolderSelection(row.folder.folderId)
                                                is HomeItem.Note -> viewModel.toggleNoteSelection(row.note.noteId)
                                            }
                                        },
                                        onDrop = { targetKey, position ->
                                            handleTreeDrop(row.key, targetKey, position)
                                        }
                                    )
                            ) {
                                when (row) {
                                    is HomeItem.Folder -> {
                                        MobileTreeFolderRow(
                                            folder = row.folder,
                                            level = row.level,
                                            guideLines = treeGuideLines.getOrElse(index) { ROOT_TREE_GUIDE_LINES },
                                            isExpanded = expandedFolderIds.contains(row.folder.folderId),
                                            isSelected = selectedFolderIds.contains(row.folder.folderId),
                                            noteCount = noteCountsByFolder[row.folder.folderId] ?: 0,
                                            dragState = treeDragState,
                                            showAddNoteAction = !isSelectionMode && !treeDragState.isDragging,
                                            onAddNote = {
                                                addNoteTargetFolderId = row.folder.folderId
                                                if (isDesktopPlatform) {
                                                    addNoteInput = ""
                                                    addNoteMenuFolderId = row.folder.folderId
                                                } else {
                                                    showAddNoteDialog = true
                                                }
                                            },
                                            showAddSubfolderAction = !isDesktopPlatform &&
                                                    !isSelectionMode && !treeDragState.isDragging,
                                            onAddSubfolder = {
                                                addFolderParentId = row.folder.folderId
                                                showAddFolderDialog = true
                                            }
                                        )

                                        if (isDesktopPlatform) {
                                            NewNoteInFolderMenu(
                                                expanded = addNoteMenuFolderId == row.folder.folderId,
                                                folderName = row.folder.name,
                                                input = addNoteInput,
                                                onInputChange = { addNoteInput = it },
                                                onDismiss = { addNoteMenuFolderId = null },
                                                onCreate = { title ->
                                                    addNoteMenuFolderId = null
                                                    handleCreateNote(title)
                                                }
                                            )
                                        }
                                    }

                                    is HomeItem.Note -> MobileTreeNoteRow(
                                        note = row.note,
                                        level = row.level,
                                        guideLines = treeGuideLines.getOrElse(index) { ROOT_TREE_GUIDE_LINES },
                                        isSelected = selectedNoteIds.contains(row.note.noteId),
                                        dragState = treeDragState
                                    )
                                }
                            }
                        }
                    }

                    if (recentNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = HORIZONTAL_PADDING, end = HORIZONTAL_PADDING, top = 26.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                        .noRippleClickable {
                                            viewModel.toggleHomeSection(SyncConstants.HOME_SECTION_RECENTS)
                                        }.padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Recents",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    SectionToggleIcon(isRecentsExpanded, "Toggle Recents")
                                }
                            }
                        }
                        if (isRecentsExpanded) {
                            items(
                                recentNotes,
                                key = { note -> "recent_${note.noteId}" },
                                span = { StaggeredGridItemSpan.FullLine }
                            ) { note ->
                                Box(
                                    modifier = Modifier
                                        .animateItem(
                                            fadeInSpec = tween(200, easing = FastOutSlowInEasing),
                                            fadeOutSpec = tween(160, easing = FastOutSlowInEasing),
                                            placementSpec = null
                                        )
                                        .padding(horizontal = HORIZONTAL_PADDING)
                                        .cardGestures(
                                            enabled = true,
                                            onClick = {
                                                if (isSelectionMode) viewModel.toggleNoteSelection(note.noteId)
                                                else onNavigateToEditor(note.noteId)
                                            },
                                            onLongClick = { viewModel.toggleNoteSelection(note.noteId) }
                                        )
                                ) {
                                    MobileTreeNoteRow(
                                        note = note,
                                        level = 0,
                                        isSelected = selectedNoteIds.contains(note.noteId),
                                        dragState = IdleTreeDragState
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }

            val floatingRow = treeDragState.draggedKey?.let { key ->
                treeRows.firstOrNull { it.key == key }
            }
            if (floatingRow != null && treeDragState.floatingSize.width > 0) {
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { treeDragState.floatingSize.width.toDp() },
                            height = with(density) { treeDragState.floatingSize.height.toDp() }
                        )
                        .mobileTreeFloatingRow(treeDragState, listOriginInRoot)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    when (floatingRow) {
                        is HomeItem.Folder -> MobileTreeFolderRow(
                            folder = floatingRow.folder,
                            level = 0,
                            isExpanded = expandedFolderIds.contains(floatingRow.folder.folderId),
                            isSelected = false,
                            noteCount = noteCountsByFolder[floatingRow.folder.folderId] ?: 0,
                            dragState = IdleTreeDragState,
                            showAddNoteAction = false,
                            onAddNote = {}
                        )

                        is HomeItem.Note -> MobileTreeNoteRow(
                            note = floatingRow.note,
                            level = 0,
                            isSelected = false,
                            dragState = IdleTreeDragState
                        )
                    }
                }
            }

            EmberrTopHeaderBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .pointerInput(Unit) { detectTapGestures {} },
                title = if (isSelectionMode) "" else activeSpace?.displayName.orEmpty(),
                titlePlacement = TopHeaderTitlePlacement.Start,
                titleStyle = MaterialTheme.typography.titleLarge,
                titleColor = MaterialTheme.colorScheme.onBackground,
                titleTrailingIcon = if (isSelectionMode) null else {
                    {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch space",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                onTitleClick = if (isSelectionMode) null else {
                    { showSpaceOptions = true }
                },
                showBackButton = false,
                reserveBackButtonSpace = false,
                hazeState = hazeState,
                applyStatusBarPadding = true,
                contentPadding = topHeaderBarPadding(top = 10.dp, bottom = 16.dp),
                leadingContent = {
                    if (!isSelectionMode && isDesktopPlatform) {
                        IconButton(
                            onClick = onToggleSidebar,
                            modifier = Modifier.offset(x = (-8).dp)
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Toggle sidebar",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        TopBarIconButtonGroup(
                            bgColor = Color.Transparent,
                            tint = MaterialTheme.colorScheme.primary,
                            hazeState = hazeState,
                            hazeStyle = EmberrBlur.Regular,
                            items = listOf(
                                TopBarIconButtonItem(
                                    icon = painterResource(Res.drawable.calendar),
                                    contentDescription = "Open Calendar",
                                    onClick = onNavigateToCalendar
                                ),
                                TopBarIconButtonItem(
                                    icon = painterResource(Res.drawable.ellipsis),
                                    contentDescription = "Settings",
                                    onClick = { showUserSettingsMenu = true }
                                )
                            )
                        )

                        UserSettings(
                            expanded = showUserSettingsMenu,
                            onDismiss = { showUserSettingsMenu = false },
                            onNavigateToSettings = {
                                showUserSettingsMenu = false
                                onNavigateToSettings()
                            },
                            onNavigateToTrash = {
                                showUserSettingsMenu = false
                                onNavigateToTrash()
                            }
                        )
                    }
                }
            )
        }
    }

    // Scaffold
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0)) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {

            homeGridContent()

            SpaceOptionsSheets(
                expanded = showSpaceOptions,
                spaces = spaces,
                activeSpaceId = activeSpaceId,
                onDismiss = { showSpaceOptions = false },
                onOpenSpace = { spaceId -> spaceViewModel.openSpace(spaceId) },
                onReorderSpaces = { orderedIds -> spaceViewModel.reorderSpaces(orderedIds) },
                onCreateSpace = { name -> spaceViewModel.createSpaceAndOpenIt(name) },
                onRenameSpace = { name -> spaceViewModel.renameSpace(activeSpaceId, name) },
                onDeleteSpace = { spaceViewModel.deleteSpace(activeSpaceId) }
            )

            NotesSelectionPill(
                isVisible = isSelectionMode,
                selectedCount = selectedNoteIds.size + selectedFolderIds.size,
                showRename = selectionMenu.showRename,
                showFavorite = selectionMenu.showFavorite,
                favoriteLabel = selectionMenu.favoriteLabel,
                onClearSelection = { viewModel.clearSelection() },
                onRename = { showRenameSheet = true },
                onToggleFavorite = { viewModel.setNotesFavorite(selectedNoteIds, selectionMenu.makeFavorite) },
                onDelete = { viewModel.deleteSelectedItems() },
                modifier = Modifier.align(Alignment.BottomCenter),
                hazeState = hazeState
            )

            if (!isDesktopPlatform) {
                AddFolderBottomSheet(
                    expanded = showAddFolderDialog,
                    onDismiss = { showAddFolderDialog = false; addFolderParentId = null },
                    onCreate = handleCreateFolder,
                    destinationFolderName = addFolderParentId?.let { folderId ->
                        foldersByParent.values.flatten().find { it.folderId == folderId }?.name
                    }
                )
                AddNoteBottomSheet(
                    expanded = showAddNoteDialog,
                    destinationFolderName = addNoteTargetFolderId?.let { folderId ->
                        foldersByParent.values.flatten().find { it.folderId == folderId }?.name
                    },
                    onDismiss = { showAddNoteDialog = false },
                    onCreate = handleCreateNote,
                    onOpenTemplates = handleOpenTemplates
                )
                RenameBottomSheet(
                    expanded = showRenameSheet,
                    currentName = renameCurrentName,
                    onDismiss = { showRenameSheet = false },
                    onRename = { newName ->
                        when {
                            renameTargetNoteId != null -> viewModel.renameNote(renameTargetNoteId, newName)
                            renameTargetFolderId != null -> viewModel.renameFolder(renameTargetFolderId, newName)
                        }
                        showRenameSheet = false
                        viewModel.clearSelection()
                    }
                )
                SortBottomSheet(
                    expanded = showSortMenu,
                    currentSortType = currentSortType,
                    currentSortOrder = currentSortOrder,
                    onDismiss = { showSortMenu = false },
                    onSortChanged = { type, order ->
                        viewModel.updateSort(
                            type,
                            order
                        ); showSortMenu = false
                    })
                TemplatesBottomSheet(
                    expanded = showTemplatesSheet,
                    templates = templates,
                    searchQuery = templateSearchQuery,
                    onSearchQueryChange = { viewModel.updateTemplateSearchQuery(it) },
                    onDismiss = { showTemplatesSheet = false },
                    onTemplateClick = handleTemplateClick,
                    onEditTemplate = handleEditTemplate,
                    onDeleteTemplate = { id -> viewModel.deleteTemplate(id) },
                    onCreateNewTemplate = handleCreateNewTemplate
                )
            }

        }
    }
}

@Composable
fun DesktopSortMenu(currentSortType: SortType, currentSortOrder: SortOrder, onDismiss: () -> Unit, onSortChanged: (SortType, SortOrder) -> Unit) {
    Column(modifier = Modifier.width(200.dp).padding(vertical = 4.dp)) {
        DesktopSortOptionItem(
            "Last Edited",
            currentSortType == SortType.LAST_EDITED
        ) { onDismiss(); onSortChanged(SortType.LAST_EDITED, currentSortOrder) }
        DesktopSortOptionItem(
            "Date Created",
            currentSortType == SortType.DATE_CREATED
        ) { onDismiss(); onSortChanged(SortType.DATE_CREATED, currentSortOrder) }
        DesktopSortOptionItem(
            "Name (A-Z)",
            currentSortType == SortType.NAME
        ) { onDismiss(); onSortChanged(SortType.NAME, currentSortOrder) }
        DesktopSortOptionItem(
            "Type",
            currentSortType == SortType.TYPE
        ) { onDismiss(); onSortChanged(SortType.TYPE, currentSortOrder) }
        DesktopSortOptionItem("Manual", currentSortType == SortType.MANUAL) {
            onDismiss(); onSortChanged(SortType.MANUAL, currentSortOrder)
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
        DesktopSortOptionItem(
            "Ascending",
            currentSortOrder == SortOrder.ASCENDING
        ) { onDismiss(); onSortChanged(currentSortType, SortOrder.ASCENDING) }
        DesktopSortOptionItem(
            "Descending",
            currentSortOrder == SortOrder.DESCENDING
        ) { onDismiss(); onSortChanged(currentSortType, SortOrder.DESCENDING) }
    }
}

@Composable
private fun DesktopSortOptionItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SelectedOptionBackground else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HomeEmptyState(modifier: Modifier = Modifier) {
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = HORIZONTAL_PADDING, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(Res.drawable.file_text),
            null,
            modifier = Modifier.size(26.dp),
            tint = mutedColor
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "No notes available",
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor
        )
    }
}

@Composable
fun OverviewCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = DefaultCornerShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clip(DefaultCornerShape).noRippleClickable { onClick() }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun BreadcrumbTrail(selectedFolderId: String?, breadcrumbs: List<FolderEntity>, onNavigate: (String?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
        item {
            val isRoot = selectedFolderId == null
            Text(
                "Home",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Medium,
                color = if (isRoot) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.noRippleClickable { onNavigate(null) })
        }
        items(breadcrumbs) { folder ->
            Icon(
                Icons.Default.ChevronRight,
                null,
                modifier = Modifier.padding(horizontal = 6.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            val isLast = folder.folderId == selectedFolderId
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                color = if (isLast) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.noRippleClickable { onNavigate(folder.folderId) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteMetadataEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    handlesGestures: Boolean = true
) {
    val mediaStorageHelper: com.emberr.domain.util.media.MediaStorageHelper = koinInject()
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.onSurface; isDesktopPlatform -> MaterialTheme.colorScheme.background; else -> MaterialTheme.colorScheme.surface
    }
    val titleColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val mutedColor =
        if (isSelected) MaterialTheme.colorScheme.background.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val coverHeight = 72.dp
    val iconOverhang = 12.dp
    val hasCover = note.coverImagePath != null
    val hasIcon = !note.icon.isNullOrEmpty()
    val hasHeader = hasCover || hasIcon

    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(DefaultCornerShape)
            .background(bgColor)
            .cardGestures(handlesGestures, onClick, onLongClick)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (hasHeader) {
                Box(modifier = Modifier.fillMaxWidth().height(coverHeight)) {
                    if (note.coverImagePath != null) {
                        val absolutePath =
                            mediaStorageHelper.getAbsoluteMediaPath(note.coverImagePath)
                        val context = coil3.compose.LocalPlatformContext.current
                        val request = remember(absolutePath) {
                            coil3.request.ImageRequest.Builder(context).data(absolutePath)
                                .memoryCacheKey(absolutePath).diskCacheKey(absolutePath).build()
                        }
                        AsyncImage(
                            model = request,
                            contentDescription = "Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 0.12f else 0.05f))
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(
                    start = 12.dp,
                    end = if (note.isFavorite && !hasHeader) 26.dp else 12.dp,
                    top = if (hasIcon) iconOverhang + 10.dp else 10.dp,
                    bottom = 10.dp
                )
            ) {
                Text(
                    note.title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(text = note.snippet.takeIf { it.isNotBlank() } ?: "Empty note...",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        if (hasIcon) Text(
            text = note.icon,
            fontSize = 22.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp)
                .offset(y = coverHeight - iconOverhang)
        )
        if (note.isFavorite) Icon(
            Icons.Default.Star,
            "Favorite",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp)
        )
        if (isSelected) Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun NotesSelectionPill(
    isVisible: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    showRename: Boolean = false,
    showFavorite: Boolean = false,
    favoriteLabel: String = "Add to Favorites",
    onRename: () -> Unit = {},
    onToggleFavorite: () -> Unit = {}
) {
    val tint = MaterialTheme.colorScheme.primary

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.padding(horizontal = 24.dp)
    ) {
        Surface(
            shape = DefaultCornerShape,
            color = Color.Transparent,
            modifier = Modifier
                .padding(bottom = 32.dp)
                .customEmberrShadow(DefaultCornerShape)
                .clip(DefaultCornerShape)
                .emberrBlur(hazeState, EmberrBlur.Regular)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = DefaultCornerShape
                )
        ) {
            val pillScroll = rememberScrollState()
            Row(
                modifier = Modifier.horizontalScroll(pillScroll)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Icon(
                    painterResource(Res.drawable.x),
                    "Clear",
                    modifier = Modifier.size(18.dp).noRippleClickable { onClearSelection() },
                    tint = tint
                )
                Text(
                    "$selectedCount",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tint
                )
                Box(Modifier.width(1.dp).height(18.dp).background(tint.copy(alpha = 0.2f)))
                if (showRename) {
                    Icon(
                        painterResource(Res.drawable.pen),
                        "Rename",
                        modifier = Modifier.size(18.dp).noRippleClickable { onRename() },
                        tint = tint
                    )
                }
                if (showFavorite) {
                    Icon(
                        painterResource(Res.drawable.star),
                        favoriteLabel,
                        modifier = Modifier.size(18.dp).noRippleClickable { onToggleFavorite() },
                        tint = tint
                    )
                }
                Icon(
                    painterResource(Res.drawable.trash),
                    "Move to Trash",
                    modifier = Modifier.size(18.dp).noRippleClickable { onDelete() },
                    tint = tint
                )
            }
        }
    }
}

@Composable
fun RenameBottomSheet(
    expanded: Boolean,
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    title: String = "Rename",
    subtitle: String = "Pick a new name.",
    confirmLabel: String = "Save",
    placeholder: String = "Name..."
) {
    var newName by remember(currentName) { mutableStateOf(currentName) }
    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = title,
        subtitle = subtitle
    ) { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp)) {
            EmberrTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth(),
                onSubmit = { if (newName.isNotBlank()) closeAnd { onRename(newName.trim()) } }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmberrButtonSecondary(
                    text = "Cancel",
                    onClick = { closeAnd(onDismiss) },
                    modifier = Modifier.weight(1f)
                )
                EmberrButtonPrimary(
                    text = confirmLabel,
                    onClick = { if (newName.isNotBlank()) closeAnd { onRename(newName.trim()) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AddFolderBottomSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    destinationFolderName: String? = null
) {
    var folderName by remember { mutableStateOf("") }
    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = if (destinationFolderName != null) "New Subfolder" else "New Folder",
        subtitle = if (destinationFolderName != null) "Nesting inside $destinationFolderName."
        else "Organize your notes."
    ) { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp)) {
            EmberrTextField(
                value = folderName,
                onValueChange = { folderName = it },
                placeholder = "e.g. Personal, Work...",
                modifier = Modifier.fillMaxWidth(),
                onSubmit = { if (folderName.isNotBlank()) closeAnd { onCreate(folderName.trim()) } }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmberrButtonSecondary(
                    text = "Cancel",
                    onClick = { closeAnd(onDismiss) },
                    modifier = Modifier.weight(1f)
                )
                EmberrButtonPrimary(
                    text = "Create",
                    onClick = { if (folderName.isNotBlank()) closeAnd { onCreate(folderName.trim()) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AddNoteBottomSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onOpenTemplates: () -> Unit = {},
    destinationFolderName: String? = null
) {
    var noteTitle by remember { mutableStateOf("") }
    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "New Note",
        subtitle = if (destinationFolderName != null) "Saving into $destinationFolderName."
        else "Give your note a fresh title.",
        headerAction = EmberrBottomSheetAction(
            icon = painterResource(Res.drawable.template),
            contentDescription = "Templates",
            onClick = onOpenTemplates
        )
    ) { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp,bottom = 16.dp)) {
            EmberrTextField(
                value = noteTitle,
                onValueChange = { noteTitle = it },
                placeholder = "Note title...",
                modifier = Modifier.fillMaxWidth(),
                onSubmit = { closeAnd { onCreate(noteTitle.trim()) } }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmberrButtonSecondary(
                    text = "Cancel",
                    onClick = { closeAnd(onDismiss) },
                    modifier = Modifier.weight(1f)
                )
                EmberrButtonPrimary(
                    text = "Create",
                    onClick = { closeAnd { onCreate(noteTitle.trim()) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NewNoteInFolderMenu(
    expanded: Boolean,
    folderName: String,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    EmberrDesktopMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(280.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "New Note in $folderName",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            EmberrTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = "Note title...",
                modifier = Modifier.fillMaxWidth(),
                onSubmit = { if (input.isNotBlank()) onCreate(input.trim()) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EmberrButtonSecondary(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                EmberrButtonPrimary(
                    text = "Create",
                    onClick = { if (input.isNotBlank()) onCreate(input.trim()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TemplatesMenuContent(
    modifier: Modifier = Modifier,
    templates: List<NoteMetadataEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTemplateClick: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onCreateNewTemplate: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EmberrTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Search templates...",
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).padding(
                horizontal = if (isDesktopPlatform) 12.dp else 0.dp,
                vertical = if (isDesktopPlatform) 12.dp else 0.dp,
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isDesktopPlatform) 8.dp else 0.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {(onCreateNewTemplate())}
                .padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.circle_plus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("Create New Template", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        if (templates.isEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) "No templates yet." else "No templates match \"$searchQuery\".",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 14.dp)
            )
        } else {
            templates.forEach { template ->
                TemplateRow(
                    template = template,
                    onClick = { onTemplateClick(template.noteId) },
                    onEdit = { onEditTemplate(template.noteId) },
                    onDelete = { onDeleteTemplate(template.noteId) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TemplateRow(
    template: NoteMetadataEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (isDesktopPlatform) 8.dp else 0.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {(onClick())}
            .padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (!template.icon.isNullOrEmpty()) {
                Text(template.icon, fontSize = 15.sp)
            } else {
                Icon(
                    painter = painterResource(Res.drawable.file_text),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                template.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(Res.drawable.pen),
            contentDescription = "Edit template",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.size(15.dp).noRippleClickable(onEdit)
        )
        Spacer(Modifier.width(14.dp))
        Icon(
            painter = painterResource(Res.drawable.trash),
            contentDescription = "Delete template",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.size(16.dp).noRippleClickable(onDelete)
        )
    }
}

// Mobile shell: same EmberrBottomSheet used by every other mobile menu in this file.
@Composable
fun TemplatesBottomSheet(
    expanded: Boolean,
    templates: List<NoteMetadataEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onTemplateClick: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onCreateNewTemplate: () -> Unit
) {
    EmberrBottomSheet(expanded = expanded, onDismiss = onDismiss, title = "Templates", subtitle = "Start a new note from a template.") { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            TemplatesMenuContent(
                templates = templates,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onTemplateClick = { id -> closeAnd { onTemplateClick(id) } },
                onEditTemplate = { id -> closeAnd { onEditTemplate(id) } },
                onDeleteTemplate = onDeleteTemplate,
                onCreateNewTemplate = { closeAnd(onCreateNewTemplate) }
            )

            EmberrButtonPrimary(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
fun TemplatesDesktopMenu(
    expanded: Boolean,
    templates: List<NoteMetadataEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onTemplateClick: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onCreateNewTemplate: () -> Unit
) {
    EmberrDesktopMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = Modifier.width(300.dp)) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "Templates", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            TemplatesMenuContent(
                templates = templates,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onTemplateClick = { id -> onDismissRequest(); onTemplateClick(id) },
                onEditTemplate = { id -> onDismissRequest(); onEditTemplate(id) },
                onDeleteTemplate = onDeleteTemplate,
                onCreateNewTemplate = { onDismissRequest(); onCreateNewTemplate() },
            )
        }
    }
}

@Composable
fun SortBottomSheet(expanded: Boolean, currentSortType: SortType, currentSortOrder: SortOrder, onDismiss: () -> Unit, onSortChanged: (SortType, SortOrder) -> Unit) {
    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Sort by",
        contentHorizontalPadding = 0.dp
    ) { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            SortOptionItem(
                "Last Edited",
                currentSortType == SortType.LAST_EDITED
            ) { closeAnd { onSortChanged(SortType.LAST_EDITED, currentSortOrder) } }
            SortOptionItem(
                "Date Created",
                currentSortType == SortType.DATE_CREATED
            ) { closeAnd { onSortChanged(SortType.DATE_CREATED, currentSortOrder) } }
            SortOptionItem(
                "Name (A-Z)",
                currentSortType == SortType.NAME
            ) { closeAnd { onSortChanged(SortType.NAME, currentSortOrder) } }
            SortOptionItem(
                "Type",
                currentSortType == SortType.TYPE
            ) { closeAnd { onSortChanged(SortType.TYPE, currentSortOrder) } }
            SortOptionItem("Manual", currentSortType == SortType.MANUAL) {
                closeAnd { onSortChanged(SortType.MANUAL, currentSortOrder) }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            SortOptionItem(
                "Ascending",
                currentSortOrder == SortOrder.ASCENDING
            ) { closeAnd { onSortChanged(currentSortType, SortOrder.ASCENDING) } }
            SortOptionItem(
                "Descending",
                currentSortOrder == SortOrder.DESCENDING
            ) { closeAnd { onSortChanged(currentSortType, SortOrder.DESCENDING) } }

            EmberrButtonPrimary(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun SortOptionItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SelectedOptionBackground else Color.Transparent)
            .noRippleClickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
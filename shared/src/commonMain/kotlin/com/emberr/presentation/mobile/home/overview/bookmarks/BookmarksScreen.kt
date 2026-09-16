package com.emberr.presentation.mobile.home.overview.bookmarks

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.koin.compose.viewmodel.koinViewModel
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.stableStatusBarsPadding
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.customEmberrShadow
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.editor.BlockSelectionPill
import com.emberr.presentation.shared.editor.FocusRequest
import com.emberr.presentation.shared.editor.blockViews.BookmarkBlockView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import emberr.shared.generated.resources.circle_plus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

private val InputContainerShape = RoundedCornerShape(12.dp)
private val SelectionHighlightShape = RoundedCornerShape(12.dp)
private val CategoryPillShape = RoundedCornerShape(20.dp)
private val PillAutoScrollEdgeWidth = 56.dp
private val PillAutoScrollStep = 9.dp
private val PillSettleAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookmarksViewModel = koinViewModel()
) {
    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val groupedBlocks by viewModel.groupedBlocks.collectAsState()

    val selectedBlockIds: Set<String> by viewModel.selectedBlockIds.collectAsState()
    val isSelectionMode = selectedBlockIds.isNotEmpty()
    val clipboardManager = LocalClipboardManager.current
    val localFocusManager = LocalFocusManager.current

    val focusRequest: FocusRequest? by viewModel.focusRequest.collectAsState()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var titleTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var topBarBottomPx by remember { mutableFloatStateOf(0f) }
    var baselineDistancePx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val collapseRangePx = with(density) { 32.dp.toPx() }
    val isAtScrollTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    LaunchedEffect(isAtScrollTop, titleTopPx, topBarBottomPx) {
        if (isAtScrollTop) baselineDistancePx = titleTopPx - topBarBottomPx
    }
    val titleCollapseProgress by remember {
        derivedStateOf {
            val distance = titleTopPx - topBarBottomPx
            val scrolledPx = (baselineDistancePx - distance).coerceAtLeast(0f)
            (scrolledPx / collapseRangePx).coerceIn(0f, 1f)
        }
    }
    val onCollapsedTitleClick: () -> Unit = {
        scope.launch { listState.animateScrollToItem(0) }
    }

    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var activeBlockId by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categoryOrder by viewModel.categoryOrder.collectAsState()

    val availableCategories = remember(groupedBlocks, categoryOrder) {
        availableBookmarkCategories(
            urls = groupedBlocks.flatMap { group -> group.blocks }.map { it.url },
            customOrder = categoryOrder
        )
    }

    val visibleGroups = remember(groupedBlocks, selectedCategory) {
        val activeCategory = selectedCategory ?: return@remember groupedBlocks
        groupedBlocks.mapNotNull { group ->
            val matchingBlocks = group.blocks.filter { bookmarkCategoryOf(it.url) == activeCategory }
            if (matchingBlocks.isEmpty()) null else group.copy(blocks = matchingBlocks)
        }
    }

    LaunchedEffect(availableCategories) {
        if (selectedCategory != null && selectedCategory !in availableCategories) selectedCategory = null
    }

    LaunchedEffect(selectedCategory) {
        listState.scrollToItem(0)
    }

    var showAddUrlInput by remember { mutableStateOf(false) }
    var newUrlInput by remember { mutableStateOf("") }
    val inputFocusRequester = remember { FocusRequester() }

    KmpBackHandler(enabled = showAddUrlInput) {
        showAddUrlInput = false
        newUrlInput = ""
    }

    KmpBackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllBookmarks()
    }

    LaunchedEffect(showAddUrlInput) {
        if (showAddUrlInput) {
            delay(100.milliseconds)
            try { inputFocusRequester.requestFocus() } catch (e: Exception) {}
        } else {
            localFocusManager.clearFocus()
        }
    }

    LaunchedEffect(focusRequest) {
        focusRequest?.let { request ->
            val id = request.id
            activeBlockId = id
            var attempts = 0
            while (focusRequesters[id] == null && attempts < 50) {
                delay(20.milliseconds)
                attempts++
            }
            try { focusRequesters[id]?.requestFocus() } catch (_: Exception) {}
            viewModel.clearFocusRequest()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = if (isDesktopPlatform) 80.dp else 110.dp,
                    bottom = 120.dp
                ),
            ) {
                item {
                    val titleStyle = MaterialTheme.typography.titleLarge.let {
                        it.copy(fontSize = it.fontSize * 1.5f, lineHeight = it.lineHeight * 1.2f)
                    }
                    Text(
                        text = "Bookmarks",
                        style = titleStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(horizontal = if (isDesktopPlatform) 40.dp else 16.dp)
                            .padding(bottom = 8.dp)
                            .onGloballyPositioned { titleTopPx = it.positionInRoot().y }
                    )
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (visibleGroups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No saved links yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    items(visibleGroups, key = { it.monthYear }) { group ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Text(
                                text = group.monthYear,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(horizontal = if (isDesktopPlatform) 40.dp else 16.dp)
                                    .padding(bottom = 12.dp)
                            )
                            BookmarkGrid(
                                blocks = group.blocks,
                                selectedBlockIds = selectedBlockIds,
                                isSelectionMode = isSelectionMode,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }

            EmberrVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 80.dp, bottom = 24.dp)
            )


            BookmarksTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                isSelectionMode = isSelectionMode,
                hazeState = hazeState,
                collapsedTitle = "Bookmarks",
                collapsedTitleProgress = titleCollapseProgress,
                onCollapsedTitleClick = onCollapsedTitleClick,
                onPositioned = { topBarBottomPx = it.positionInRoot().y + it.size.height },
                onBackClick = {
                    if (isSelectionMode) viewModel.clearSelection() else onNavigateBack()
                },
                onAddClick = { showAddUrlInput = true }
            )

            AnimatedVisibility(
                visible = showAddUrlInput,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val defaultBgColor = if (isDesktopPlatform) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
                val defaultContentColor = MaterialTheme.colorScheme.onSurface
                val barSize = if (isDesktopPlatform) 46.dp else 52.dp

                Box(
                    modifier = Modifier
                        .then(if (isDesktopPlatform) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth())
                        .imePadding()
                        .then(if (isDesktopPlatform) Modifier else Modifier.navigationBarsPadding())
                        .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    Surface(
                        shape = InputContainerShape,
                        color = defaultBgColor,
                        contentColor = defaultContentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barSize)
                            .customEmberrShadow(InputContainerShape)
                            .clip(InputContainerShape)
                            .emberrBlur(hazeState, EmberrBlur.Regular)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = "Add Link",
                                modifier = Modifier.size(20.dp),
                                tint = defaultContentColor
                            )
                            Spacer(Modifier.width(12.dp))
                            BasicTextField(
                                value = newUrlInput,
                                onValueChange = { newUrlInput = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = defaultContentColor
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(defaultContentColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(inputFocusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    if (newUrlInput.isNotBlank()) {
                                        viewModel.insertBookmarkWithUrl(newUrlInput.trim())
                                        showAddUrlInput = false
                                        newUrlInput = ""
                                        localFocusManager.clearFocus()
                                    }
                                }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (newUrlInput.isEmpty()) {
                                            Text(
                                                text = "Paste a link...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = defaultContentColor.copy(0.5f)
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                            IconButton(
                                onClick = {
                                    if (newUrlInput.isNotEmpty()) {
                                        newUrlInput = ""
                                    } else {
                                        showAddUrlInput = false
                                        localFocusManager.clearFocus()
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp),
                                    tint = defaultContentColor.copy(0.6f)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = availableCategories.isNotEmpty() && !isSelectionMode && !showAddUrlInput,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (isDesktopPlatform) Modifier else Modifier.navigationBarsPadding())
                    .padding(bottom = 16.dp)
            ) {
                BookmarkCategoryPills(
                    categories = availableCategories,
                    selectedCategory = selectedCategory,
                    hazeState = hazeState,
                    onSelectCategory = { category -> selectedCategory = category },
                    onReorder = { reorderedCategories -> viewModel.saveCategoryOrder(reorderedCategories) }
                )
            }

            BlockSelectionPill(
                isVisible = isSelectionMode,
                selectedCount = selectedBlockIds.size,
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAllBlocks() },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(viewModel.getSelectedText()))
                    viewModel.clearSelection()
                },
                onCut = {
                    clipboardManager.setText(AnnotatedString(viewModel.cutSelectedBlocks()))
                },
                onAddBlockAbove = {},
                onAddBlockBelow = {},
                onDelete = { viewModel.deleteSelectedBlocks() },
                onTogglePin = {},
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .then(if (isDesktopPlatform) Modifier.padding(bottom = 16.dp) else Modifier.navigationBarsPadding())
            )
        }
    }
}

@Composable
fun BookmarkGrid(
    blocks: List<BookmarkBlock>,
    selectedBlockIds: Set<String>,
    isSelectionMode: Boolean,
    viewModel: BookmarksViewModel
) {
    val columns = if (isDesktopPlatform) 3 else 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = if (isDesktopPlatform) 40.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val chunkedBlocks = blocks.chunked(columns)

        chunkedBlocks.forEach { rowBlocks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowBlocks.forEach { block ->
                    val isSelected = selectedBlockIds.contains(block.id)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        BookmarkBlockView(
                            block = block,
                            inSelectionMode = isSelectionMode,
                            onToggleSelection = { viewModel.toggleSelection(block.id) },
                            onUrlSubmit = { url -> viewModel.handleUrlSubmit(block.id, url) }
                        )

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .border(3.dp, MaterialTheme.colorScheme.primary, SelectionHighlightShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            )
                        }
                    }
                }

                val emptySpaces = columns - rowBlocks.size
                repeat(emptySpaces) {
                    Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.background))
                }
            }
        }
    }
}

@Composable
private fun BookmarksTopBar(
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean,
    hazeState: HazeState? = null,
    collapsedTitle: String = "",
    collapsedTitleProgress: Float = 0f,
    onCollapsedTitleClick: () -> Unit = {},
    onPositioned: (LayoutCoordinates) -> Unit = {},
    onBackClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val defaultContentColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            .padding(top = if (isDesktopPlatform) 16.dp else 10.dp, start = 16.dp, end = 16.dp)
            .onGloballyPositioned(onPositioned),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopBarIconButton(
            icon = painterResource(Res.drawable.chevron_left),
            contentDescription = "Back",
            bgColor = Color.Transparent,
            tint = MaterialTheme.colorScheme.primary,
            hazeState = hazeState,
            hazeStyle = EmberrBlur.Regular,
            onClick = onBackClick
        )

        if (collapsedTitleProgress > 0f) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer { alpha = collapsedTitleProgress }
                    .clickable(onClick = onCollapsedTitleClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = collapsedTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = defaultContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (!isSelectionMode) {
            TopBarIconButton(
                icon = painterResource(Res.drawable.circle_plus),
                contentDescription = "Add Bookmark",
                bgColor = Color.Transparent,
                tint = MaterialTheme.colorScheme.primary,
                hazeState = hazeState,
                hazeStyle = EmberrBlur.Regular,
                onClick = onAddClick
            )
        } else {
            Spacer(Modifier.size(1.dp))
        }
    }
}

@Composable
private fun BookmarkCategoryPills(
    categories: List<String>,
    selectedCategory: String?,
    hazeState: HazeState,
    onSelectCategory: (String?) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val orderedCategories = remember(categories) { mutableStateListOf(*categories.toTypedArray()) }
    val pillSlotBounds = remember(categories) { mutableStateMapOf<String, Rect>() }
    val scrollState = rememberScrollState()
    val dragScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var draggedCategory by remember { mutableStateOf<String?>(null) }
    var releasingCategory by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragPointerX by remember { mutableFloatStateOf(0f) }
    var rowViewportBounds by remember { mutableStateOf(Rect.Zero) }
    val liftedTranslationX = remember { Animatable(0f) }

    val autoScrollEdgeWidthPx = with(density) { PillAutoScrollEdgeWidth.toPx() }
    val autoScrollStepPx = with(density) { PillAutoScrollStep.toPx() }

    val settlePill: (String) -> Unit = { category ->
        releasingCategory = category
        dragStartIndex = -1
        dragScope.launch {
            liftedTranslationX.animateTo(0f, PillSettleAnimationSpec)
            releasingCategory = null
        }
    }

    LaunchedEffect(draggedCategory) {
        val category = draggedCategory ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }

            val autoScrollStep = when {
                dragPointerX > rowViewportBounds.right - autoScrollEdgeWidthPx -> autoScrollStepPx
                dragPointerX < rowViewportBounds.left + autoScrollEdgeWidthPx -> -autoScrollStepPx
                else -> 0f
            }
            if (autoScrollStep != 0f) scrollState.scrollBy(autoScrollStep)

            val hoveredSlot = pillSlotBounds.entries.firstOrNull { (name, bounds) ->
                name != category && dragPointerX in bounds.left..bounds.right
            }
            if (hoveredSlot != null) {
                val fromIndex = orderedCategories.indexOf(category)
                val toIndex = orderedCategories.indexOf(hoveredSlot.key)
                if (fromIndex != -1 && toIndex != -1) {
                    val hoveredCenterX = hoveredSlot.value.center.x
                    val hasPassedHoveredCenter =
                        if (toIndex > fromIndex) dragPointerX > hoveredCenterX
                        else dragPointerX < hoveredCenterX
                    if (hasPassedHoveredCenter) {
                        orderedCategories.add(toIndex, orderedCategories.removeAt(fromIndex))
                    }
                }
            }

            val slotCenterX = pillSlotBounds[category]?.center?.x
            if (slotCenterX != null) liftedTranslationX.snapTo(dragPointerX - slotCenterX)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowViewportBounds = it.boundsInWindow() }
            .horizontalScroll(scrollState)
            .padding(horizontal = if (isDesktopPlatform) 40.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookmarkCategoryPill(
            label = "All",
            isSelected = selectedCategory == null,
            isLifted = false,
            hazeState = hazeState,
            translationProvider = { 0f },
            modifier = Modifier.clickable { onSelectCategory(null) }
        )

        orderedCategories.forEach { category ->
            key(category) {
                val isLifted = draggedCategory == category || releasingCategory == category
                val settleTranslationX = remember { Animatable(0f) }
                var slotXInRow by remember { mutableFloatStateOf(Float.NaN) }

                Box(
                    modifier = Modifier
                        .zIndex(if (isLifted) 1f else 0f)
                        .onGloballyPositioned { coordinates ->
                            pillSlotBounds[category] = coordinates.boundsInWindow()

                            val newSlotXInRow = coordinates.positionInRoot().x + scrollState.value
                            if (!slotXInRow.isNaN() && newSlotXInRow != slotXInRow && !isLifted) {
                                val shiftFromPreviousSlot = slotXInRow - newSlotXInRow
                                dragScope.launch {
                                    settleTranslationX.snapTo(shiftFromPreviousSlot)
                                    settleTranslationX.animateTo(0f, PillSettleAnimationSpec)
                                }
                            }
                            slotXInRow = newSlotXInRow
                        }
                ) {
                    BookmarkCategoryPill(
                        label = category,
                        isSelected = selectedCategory == category,
                        isLifted = isLifted,
                        hazeState = hazeState,
                        translationProvider = {
                            if (isLifted) liftedTranslationX.value else settleTranslationX.value
                        },
                        modifier = Modifier
                            .clickable {
                                onSelectCategory(if (selectedCategory == category) null else category)
                            }
                            .pointerInput(category) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        releasingCategory = null
                                        dragStartIndex = orderedCategories.indexOf(category)
                                        dragPointerX = pillSlotBounds[category]?.center?.x ?: 0f
                                        draggedCategory = category
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragPointerX += dragAmount.x
                                    },
                                    onDragEnd = {
                                        val finalIndex = orderedCategories.indexOf(category)
                                        val startIndex = dragStartIndex
                                        draggedCategory = null
                                        settlePill(category)
                                        if (startIndex != -1 && finalIndex != -1 && startIndex != finalIndex) {
                                            onReorder(orderedCategories.toList())
                                        }
                                    },
                                    onDragCancel = {
                                        draggedCategory = null
                                        settlePill(category)
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkCategoryPill(
    label: String,
    isSelected: Boolean,
    isLifted: Boolean,
    hazeState: HazeState,
    translationProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val pillColorAnimationSpec = tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = pillColorAnimationSpec,
        label = "BookmarkCategoryPillBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = pillColorAnimationSpec,
        label = "BookmarkCategoryPillContent"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        animationSpec = pillColorAnimationSpec,
        label = "BookmarkCategoryPillBorder"
    )
    val liftScale by animateFloatAsState(
        targetValue = if (isLifted) 1.08f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "BookmarkCategoryPillScale"
    )
    val liftShadowAlpha by animateFloatAsState(
        targetValue = if (isLifted) 0.85f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "BookmarkCategoryPillAlpha"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = translationProvider()
                scaleX = liftScale
                scaleY = liftScale
                alpha = liftShadowAlpha
            }
            .clip(CategoryPillShape)
            .emberrBlur(hazeState, EmberrBlur.Regular)
            .background(backgroundColor)
            .border(width = 0.5.dp, color = borderColor, shape = CategoryPillShape)
            .then(modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1
        )
    }
}

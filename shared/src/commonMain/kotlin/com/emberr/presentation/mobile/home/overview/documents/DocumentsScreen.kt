package com.emberr.presentation.mobile.home.overview.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.stableStatusBarsPadding
import com.emberr.presentation.shared.editor.BlockSelectionPill
import androidx.compose.ui.text.AnnotatedString
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.editor.blockViews.DocumentBlockView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import emberr.shared.generated.resources.circle_plus
import org.jetbrains.compose.resources.painterResource

private val SelectionHighlightShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onNavigateBack: () -> Unit,
    onTriggerDocumentPicker: () -> Unit,
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    viewModel: DocumentsViewModel = koinViewModel()
) {
    val clipboardManager = LocalClipboardManager.current

    val isLoading by viewModel.isLoading.collectAsState()
    val groupedBlocks by viewModel.groupedBlocks.collectAsState()

    val selectedBlockIds by viewModel.selectedBlockIds.collectAsState()
    val isSelectionMode = selectedBlockIds.isNotEmpty()
    val focusRequest by viewModel.focusRequest.collectAsState()

    KmpBackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

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

    LaunchedEffect(Unit) {
        viewModel.loadAllDocuments()
    }

    LaunchedEffect(focusRequest) {
        focusRequest?.let {
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
                        text = "Documents",
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
                } else if (groupedBlocks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No documents attached yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    items(groupedBlocks, key = { it.monthYear }) { group ->
                        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                            Text(
                                text = group.monthYear,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(horizontal = if (isDesktopPlatform) 40.dp else 16.dp)
                                    .padding(bottom = 12.dp)
                            )

                            DocumentGrid(
                                blocks = group.blocks,
                                selectedBlockIds = selectedBlockIds,
                                isSelectionMode = isSelectionMode,
                                viewModel = viewModel,
                                onOpenFile = onOpenFile
                            )
                        }
                    }
                }
            }

            EmberrVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 80.dp, bottom = 24.dp)
            )


            DocumentsTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                isSelectionMode = isSelectionMode,
                hazeState = hazeState,
                collapsedTitle = "Documents",
                collapsedTitleProgress = titleCollapseProgress,
                onCollapsedTitleClick = onCollapsedTitleClick,
                onPositioned = { topBarBottomPx = it.positionInRoot().y + it.size.height },
                onBackClick = {
                    if (isSelectionMode) viewModel.clearSelection() else onNavigateBack()
                },
                onAddClick = onTriggerDocumentPicker
            )

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
fun DocumentGrid(
    blocks: List<DocumentBlock>,
    selectedBlockIds: Set<String>,
    isSelectionMode: Boolean,
    viewModel: DocumentsViewModel,
    onOpenFile: (filePath: String, mimeType: String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = if (isDesktopPlatform) 40.dp else 16.dp)
    ) {
        val minItemWidth = if (isDesktopPlatform) 280f else 150f
        val spacing = 12f
        val columns = maxOf(2, ((maxWidth.value + spacing) / (minItemWidth + spacing)).toInt())

        Column(
            modifier = Modifier.fillMaxWidth(),
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
                            DocumentBlockView(
                                block = block,
                                inSelectionMode = isSelectionMode,
                                onToggleSelection = { viewModel.toggleSelection(block.id) },
                                onRequestPicker = {},
                                onOpenFile = onOpenFile
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
}

@Composable
private fun DocumentsTopBar(
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
                contentDescription = "Add Document",
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
package com.emberr.presentation.mobile.home.overview.images

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.editor.BlockSelectionPill
import com.emberr.presentation.shared.editor.blockViews.ImageBlockView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.circle_plus
import org.jetbrains.compose.resources.painterResource

private val SelectionHighlightShape = RoundedCornerShape(12.dp)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesScreen(
    onNavigateBack: () -> Unit,
    onTriggerImagePicker: () -> Unit,
    viewModel: ImagesViewModel = koinViewModel()
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
        viewModel.loadAllImages()
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
                )
            ) {
                item {
                    val titleStyle = MaterialTheme.typography.titleLarge.let {
                        it.copy(fontSize = it.fontSize * 1.5f, lineHeight = it.lineHeight * 1.2f)
                    }
                    Text(
                        text = "Images",
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
                                "No images saved yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    items(groupedBlocks, key = { it.monthYear }) { group ->
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .padding(bottom = 36.dp)
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

                            ImageGrid(
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


            EmberrTopHeaderBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = "Images",
                titleVisibility = titleCollapseProgress,
                onTitleClick = onCollapsedTitleClick,
                hazeState = hazeState,
                onPositioned = { topBarBottomPx = it.positionInRoot().y + it.size.height },
                onBackClick = {
                    if (isSelectionMode) viewModel.clearSelection() else onNavigateBack()
                },
                actions = {
                    if (!isSelectionMode) {
                        TopBarIconButton(
                            icon = painterResource(Res.drawable.circle_plus),
                            contentDescription = "Add Image",
                            bgColor = Color.Transparent,
                            tint = MaterialTheme.colorScheme.primary,
                            hazeState = hazeState,
                            hazeStyle = EmberrBlur.Regular,
                            onClick = onTriggerImagePicker
                        )
                    }
                }
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
fun ImageGrid(
    blocks: List<ImageBlock>,
    selectedBlockIds: Set<String>,
    isSelectionMode: Boolean,
    viewModel: ImagesViewModel
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = if (isDesktopPlatform) 40.dp else 16.dp)
    ) {
        val minItemWidth = 120f
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
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            ImageBlockView(
                                block = block,
                                inSelectionMode = isSelectionMode,
                                onToggleSelection = { viewModel.toggleSelection(block.id) },
                                onRequestPicker = {},
                                onRequestCamera = {},
                                onDelete = { viewModel.deleteImageBlock(block.id) }
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }
                }
            }
        }
    }
}

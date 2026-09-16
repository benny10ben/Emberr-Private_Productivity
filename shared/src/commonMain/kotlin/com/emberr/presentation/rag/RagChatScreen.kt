package com.emberr.presentation.rag

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.eventbus.AiEventBus
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.rag.chat.ChatBubble
import com.emberr.presentation.rag.chat.ChatEmptyState
import com.emberr.presentation.rag.chat.ChatInputBar
import com.emberr.presentation.rag.chat.ModelUnavailablePrompt
import com.emberr.presentation.rag.chat.ThinkingIndicator
import com.emberr.presentation.rag.chat.VaultAccessPill
import com.emberr.presentation.rag.history.ChatHistoryMenuContent
import com.emberr.presentation.rag.history.ChatHistorySheet
import com.emberr.presentation.rag.settings.AiSettingsSheet
import com.emberr.presentation.rag.settings.ExternalAiSettingsSheet
import com.emberr.presentation.rag.settings.FineTuningSheet
import com.emberr.presentation.rag.settings.LocalAiSettingsSheet
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.rememberStableStatusBarsPadding
import com.emberr.presentation.shared.stableStatusBarsPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import org.jetbrains.compose.resources.painterResource

internal val DesktopPanelTopInset = 12.dp
internal val DesktopPanelContentInset = 13.dp
private val TopBarButtonSize = 44.dp
private val VaultAccessPillGap = 4.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RagChatScreen(
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        RagChatContent(
            viewModel = viewModel,
            onDismiss = onDismiss,
            isVisible = true,
            sharedTransitionScope = sharedTransitionScope,
            chatAnimatedVisibilityScope = animatedContentScope,
            onPickDocument = onPickDocument,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RagChatPanel(
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    modifier: Modifier = Modifier,
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        RagChatContent(
            viewModel = viewModel,
            onDismiss = onDismiss,
            isVisible = true,
            onPickDocument = onPickDocument,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RagChatContent(
    modifier: Modifier,
    viewModel: RagViewModel,
    onDismiss: () -> Unit,
    isVisible: Boolean,
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    chatAnimatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isModelAvailable by viewModel.isModelAvailable.collectAsState()
    val embeddingSetupState by viewModel.embeddingSetupState.collectAsState()
    val aiGenerationMode by viewModel.aiGenerationMode.collectAsState()
    val externalAiReadOnly by viewModel.externalAiReadOnly.collectAsState()
    val localGeneratorDownloadProgress by viewModel.localGeneratorDownloadProgress.collectAsState()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    var userScrolledAwayFromBottom by remember { mutableStateOf(false) }

    val chatListNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    userScrolledAwayFromBottom = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !listState.canScrollForward) {
                    userScrolledAwayFromBottom = false
                }
                return Offset.Zero
            }
        }
    }

    var inputText by remember { mutableStateOf("") }
    var showAiSettingsSheet by remember { mutableStateOf(false) }
    var showLocalAiSheet by remember { mutableStateOf(false) }
    var showExternalAiSheet by remember { mutableStateOf(false) }
    var showFineTuningSheet by remember { mutableStateOf(false) }
    var showChatHistorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            viewModel.refreshAiAvailability()
            AiEventBus.requestImmediateIndex()
        } else {
            inputText = ""
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            userScrolledAwayFromBottom = false
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(messages.lastOrNull()?.text) {
        if (messages.isNotEmpty() && isLoading && !userScrolledAwayFromBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val submit: () -> Unit = {
        val trimmed = inputText.trim()
        if (trimmed.isNotEmpty() && !isLoading) {
            viewModel.submitQuery(trimmed)
            inputText = ""
        }
    }

    val sidePadding = if (isDesktopPlatform) 32.dp else 16.dp

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                embeddingSetupState != EmbeddingSetupState.Ready -> {
                    EmbeddingSetupScreen(
                        state = embeddingSetupState,
                        sidePadding = sidePadding,
                        isResumable = viewModel.hasResumableEmbeddingDownload(),
                        onDownloadClick = viewModel::downloadEmbeddingModel,
                        onPauseClick = viewModel::pauseEmbeddingModelDownload,
                        onProceedClick = viewModel::proceedAfterEmbeddingModelDownload
                    )
                }

                isModelAvailable == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                isModelAvailable == false -> {
                    ModelUnavailablePrompt(
                        sidePadding = sidePadding,
                        downloadProgress = localGeneratorDownloadProgress,
                        localAiUnsupportedReason = viewModel.localAiUnsupportedReason,
                        onDownloadClick = { showLocalAiSheet = true },
                        onApiKeyClick = { showExternalAiSheet = true }
                    )
                }

                messages.isEmpty() -> {
                    ChatEmptyState(
                        sidePadding = sidePadding,
                        onSuggestionTap = { suggestion ->
                            inputText = suggestion
                            submit()
                        }
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(chatListNestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = sidePadding,
                            end = sidePadding,
                            top = rememberStableStatusBarsPadding().calculateTopPadding() + 100.dp,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = messages, key = { it.id }) { message ->
                            ChatBubble(
                                message = message,
                                onEditClick = if (message.isUser) {
                                    {
                                        viewModel.beginEditingMessage(message.id)
                                        inputText = message.text
                                    }
                                } else null,
                                onConfirmPendingWrite = { viewModel.confirmPendingWrite(message.id) },
                                onRejectPendingWrite = { viewModel.rejectPendingWrite(message.id) }
                            )
                        }
                        if (isLoading && messages.lastOrNull()?.text?.isEmpty() == true) {
                            item { ThinkingIndicator() }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
                .padding(
                    start = if (isDesktopPlatform) DesktopPanelContentInset else 16.dp,
                    end = if (isDesktopPlatform) DesktopPanelContentInset else 16.dp,
                    top = if (isDesktopPlatform) DesktopPanelTopInset else 10.dp,
                    bottom = 8.dp
                )
        ) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                TopBarIconButton(
                    icon = painterResource(Res.drawable.chevron_left),
                    contentDescription = "Back",
                    bgColor = Color.Transparent,
                    tint = MaterialTheme.colorScheme.primary,
                    hazeState = hazeState,
                    hazeStyle = EmberrBlur.Regular,
                    onClick = onDismiss
                )
            }

            if (embeddingSetupState == EmbeddingSetupState.Ready) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ask Emberr",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (embeddingSetupState == EmbeddingSetupState.Ready) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    TopBarIconButton(
                        icon = rememberVectorPainter(Icons.Default.Menu),
                        contentDescription = "Chat history",
                        onClick = { showChatHistorySheet = true },
                        bgColor = Color.Transparent,
                        tint = MaterialTheme.colorScheme.primary,
                        hazeState = hazeState,
                        hazeStyle = EmberrBlur.Regular,
                    )

                    if (isDesktopPlatform) {
                        EmberrDesktopMenu(
                            expanded = showChatHistorySheet,
                            onDismissRequest = { showChatHistorySheet = false }
                        ) {
                            Column(modifier = Modifier.width(320.dp).padding(vertical = 8.dp)) {
                                ChatHistoryMenuContent(
                                    viewModel = viewModel,
                                    closeAnd = { action -> action(); showChatHistorySheet = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (embeddingSetupState == EmbeddingSetupState.Ready) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
                    .padding(
                        top = (if (isDesktopPlatform) DesktopPanelTopInset else 10.dp) +
                            TopBarButtonSize + 8.dp + VaultAccessPillGap
                    ),
                contentAlignment = Alignment.Center
            ) {
                VaultAccessPill(aiGenerationMode = aiGenerationMode, externalAiReadOnly = externalAiReadOnly)
            }
        }

        if (embeddingSetupState == EmbeddingSetupState.Ready) {
            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSubmit = submit,
                enabled = !isLoading && isModelAvailable == true,
                isGenerating = isLoading,
                onStopGeneration = viewModel::stopGeneration,
                hazeState = hazeState,
                viewModel = viewModel,
                sharedTransitionScope = sharedTransitionScope,
                chatAnimatedVisibilityScope = chatAnimatedVisibilityScope,
                onSettingsClick = { showAiSettingsSheet = true },
                showAiSettingsMenu = showAiSettingsSheet,
                onAiSettingsMenuDismiss = { showAiSettingsSheet = false },
                onLocalAiClick = { showLocalAiSheet = true },
                onExternalAiClick = { showExternalAiSheet = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (!isDesktopPlatform) {
        AiSettingsSheet(
            expanded = showAiSettingsSheet,
            onDismiss = { showAiSettingsSheet = false },
            viewModel = viewModel,
            onLocalAiClick = { showLocalAiSheet = true },
            onExternalAiClick = { showExternalAiSheet = true },
            onFineTuningClick = { showFineTuningSheet = true }
        )
    }

    LocalAiSettingsSheet(
        expanded = showLocalAiSheet,
        onDismiss = { showLocalAiSheet = false },
        viewModel = viewModel,
        onPickDocument = onPickDocument
    )

    ExternalAiSettingsSheet(
        expanded = showExternalAiSheet,
        onDismiss = { showExternalAiSheet = false },
        viewModel = viewModel
    )

    FineTuningSheet(
        expanded = showFineTuningSheet,
        onDismiss = { showFineTuningSheet = false },
        viewModel = viewModel
    )

    if (!isDesktopPlatform) {
        ChatHistorySheet(
            expanded = showChatHistorySheet,
            onDismiss = { showChatHistorySheet = false },
            viewModel = viewModel
        )
    }
}

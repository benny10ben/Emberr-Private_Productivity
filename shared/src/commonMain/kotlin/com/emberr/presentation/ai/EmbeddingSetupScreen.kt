package com.emberr.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emberr.presentation.onboarding.OnboardingChatMock
import com.emberr.presentation.onboarding.LocalOnboardingWideLayout
import com.emberr.presentation.onboarding.OnboardingFlowBottomBar
import com.emberr.presentation.onboarding.OnboardingFlowScaffold
import com.emberr.presentation.onboarding.OnboardingPanelTransition
import com.emberr.presentation.onboarding.OnboardingStep
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.astroid
import emberr.shared.generated.resources.download
import emberr.shared.generated.resources.logo_chatgpt
import emberr.shared.generated.resources.logo_claude
import emberr.shared.generated.resources.logo_gemini
import emberr.shared.generated.resources.logo_grok
import emberr.shared.generated.resources.logo_llama
import emberr.shared.generated.resources.logo_mistral
import emberr.shared.generated.resources.search
import emberr.shared.generated.resources.shield_alert
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private enum class ModelProvider(
    val label: String,
    val logo: DrawableResource,
    val isMonochromeMark: Boolean
) {
    Claude("Claude", Res.drawable.logo_claude, isMonochromeMark = false),
    ChatGpt("ChatGPT", Res.drawable.logo_chatgpt, isMonochromeMark = true),
    Gemini("Gemini", Res.drawable.logo_gemini, isMonochromeMark = false),
    Grok("Grok", Res.drawable.logo_grok, isMonochromeMark = true),
    Llama("Llama", Res.drawable.logo_llama, isMonochromeMark = true),
    Mistral("Mistral", Res.drawable.logo_mistral, isMonochromeMark = false)
}

private const val ProvidersPerRow = 3
private val ProviderBadgeSize = 48.dp
private val ProviderLogoSize = 24.dp
private val ProviderBadgeGap = 16.dp
private const val ModelsStepIndex = 2
private const val DownloadStepIndex = 3

private enum class SetupPanel { None, Providers, Download }

@Composable
internal fun EmbeddingSetupScreen(
    state: EmbeddingSetupState,
    isResumable: Boolean,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onProceedClick: () -> Unit
) {
    val statements = embeddingSetupStatements(state = state, isResumable = isResumable)

    var currentStepIndex by remember {
        mutableIntStateOf(
            if (state == EmbeddingSetupState.Required && !isResumable) 0 else DownloadStepIndex
        )
    }

    LaunchedEffect(state) {
        if (state is EmbeddingSetupState.Downloading ||
            state is EmbeddingSetupState.Indexing ||
            state == EmbeddingSetupState.DownloadComplete
        ) {
            currentStepIndex = DownloadStepIndex
        }
    }

    val canGoBack = when (currentStepIndex) {
        0 -> false
        DownloadStepIndex -> state.allowsReturningToModels()
        else -> true
    }

    fun goToPreviousStep() {
        if (canGoBack) currentStepIndex--
    }

    fun onPrimaryClick() {
        if (currentStepIndex < DownloadStepIndex) {
            currentStepIndex++
            return
        }

        when (state) {
            EmbeddingSetupState.Required -> onDownloadClick()
            is EmbeddingSetupState.Downloading -> onPauseClick()
            is EmbeddingSetupState.DownloadFailed -> onDownloadClick()
            EmbeddingSetupState.DownloadComplete -> onProceedClick()
            else -> Unit
        }
    }

    OnboardingFlowScaffold(
        statements = statements,
        currentStepIndex = currentStepIndex,
        showSkip = false,
        onSkip = {},
        onSwipeForward = null,
        onSwipeBackward = null,
        panel = {
            val visiblePanel = when (currentStepIndex) {
                ModelsStepIndex -> SetupPanel.Providers
                DownloadStepIndex -> SetupPanel.Download
                else -> SetupPanel.None
            }

            OnboardingPanelTransition(
                targetState = visiblePanel,
                modifier = Modifier.fillMaxWidth()
            ) { panelToShow ->
                when (panelToShow) {
                    SetupPanel.None -> Spacer(modifier = Modifier.fillMaxWidth())
                    SetupPanel.Providers -> ProviderBadgeGrid()
                    SetupPanel.Download -> DownloadPanel(state = state)
                }
            }
        },
        bottomBar = {
            if (state is EmbeddingSetupState.Indexing) {
                IndexingProgress(state = state)
            } else {
                OnboardingFlowBottomBar(
                    label = primaryActionLabel(currentStepIndex, state, isResumable),
                    showBack = canGoBack,
                    onBack = ::goToPreviousStep,
                    onNext = ::onPrimaryClick
                )
            }
        }
    )
}

@Composable
private fun embeddingSetupStatements(
    state: EmbeddingSetupState,
    isResumable: Boolean
): List<OnboardingStep> = listOf(
    OnboardingStep(
        icon = Res.drawable.shield_alert,
        lead = "Ask your notes anything.",
        headline = "Answered on this device.",
        detail = "Your question and the notes it reads never leave this machine."
    ),
    OnboardingStep(
        icon = Res.drawable.search,
        lead = "Your notes become",
        headline = "a private index.",
        detail = "Stored on your own disk. No analytics, no query history, nothing logged."
    ),
    OnboardingStep(
        icon = Res.drawable.astroid,
        lead = "Local first, cloud optional.",
        headline = "Bring your own key.",
        detail = "A local model is included and needs no account. Add a provider later in Settings."
    ),
    OnboardingStep(
        icon = Res.drawable.download,
        lead = "One file to fetch.",
        headline = state.downloadHeadline(isResumable),
        detail = (state as? EmbeddingSetupState.DownloadFailed)?.message
    )
)

@Composable
private fun ProviderBadgeGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ProviderBadgeGap)
    ) {
        ModelProvider.entries.chunked(ProvidersPerRow).forEach { rowProviders ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (LocalOnboardingWideLayout.current) {
                    Arrangement.spacedBy(ProviderBadgeGap, Alignment.CenterHorizontally)
                } else {
                    Arrangement.SpaceBetween
                }
            ) {
                rowProviders.forEach { provider ->
                    ProviderBadge(provider = provider)
                }
            }
        }
    }
}

@Composable
private fun ProviderBadge(provider: ModelProvider) {
    Box(
        modifier = Modifier
            .size(ProviderBadgeSize)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(provider.logo),
            contentDescription = provider.label,
            modifier = Modifier.size(ProviderLogoSize),
            contentScale = ContentScale.Fit,
            colorFilter = if (provider.isMonochromeMark) {
                ColorFilter.tint(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
            } else {
                null
            }
        )
    }
}

@Composable
private fun DownloadPanel(state: EmbeddingSetupState) {
    val progress = state.progressFraction()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        OnboardingChatMock(
            question = "What did I write about my research last month?",
            answer = "You outlined three experiments and flagged the second one as blocked.",
            placeholder = "Ask about your notes"
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Embedding model",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            if (progress != null) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }

        AnimatedVisibility(
            visible = progress != null,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(140))
        ) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))

                ProgressTrack(progress = progress ?: 0f, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        SetupStepRow(
            label = "Model downloaded",
            isDone = state == EmbeddingSetupState.DownloadComplete ||
                state is EmbeddingSetupState.Indexing,
            isActive = state is EmbeddingSetupState.Downloading,
            isFailed = state is EmbeddingSetupState.DownloadFailed
        )

        Spacer(modifier = Modifier.height(12.dp))

        SetupStepRow(
            label = "Notes indexed",
            isDone = false,
            isActive = state is EmbeddingSetupState.Indexing,
            isFailed = false
        )
    }
}

@Composable
private fun IndexingProgress(state: EmbeddingSetupState.Indexing) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Indexing notes",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (state.total > 0) "${state.completed} of ${state.total}" else "Starting",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        ProgressTrack(
            progress = state.progressFraction() ?: 0f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    val filled by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessLow),
        label = "embedding-progress-fill"
    )

    Box(
        modifier = modifier
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        if (filled > 0.004f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(filled)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground)
            )
        }
    }
}

@Composable
private fun SetupStepRow(
    label: String,
    isDone: Boolean,
    isActive: Boolean,
    isFailed: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (isDone || isActive) 8.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDone || isActive -> MaterialTheme.colorScheme.onBackground
                            isFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        }
                    )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            color = if (isDone || isActive) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            }
        )
    }
}

private fun EmbeddingSetupState.progressFraction(): Float? = when (this) {
    is EmbeddingSetupState.Downloading -> progress.coerceIn(0f, 1f)
    is EmbeddingSetupState.Indexing ->
        if (total > 0) (completed / total.toFloat()).coerceIn(0f, 1f) else 0f

    else -> null
}

private fun EmbeddingSetupState.allowsReturningToModels(): Boolean =
    this == EmbeddingSetupState.Required || this is EmbeddingSetupState.DownloadFailed

private fun EmbeddingSetupState.downloadHeadline(isResumable: Boolean): String = when (this) {
    EmbeddingSetupState.Required ->
        if (isResumable) "Finish the download." else "Add the on-device model."

    is EmbeddingSetupState.Downloading -> "Getting the model ready."
    is EmbeddingSetupState.DownloadFailed -> "The download stopped."
    EmbeddingSetupState.DownloadComplete -> "The model is ready."
    is EmbeddingSetupState.Indexing -> "Reading through your notes."
    else -> "Add the on-device model."
}

private fun primaryActionLabel(
    currentStepIndex: Int,
    state: EmbeddingSetupState,
    isResumable: Boolean
): String = if (currentStepIndex < DownloadStepIndex) "Continue" else when (state) {
    EmbeddingSetupState.Required -> if (isResumable) "Resume download" else "Download model"
    is EmbeddingSetupState.Downloading -> "Pause download"
    is EmbeddingSetupState.DownloadFailed -> if (isResumable) "Resume download" else "Try again"
    EmbeddingSetupState.DownloadComplete -> "Index my notes"
    else -> "Continue"
}

package com.emberr.presentation.shared.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.util.network.HtmlMetadataFetcher
import com.emberr.domain.util.network.UrlMetadata
import com.emberr.presentation.shared.components.EmberrShadowElevation
import com.emberr.presentation.shared.editor.components.DesktopCursor
import com.emberr.presentation.shared.editor.components.desktopPointerCursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val PREVIEW_FAILURE_DESCRIPTION = "Could not load preview"
private const val MOST_PREVIEWS_KEPT_IN_MEMORY = 200

private val TimeRestingOnALinkBeforeTheCardAppears = 1000.milliseconds
private val TimeToReachTheCard = 260.milliseconds
private val CardFadeOutDuration = 130
private val CardFadeInDuration = 140

@Immutable
data class WebLinkPreview(val title: String?, val description: String?)

private object WebLinkPreviewCache {
    private val loadedPreviews = mutableStateMapOf<String, WebLinkPreview>()
    private val urlsAlreadyRequested = mutableSetOf<String>()
    private val loadingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun previewFor(url: String): WebLinkPreview? = loadedPreviews[url]

    fun startLoading(url: String) {
        if (loadedPreviews.size > MOST_PREVIEWS_KEPT_IN_MEMORY) {
            loadedPreviews.clear()
            urlsAlreadyRequested.clear()
        }
        if (!urlsAlreadyRequested.add(url)) return

        loadingScope.launch {
            val metadata = try {
                HtmlMetadataFetcher.fetchMetadata(toOpenableWebLink(url))
            } catch (_: Exception) {
                null
            }
            loadedPreviews[url] = metadata.toWebLinkPreview(url)
        }
    }
}

private fun UrlMetadata?.toWebLinkPreview(url: String): WebLinkPreview {
    if (this == null) return WebLinkPreview(null, null)

    val openableUrl = toOpenableWebLink(url)
    val readableTitle = title?.trim()?.takeIf { it.isNotEmpty() && it != url && it != openableUrl }
    val readableDescription = description?.trim()
        ?.takeIf { it.isNotEmpty() && it != PREVIEW_FAILURE_DESCRIPTION }

    return WebLinkPreview(readableTitle, readableDescription)
}

@Composable
private fun webPreviewFor(url: String): WebLinkPreview? {
    LaunchedEffect(url) { WebLinkPreviewCache.startLoading(url) }
    return WebLinkPreviewCache.previewFor(url)
}

private fun lastEditedLabel(updatedAt: Long): String {
    val editedOn = Instant.fromEpochMilliseconds(updatedAt)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val month = editedOn.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "Edited $month ${editedOn.day}"
}

@Composable
fun LinkHoverCard(
    hoverState: LinkHoverState,
    onOpenNoteLink: (String) -> Unit = {},
    findNote: (String) -> NoteMetadataEntity? = { null }
) {
    val hoveredLink = hoverState.hoveredLink.takeIf { it is HoveredLink.Web || it is HoveredLink.Note }
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isPointerOverCard by cardInteractionSource.collectIsHoveredAsState()

    var anchoredLink by remember { mutableStateOf<HoveredLink?>(null) }
    var isCardVisible by remember { mutableStateOf(false) }

    LaunchedEffect(hoveredLink, isPointerOverCard) {
        when {
            hoveredLink != null -> {
                if (anchoredLink == null) delay(TimeRestingOnALinkBeforeTheCardAppears)
                anchoredLink = hoveredLink
                isCardVisible = true
            }
            isPointerOverCard -> isCardVisible = true
            else -> {
                delay(TimeToReachTheCard)
                isCardVisible = false
                delay(CardFadeOutDuration.milliseconds)
                anchoredLink = null
            }
        }
    }

    val link = anchoredLink ?: return
    val webLinkActions = rememberWebLinkActions()
    val density = LocalDensity.current
    val gapAboveLinkPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(gapAboveLinkPx) { CardAboveLinkPositionProvider(gapAboveLinkPx) }

    val cardVisibility = remember(link) { MutableTransitionState(false) }
    cardVisibility.targetState = isCardVisible

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { link.anchor.left.toDp() },
                y = with(density) { link.anchor.top.toDp() }
            )
            .size(
                width = with(density) { link.anchor.width.toDp() },
                height = with(density) { link.anchor.height.toDp() }
            )
    ) {
        Popup(
            popupPositionProvider = positionProvider,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            AnimatedVisibility(
                visibleState = cardVisibility,
                enter = fadeIn(tween(CardFadeInDuration)) +
                    scaleIn(tween(CardFadeInDuration), initialScale = 0.94f) +
                    slideInVertically(tween(CardFadeInDuration)) { it / 6 },
                exit = fadeOut(tween(CardFadeOutDuration)) +
                    scaleOut(tween(CardFadeOutDuration), targetScale = 0.94f) +
                    slideOutVertically(tween(CardFadeOutDuration)) { it / 6 }
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = EmberrShadowElevation.Standard,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            RoundedCornerShape(10.dp)
                        )
                        .hoverable(cardInteractionSource)
                        .desktopPointerCursor(DesktopCursor.HAND)
                        .clickable(
                            interactionSource = cardInteractionSource,
                            indication = null
                        ) {
                            hoverState.clear()
                            when (link) {
                                is HoveredLink.Web -> webLinkActions.openLink(link.url)
                                is HoveredLink.Note -> onOpenNoteLink(link.noteId)
                                is HoveredLink.Email, is HoveredLink.Phone -> {}
                            }
                        }
                ) {
                    when (link) {
                        is HoveredLink.Web -> WebLinkCardContent(link.url)
                        is HoveredLink.Note -> NoteLinkCardContent(findNote(link.noteId))
                        is HoveredLink.Email, is HoveredLink.Phone -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun WebLinkCardContent(url: String) {
    val preview = webPreviewFor(url)

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            text = preview?.title ?: webLinkHost(url),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val description = preview?.description
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = url,
            style = MaterialTheme.typography.labelSmall,
            color = rememberWebLinkColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NoteLinkCardContent(note: NoteMetadataEntity?) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        val icon = note?.icon
        val title = note?.title?.ifBlank { "Untitled" } ?: "Note not found"

        Text(
            text = if (icon.isNullOrBlank()) title else "$icon  $title",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val snippet = note?.snippet?.trim()
        if (!snippet.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = snippet,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = lastEditedLabel(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                maxLines = 1
            )
        }
    }
}

private class CardAboveLinkPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val spaceAtTheEdge = gapPx * 2

        var y = anchorBounds.top - popupContentSize.height - gapPx
        if (y < spaceAtTheEdge) y = anchorBounds.bottom + gapPx

        var x = anchorBounds.left
        if (x + popupContentSize.width > windowSize.width - spaceAtTheEdge) {
            x = windowSize.width - popupContentSize.width - spaceAtTheEdge
        }

        return IntOffset(
            x.coerceAtLeast(spaceAtTheEdge),
            y.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height))
        )
    }
}

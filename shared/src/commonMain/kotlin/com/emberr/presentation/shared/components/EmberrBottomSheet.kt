package com.emberr.presentation.shared.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emberr.domain.util.system.isDesktopPlatform
import androidx.compose.ui.graphics.painter.Painter
import com.emberr.presentation.shared.stableStatusBarsPadding
import com.emberr.ui.theme.LocalAppIsDark
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.abs

private val BottomSheetCornerRadius = 26.dp
private val BottomSheetShape = RoundedCornerShape(topEnd = BottomSheetCornerRadius, topStart = BottomSheetCornerRadius)
private val FloatingDialogShape = RoundedCornerShape(12.dp)
private val SheetEdgeWidth = 0.5.dp
private const val SheetEdgeAlpha = 0.2f
private val SheetHorizontalPadding = 20.dp
private val SheetIconSpacing = 8.dp

/**
 * Scrolls a focused child just far enough to be fully visible, and not at all when it already is.
 * A sheet has to provide this for itself because a host screen can install its own
 * [BringIntoViewSpec] to reserve room for floating bars; inherited into a short sheet, those
 * reservations never fit, so every keystroke asks for a scroll the sheet cannot make and the
 * leftover distance is handed to the sheet's drag anchor instead, walking it off the bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
private object SheetBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leadingEdge = offset
        val trailingEdge = offset + size
        val bottomDelta = trailingEdge - containerSize
        return when {
            leadingEdge >= 0f && trailingEdge <= containerSize -> 0f
            leadingEdge < 0f && trailingEdge > containerSize -> 0f
            abs(leadingEdge) < abs(bottomDelta) -> leadingEdge
            else -> bottomDelta
        }
    }
}

private val KeepLeftoverUpwardFlingAwayFromSheet = object : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) available else Velocity.Zero
}

@Composable
private fun Modifier.sheetCardBackground(edgeShape: Shape? = null): Modifier {
    val backgroundColor = if (LocalAppIsDark.current) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.background
    }

    var modifierWithBackground = this.background(backgroundColor)

    if (edgeShape != null) {
        val edgeColor = MaterialTheme.colorScheme.outline.copy(alpha = SheetEdgeAlpha)
        modifierWithBackground = modifierWithBackground.border(
            width = SheetEdgeWidth,
            color = edgeColor,
            shape = edgeShape
        )
    }

    return modifierWithBackground
}

class EmberrBottomSheetAction(
    val icon: Painter,
    val contentDescription: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EmberrBottomSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    applyNavPadding: Boolean = false,
    headerAction: EmberrBottomSheetAction? = null,
    contentHorizontalPadding: Dp = SheetHorizontalPadding,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    if (!expanded) return

    CompositionLocalProvider(LocalBringIntoViewSpec provides SheetBringIntoViewSpec) {
        if (isDesktopPlatform) {
            EmberrFloatingDialog(
                onDismiss = onDismiss,
                title = title,
                subtitle = subtitle,
                headerAction = headerAction,
                contentHorizontalPadding = contentHorizontalPadding,
                content = content
            )
        } else {
            EmberrModalBottomSheet(
                onDismiss = onDismiss,
                title = title,
                subtitle = subtitle,
                applyNavPadding = applyNavPadding,
                headerAction = headerAction,
                contentHorizontalPadding = contentHorizontalPadding,
                content = content
            )
        }
    }
}

@Composable
private fun EmberrFloatingDialog(
    onDismiss: () -> Unit,
    title: String?,
    subtitle: String?,
    headerAction: EmberrBottomSheetAction? = null,
    contentHorizontalPadding: Dp,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    fun closeAnd(action: () -> Unit) {
        action()
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.9f)
                .clip(FloatingDialogShape)
                .sheetCardBackground(FloatingDialogShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                                .padding(horizontal = SheetHorizontalPadding, vertical = 8.dp)
                        )
                        if (headerAction != null) {
                            Box(modifier = Modifier.padding(end = SheetHorizontalPadding)) {
                                TopBarIconButton(
                                    icon = headerAction.icon,
                                    contentDescription = headerAction.contentDescription,
                                    bgColor = if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
                                    tint = headerAction.tint ?: MaterialTheme.colorScheme.onSurface,
                                    shadowElevation = EmberrShadowElevation.None,
                                    onClick = headerAction.onClick
                                )
                            }
                        }
//                        Box(modifier = Modifier.padding(end = SheetHorizontalPadding)) {
//                            TopBarIconButton(
//                                icon = Icons.Default.Close,
//                                contentDescription = "Close",
//                                bgColor = if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
//                                tint = MaterialTheme.colorScheme.onSurface,
//                                shadowElevation = EmberrShadowElevation.None,
//                                onClick = onDismiss
//                            )
//                        }
                    }
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = SheetHorizontalPadding).padding(bottom = 16.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = contentHorizontalPadding)
                ) {
                    content { action -> closeAnd(action) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmberrModalBottomSheet(
    onDismiss: () -> Unit,
    title: String?,
    subtitle: String?,
    applyNavPadding: Boolean,
    headerAction: EmberrBottomSheetAction? = null,
    contentHorizontalPadding: Dp,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nonScrollingAreaDragRelay = rememberScrollableState { 0f }

    fun closeAnd(action: () -> Unit) {
        coroutineScope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(250.milliseconds) {
                    sheetState.hide()
                }
            } finally {
                action()
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(0.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false
        )
    ) {
        KmpBackHandler(enabled = true) {
            coroutineScope.launch {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(250.milliseconds) {
                        sheetState.hide()
                    }
                } finally {
                    onDismiss()
                }
            }
        }

        // card
        Box(
            modifier = Modifier
                .nestedScroll(KeepLeftoverUpwardFlingAwayFromSheet)
                .scrollable(
                    state = nonScrollingAreaDragRelay,
                    orientation = Orientation.Vertical
                )
                .stableStatusBarsPadding()
                .fillMaxWidth()
                .clip(BottomSheetShape)
                .sheetCardBackground()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .then(if (applyNavPadding) Modifier.padding(bottom = 16.dp) else Modifier)
            ) {
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                                .padding(horizontal = SheetHorizontalPadding, vertical = 8.dp)
                        )
                        if (headerAction != null) {
                            Box(modifier = Modifier.padding(end = SheetHorizontalPadding)) {
                                TopBarIconButton(
                                    icon = headerAction.icon,
                                    contentDescription = headerAction.contentDescription,
                                    bgColor = if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
                                    tint = headerAction.tint ?: MaterialTheme.colorScheme.onSurface,
                                    shadowElevation = EmberrShadowElevation.None,
                                    onClick = headerAction.onClick
                                )
                            }
                        }
                    }
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = SheetHorizontalPadding).padding(bottom = 8.dp)
                    )
                }

                if (title != null || subtitle != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = SheetHorizontalPadding, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = contentHorizontalPadding)
                ) {
                    content { action -> closeAnd(action) }
                }
            }
        }
    }
}
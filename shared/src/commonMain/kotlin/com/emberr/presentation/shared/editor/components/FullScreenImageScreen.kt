package com.emberr.presentation.shared.editor.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.topHeaderBarPadding
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.presentation.shared.editor.DefaultBlockShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.painterResource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.copy
import emberr.shared.generated.resources.download
import emberr.shared.generated.resources.trash
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val DoubleTapZoomScale = 2.5f
private const val MaximumZoomScale = 5f
private const val OpenCloseDurationMillis = 300
private const val ScaleWhenThumbnailBoundsAreUnknown = 0.92f
private const val DismissDragHeightFraction = 0.18f
private const val DismissFlingVelocity = 1200f
private const val DismissShrinkAmount = 0.15f

@Composable
fun FullScreenImageScreen(
    request: Any?,
    hasLocalFile: Boolean,
    thumbnailBoundsInRoot: Rect?,
    imageWidthToHeightRatio: Float?,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val gestureScope = rememberCoroutineScope()

    val zoomScale = remember { Animatable(1f) }
    val panOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val dismissDragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val openProgress = remember { Animatable(0f) }

    var containerSize by remember { mutableStateOf(Size.Zero) }
    var overlayPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var isClosing by remember { mutableStateOf(false) }

    val zoomAnimationSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    val panAnimationSpec = remember {
        spring<Offset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    val settleBackAnimationSpec = remember {
        spring<Offset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    }
    val openCloseProgressSpec = remember {
        tween<Float>(durationMillis = OpenCloseDurationMillis, easing = FastOutSlowInEasing)
    }
    val openCloseOffsetSpec = remember {
        tween<Offset>(durationMillis = OpenCloseDurationMillis, easing = FastOutSlowInEasing)
    }

    val fittedImageSize = remember(containerSize, imageWidthToHeightRatio) {
        calculateFittedImageSize(containerSize, imageWidthToHeightRatio)
    }
    val startBounds = remember(thumbnailBoundsInRoot, overlayPositionInRoot) {
        thumbnailBoundsInRoot?.translate(-overlayPositionInRoot)
    }
    val isMorphingFromThumbnail = startBounds != null && fittedImageSize.width > 0f
    val startScale = if (startBounds != null && fittedImageSize.width > 0f) {
        startBounds.width / fittedImageSize.width
    } else {
        ScaleWhenThumbnailBoundsAreUnknown
    }
    val startTranslation = if (startBounds != null && fittedImageSize.width > 0f) {
        startBounds.center - containerSize.center
    } else {
        Offset.Zero
    }
    val blockCornerRadiusPx = remember(density, fittedImageSize) {
        DefaultBlockShape.topStart.toPx(fittedImageSize, density)
    }

    fun currentDismissFraction(): Float {
        val containerHeight = containerSize.height.coerceAtLeast(1f)
        return (abs(dismissDragOffset.value.y) / containerHeight).coerceIn(0f, 1f)
    }

    fun currentDragProgress(): Float =
        (currentDismissFraction() / DismissDragHeightFraction).coerceIn(0f, 1f)

    fun currentExpansion(): Float = openProgress.value * (1f - currentDismissFraction())

    LaunchedEffect(Unit) {
        openProgress.animateTo(1f, openCloseProgressSpec)
    }

    val closeFullScreen: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            gestureScope.launch { zoomScale.animateTo(1f, openCloseProgressSpec) }
            gestureScope.launch { panOffset.animateTo(Offset.Zero, openCloseOffsetSpec) }
            gestureScope.launch { dismissDragOffset.animateTo(Offset.Zero, openCloseOffsetSpec) }
            gestureScope.launch {
                openProgress.animateTo(0f, openCloseProgressSpec)
                onBack()
            }
        }
    }

    KmpBackHandler(enabled = true) { closeFullScreen() }

    val hazeState = remember { HazeState() }
    val tint = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size.toSize()
                overlayPositionInRoot = coordinates.positionInRoot()
            }
    ) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = currentExpansion() }
                    .background(MaterialTheme.colorScheme.background)
            )

            val imageSizeModifier = if (fittedImageSize.width > 0f) {
                with(density) {
                    Modifier.size(fittedImageSize.width.toDp(), fittedImageSize.height.toDp())
                }
            } else {
                Modifier.fillMaxSize()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(imageSizeModifier)
                    .graphicsLayer {
                        val morphProgress = openProgress.value
                        val dragProgress = currentDragProgress()
                        val shrinkWhileDragging = 1f - DismissShrinkAmount * dragProgress
                        val totalScale =
                            (startScale + (1f - startScale) * morphProgress) * shrinkWhileDragging * zoomScale.value
                        scaleX = totalScale
                        scaleY = totalScale
                        translationX =
                            startTranslation.x * (1f - morphProgress) + panOffset.value.x + dismissDragOffset.value.x
                        translationY =
                            startTranslation.y * (1f - morphProgress) + panOffset.value.y + dismissDragOffset.value.y
                        alpha = if (isMorphingFromThumbnail) 1f else morphProgress

                        val insetFromScreenEdge = minOf(
                            containerSize.width - fittedImageSize.width * totalScale,
                            containerSize.height - fittedImageSize.height * totalScale
                        ) / 2f
                        val cornerVisibility =
                            (insetFromScreenEdge / blockCornerRadiusPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val roundingProgress = maxOf(dragProgress, 1f - morphProgress) * cornerVisibility
                        clip = roundingProgress > 0f
                        shape = RoundedCornerShape(
                            blockCornerRadiusPx * roundingProgress / totalScale.coerceAtLeast(0.01f)
                        )
                    }
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = "Full Screen Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(fittedImageSize) {
                    detectTapGestures(
                        onDoubleTap = { tapPosition ->
                            val targetScale = if (zoomScale.value > 1f) 1f else DoubleTapZoomScale
                            val targetOffset = if (targetScale == 1f) {
                                Offset.Zero
                            } else {
                                val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                                val shiftTowardsTap = (tapPosition - viewportCenter) * (1f - targetScale)
                                val maxX = (fittedImageSize.width * (targetScale - 1f)) / 2f
                                val maxY = (fittedImageSize.height * (targetScale - 1f)) / 2f
                                Offset(
                                    x = shiftTowardsTap.x.coerceIn(-maxX, maxX),
                                    y = shiftTowardsTap.y.coerceIn(-maxY, maxY)
                                )
                            }
                            gestureScope.launch { zoomScale.animateTo(targetScale, zoomAnimationSpec) }
                            gestureScope.launch { panOffset.animateTo(targetOffset, panAnimationSpec) }
                        }
                    )
                }
                .pointerInput(fittedImageSize) {
                    detectZoomPanAndDismissDrag(
                        onGesture = { pan, zoom ->
                            val isZoomingOrPanningZoomedImage = zoom != 1f || zoomScale.value > 1f
                            if (isZoomingOrPanningZoomedImage) {
                                val nextScale = (zoomScale.value * zoom).coerceIn(1f, MaximumZoomScale)
                                val nextOffset = if (nextScale > 1f) {
                                    val maxX = (fittedImageSize.width * (nextScale - 1f)) / 2f
                                    val maxY = (fittedImageSize.height * (nextScale - 1f)) / 2f
                                    Offset(
                                        x = (panOffset.value.x + pan.x).coerceIn(-maxX, maxX),
                                        y = (panOffset.value.y + pan.y).coerceIn(-maxY, maxY)
                                    )
                                } else {
                                    Offset.Zero
                                }
                                gestureScope.launch {
                                    zoomScale.snapTo(nextScale)
                                    panOffset.snapTo(nextOffset)
                                }
                            } else {
                                gestureScope.launch {
                                    dismissDragOffset.snapTo(dismissDragOffset.value + pan)
                                }
                            }
                        },
                        onGestureFinished = { verticalVelocity ->
                            val draggedFarEnough =
                                dismissDragOffset.value.y > containerSize.height * DismissDragHeightFraction
                            val flungDownwards =
                                verticalVelocity > DismissFlingVelocity && dismissDragOffset.value.y > 0f
                            if (zoomScale.value <= 1f && (draggedFarEnough || flungDownwards)) {
                                closeFullScreen()
                            } else if (dismissDragOffset.value != Offset.Zero) {
                                gestureScope.launch {
                                    dismissDragOffset.animateTo(Offset.Zero, settleBackAnimationSpec)
                                }
                            }
                        }
                    )
                }
        )

        EmberrTopHeaderBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = currentExpansion() },
            hazeState = hazeState,
            applyStatusBarPadding = true,
            contentPadding = topHeaderBarPadding(top = 18.dp, horizontal = 18.dp),
            onBackClick = closeFullScreen
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = currentExpansion() }
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                .clip(DefaultBlockShape)
                .emberrBlur(hazeState, EmberrBlur.Regular)
                .background(Color.Transparent)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = DefaultBlockShape
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                val iconSize = 18.dp

                Icon(
                    painter = painterResource(Res.drawable.download),
                    contentDescription = "Download",
                    modifier = Modifier.size(iconSize).clickable {
                        if (hasLocalFile) onDownload()
                    },
                    tint = tint
                )

                Box(Modifier.width(1.dp).height(18.dp).background(tint.copy(alpha = 0.2f)))

                Icon(
                    painter = painterResource(Res.drawable.copy),
                    contentDescription = "Copy Image",
                    modifier = Modifier.size(iconSize).clickable {
                        if (hasLocalFile) onCopy()
                    },
                    tint = tint
                )

                Box(Modifier.width(1.dp).height(18.dp).background(tint.copy(alpha = 0.2f)))

                Icon(
                    painter = painterResource(Res.drawable.trash),
                    contentDescription = "Delete",
                    modifier = Modifier.size(iconSize).clickable {
                        onDelete()
                    },
                    tint = tint
                )
            }
        }
    }
}

private fun calculateFittedImageSize(containerSize: Size, widthToHeightRatio: Float?): Size {
    if (containerSize.width <= 0f || containerSize.height <= 0f) return Size.Zero
    if (widthToHeightRatio == null || widthToHeightRatio <= 0f) return containerSize
    val heightWhenFillingWidth = containerSize.width / widthToHeightRatio
    return if (heightWhenFillingWidth <= containerSize.height) {
        Size(containerSize.width, heightWhenFillingWidth)
    } else {
        Size(containerSize.height * widthToHeightRatio, containerSize.height)
    }
}

private suspend fun PointerInputScope.detectZoomPanAndDismissDrag(
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    onGestureFinished: (verticalVelocity: Float) -> Unit
) {
    awaitEachGesture {
        var accumulatedZoom = 1f
        var accumulatedPan = Offset.Zero
        var hasCrossedTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        val velocityTracker = VelocityTracker()

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (!hasCrossedTouchSlop) {
                accumulatedZoom *= zoomChange
                accumulatedPan += panChange
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1f - accumulatedZoom) * centroidSize
                if (zoomMotion > touchSlop || accumulatedPan.getDistance() > touchSlop) {
                    hasCrossedTouchSlop = true
                }
            }

            if (hasCrossedTouchSlop) {
                if (zoomChange != 1f || panChange != Offset.Zero) onGesture(panChange, zoomChange)
                event.changes.firstOrNull { it.pressed }?.let { pointer ->
                    velocityTracker.addPosition(pointer.uptimeMillis, pointer.position)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })

        onGestureFinished(if (hasCrossedTouchSlop) velocityTracker.calculateVelocity().y else 0f)
    }
}

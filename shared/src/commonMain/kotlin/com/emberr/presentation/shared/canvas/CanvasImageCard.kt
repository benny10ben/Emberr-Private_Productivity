package com.emberr.presentation.shared.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.presentation.shared.editor.blockViews.MediaAvailability
import com.emberr.presentation.shared.editor.blockViews.rememberMediaAvailability
import org.koin.compose.koinInject
import java.io.File
import kotlin.math.roundToInt

private val IMAGE_CORNER_RADIUS = 6.dp
private const val IMAGE_MAX_DECODE_PIXELS = 1920

@Composable
internal fun CanvasImageCard(node: CanvasNodeEntity, viewport: CanvasViewport, isSelected: Boolean) {
    val mediaStorageHelper = koinInject<MediaStorageHelper>()
    val fileName = node.imagePath.orEmpty()
    val absolutePath = remember(fileName) { mediaStorageHelper.getAbsoluteMediaPath(fileName) }
    val availability = rememberMediaAvailability(absolutePath, fileName.substringAfterLast("/"))
    val context = LocalPlatformContext.current
    val request = remember(absolutePath, context) {
        ImageRequest.Builder(context)
            .data(File(absolutePath))
            .size(IMAGE_MAX_DECODE_PIXELS)
            .build()
    }

    val baseDensity = LocalDensity.current
    val screenTopLeft = viewport.worldToScreen(Offset(node.x, node.y), baseDensity.density)
    val zoomedDensity = Density(baseDensity.density * viewport.zoom, baseDensity.fontScale)
    val cardShape = RoundedCornerShape(IMAGE_CORNER_RADIUS)
    val selectionBorderWidth = (2f / viewport.zoom).dp

    Box(
        Modifier
            .offset { IntOffset(screenTopLeft.x.roundToInt(), screenTopLeft.y.roundToInt()) }
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
    ) {
        CompositionLocalProvider(LocalDensity provides zoomedDensity) {
            Box(
                modifier = Modifier
                    .size(node.width.dp, node.height.dp)
                    .then(if (isSelected) Modifier.border(selectionBorderWidth, CanvasSelectionColor, cardShape) else Modifier)
                    .clip(cardShape),
                contentAlignment = Alignment.Center
            ) {
                if (availability == MediaAvailability.Available) {
                    AsyncImage(
                        model = request,
                        contentDescription = "Canvas image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)).padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (availability == MediaAvailability.Failed) "Image failed to download" else "Downloading image...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

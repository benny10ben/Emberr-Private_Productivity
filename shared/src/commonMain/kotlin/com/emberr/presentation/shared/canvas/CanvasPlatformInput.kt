package com.emberr.presentation.shared.canvas

import androidx.compose.ui.input.pointer.PointerIcon

expect fun canvasResizePointerIcon(edges: CanvasResizeEdges): PointerIcon

expect val horizontalScrollArrivesAsBackAndForwardButtons: Boolean

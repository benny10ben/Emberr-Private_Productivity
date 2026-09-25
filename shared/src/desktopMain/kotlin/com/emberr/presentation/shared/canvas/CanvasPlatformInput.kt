package com.emberr.presentation.shared.canvas

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun canvasResizePointerIcon(edges: CanvasResizeEdges): PointerIcon {
    val cursorType = when {
        edges.top && edges.left -> Cursor.NW_RESIZE_CURSOR
        edges.top && edges.right -> Cursor.NE_RESIZE_CURSOR
        edges.bottom && edges.left -> Cursor.SW_RESIZE_CURSOR
        edges.bottom && edges.right -> Cursor.SE_RESIZE_CURSOR
        edges.top -> Cursor.N_RESIZE_CURSOR
        edges.bottom -> Cursor.S_RESIZE_CURSOR
        edges.left -> Cursor.W_RESIZE_CURSOR
        else -> Cursor.E_RESIZE_CURSOR
    }
    return PointerIcon(Cursor(cursorType))
}

actual val horizontalScrollArrivesAsBackAndForwardButtons: Boolean =
    System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)

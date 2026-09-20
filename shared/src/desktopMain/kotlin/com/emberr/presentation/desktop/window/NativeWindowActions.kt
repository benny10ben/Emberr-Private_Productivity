package com.emberr.presentation.desktop.window

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.Component
import java.awt.Window
import java.lang.reflect.Field
import java.lang.reflect.Method

enum class WindowResizeEdge(val nativeDirection: Int) {
    TopLeft(0),
    Top(1),
    TopRight(2),
    Right(3),
    BottomRight(4),
    Bottom(5),
    BottomLeft(6),
    Left(7)
}

private interface X11WindowFunctions : Library {
    fun XUngrabPointer(display: X11.Display, time: NativeLong): Int
}

object NativeWindowActions {

    val isAvailable: Boolean get() = bridge != null

    fun beginResize(window: Window, edge: WindowResizeEdge, screenX: Int, screenY: Int): Boolean =
        bridge?.beginResize(window, edge, screenX, screenY) ?: false

    fun showWindowMenu(window: Window, screenX: Int, screenY: Int): Boolean =
        bridge?.showWindowMenu(window, screenX, screenY) ?: false

    fun beginMove(window: Window, screenX: Int, screenY: Int): Boolean =
        bridge?.beginMove(window, screenX, screenY) ?: false

    fun makeUncoveredAreaTransparentWhileResizing(window: Window) {
        bridge?.makeUncoveredAreaTransparentWhileResizing(window)
    }

    fun reportInvisibleBorderWidth(window: Window, borderWidthPx: Int) {
        bridge?.reportInvisibleBorderWidth(window, borderWidthPx)
    }

    private val bridge: X11WindowBridge? by lazy {
        if (!System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)) {
            return@lazy null
        }
        runCatching { X11WindowBridge.create() }.getOrNull()
    }
}

private class X11WindowBridge(
    private val display: X11.Display,
    private val rootWindow: X11.Window,
    private val moveResizeAtom: X11.Atom,
    private val showWindowMenuAtom: X11.Atom,
    private val frameExtentsAtom: X11.Atom,
    private val componentPeerField: Field,
    private val awtLock: Method,
    private val awtUnlock: Method,
    private val windowFunctions: X11WindowFunctions
) {

    fun beginResize(window: Window, edge: WindowResizeEdge, screenX: Int, screenY: Int): Boolean {
        val windowId = readNativeWindowId(window) ?: return false
        return sendRootClientMessage(
            windowId = windowId,
            messageAtom = moveResizeAtom,
            values = longArrayOf(
                screenX.toLong(),
                screenY.toLong(),
                edge.nativeDirection.toLong(),
                PRIMARY_MOUSE_BUTTON,
                SOURCE_IS_NORMAL_APPLICATION
            )
        )
    }

    fun beginMove(window: Window, screenX: Int, screenY: Int): Boolean {
        val windowId = readNativeWindowId(window) ?: return false
        return sendRootClientMessage(
            windowId = windowId,
            messageAtom = moveResizeAtom,
            values = longArrayOf(
                screenX.toLong(),
                screenY.toLong(),
                MOVE_DIRECTION,
                PRIMARY_MOUSE_BUTTON,
                SOURCE_IS_NORMAL_APPLICATION
            )
        )
    }

    fun showWindowMenu(window: Window, screenX: Int, screenY: Int): Boolean {
        val windowId = readNativeWindowId(window) ?: return false
        return sendRootClientMessage(
            windowId = windowId,
            messageAtom = showWindowMenuAtom,
            values = longArrayOf(
                CORE_POINTER_DEVICE,
                screenX.toLong(),
                screenY.toLong(),
                0L,
                0L
            )
        )
    }

    fun makeUncoveredAreaTransparentWhileResizing(window: Window) {
        val windowId = readNativeWindowId(window) ?: return
        runCatching {
            awtLock.invoke(null)
            try {
                useTransparentBackgroundForWindowTree(X11.Window(windowId))
                X11.INSTANCE.XFlush(display)
            } finally {
                awtUnlock.invoke(null)
            }
        }
    }

    fun reportInvisibleBorderWidth(window: Window, borderWidthPx: Int) {
        val windowId = readNativeWindowId(window) ?: return
        runCatching {
            awtLock.invoke(null)
            try {
                val extents = Memory(EXTENTS_VALUE_COUNT.toLong() * NativeLong.SIZE)
                repeat(EXTENTS_VALUE_COUNT) { index ->
                    extents.setNativeLong(
                        index.toLong() * NativeLong.SIZE,
                        NativeLong(borderWidthPx.toLong())
                    )
                }
                X11.INSTANCE.XChangeProperty(
                    display,
                    X11.Window(windowId),
                    frameExtentsAtom,
                    X11.XA_CARDINAL,
                    PROPERTY_FORMAT_32_BIT,
                    X11.PropModeReplace,
                    extents,
                    EXTENTS_VALUE_COUNT
                )
                X11.INSTANCE.XFlush(display)
            } finally {
                awtUnlock.invoke(null)
            }
        }
    }

    private fun sendRootClientMessage(
        windowId: Long,
        messageAtom: X11.Atom,
        values: LongArray
    ): Boolean = runCatching {
        awtLock.invoke(null)
        try {
            windowFunctions.XUngrabPointer(display, NativeLong(CURRENT_TIME))
            val event = buildClientMessage(windowId, messageAtom, values)
            val sent = X11.INSTANCE.XSendEvent(
                display,
                rootWindow,
                0,
                NativeLong(SUBSTRUCTURE_NOTIFY_MASK or SUBSTRUCTURE_REDIRECT_MASK),
                event
            )
            X11.INSTANCE.XFlush(display)
            sent != 0
        } finally {
            awtUnlock.invoke(null)
        }
    }.getOrDefault(false)

    private fun buildClientMessage(
        windowId: Long,
        messageAtom: X11.Atom,
        values: LongArray
    ): X11.XEvent {
        val event = X11.XEvent()
        event.setType(X11.XClientMessageEvent::class.java)
        event.xclient.type = CLIENT_MESSAGE_TYPE
        event.xclient.serial = NativeLong(0)
        event.xclient.send_event = 1
        event.xclient.display = display
        event.xclient.window = X11.Window(windowId)
        event.xclient.message_type = messageAtom
        event.xclient.format = 32
        event.xclient.data.setType(Array<NativeLong>::class.java)
        values.forEachIndexed { index, value ->
            event.xclient.data.l[index] = NativeLong(value)
        }
        event.write()
        return event
    }

    private fun useTransparentBackgroundForWindowTree(window: X11.Window) {
        val attributes = X11.XWindowAttributes()
        val readAttributes = X11.INSTANCE.XGetWindowAttributes(display, window, attributes)
        if (readAttributes != 0 && attributes.c_class == INPUT_OUTPUT_WINDOW) {
            val resizeAttributes = X11.XSetWindowAttributes().apply {
                background_pixel = NativeLong(FULLY_TRANSPARENT_PIXEL)
                bit_gravity = KEEP_CONTENT_ANCHORED_TOP_LEFT
            }
            X11.INSTANCE.XChangeWindowAttributes(
                display,
                window,
                NativeLong(CHANGE_BACKGROUND_PIXEL or CHANGE_BIT_GRAVITY),
                resizeAttributes
            )
        }

        val rootWindowRef = X11.WindowByReference()
        val parentWindowRef = X11.WindowByReference()
        val childrenRef = PointerByReference()
        val childCountRef = IntByReference()

        val queried = X11.INSTANCE.XQueryTree(
            display,
            window,
            rootWindowRef,
            parentWindowRef,
            childrenRef,
            childCountRef
        )
        if (queried == 0) return

        val children = childrenRef.value ?: return
        if (childCountRef.value > 0) {
            children.getLongArray(0, childCountRef.value).forEach { childId ->
                useTransparentBackgroundForWindowTree(X11.Window(childId))
            }
        }
        X11.INSTANCE.XFree(children)
    }

    private fun readNativeWindowId(window: Window): Long? {
        val peer = componentPeerField.get(window) ?: return null
        val readWindowId = peer.javaClass.getMethod("getWindow").apply { isAccessible = true }
        return (readWindowId.invoke(peer) as? Long)?.takeIf { it != 0L }
    }

    companion object {
        private const val CLIENT_MESSAGE_TYPE = 33
        private const val EXTENTS_VALUE_COUNT = 4
        private const val PROPERTY_FORMAT_32_BIT = 32
        private const val FULLY_TRANSPARENT_PIXEL = 0L
        private const val KEEP_CONTENT_ANCHORED_TOP_LEFT = 1
        private const val CHANGE_BACKGROUND_PIXEL = 1L shl 1
        private const val CHANGE_BIT_GRAVITY = 1L shl 4
        private const val INPUT_OUTPUT_WINDOW = 1
        private const val CURRENT_TIME = 0L
        private const val MOVE_DIRECTION = 8L
        private const val PRIMARY_MOUSE_BUTTON = 1L
        private const val CORE_POINTER_DEVICE = 0L
        private const val SOURCE_IS_NORMAL_APPLICATION = 1L
        private const val SUBSTRUCTURE_NOTIFY_MASK = 1L shl 19
        private const val SUBSTRUCTURE_REDIRECT_MASK = 1L shl 20

        fun create(): X11WindowBridge {
            val componentPeerField = Component::class.java.getDeclaredField("peer")
                .apply { isAccessible = true }

            val toolkit = Class.forName("sun.awt.X11.XToolkit")
            val readDisplayAddress = toolkit.getDeclaredMethod("getDisplay")
                .apply { isAccessible = true }
            val displayAddress = readDisplayAddress.invoke(null) as Long
            require(displayAddress != 0L) { "AWT has no X11 display connection" }

            val sunToolkit = Class.forName("sun.awt.SunToolkit")
            val awtLock = sunToolkit.getMethod("awtLock").apply { isAccessible = true }
            val awtUnlock = sunToolkit.getMethod("awtUnlock").apply { isAccessible = true }

            val display = X11.Display().apply { pointer = Pointer(displayAddress) }
            val windowFunctions = Native.load("X11", X11WindowFunctions::class.java)

            awtLock.invoke(null)
            try {
                return X11WindowBridge(
                    display = display,
                    rootWindow = X11.INSTANCE.XDefaultRootWindow(display),
                    moveResizeAtom = X11.INSTANCE.XInternAtom(display, "_NET_WM_MOVERESIZE", false),
                    showWindowMenuAtom = X11.INSTANCE.XInternAtom(display, "_GTK_SHOW_WINDOW_MENU", false),
                    frameExtentsAtom = X11.INSTANCE.XInternAtom(display, "_GTK_FRAME_EXTENTS", false),
                    componentPeerField = componentPeerField,
                    awtLock = awtLock,
                    awtUnlock = awtUnlock,
                    windowFunctions = windowFunctions
                )
            } finally {
                awtUnlock.invoke(null)
            }
        }
    }
}

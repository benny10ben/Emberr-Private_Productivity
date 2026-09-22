package com.emberr.core.desktop

import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11

object StartupNotificationCompleter {

    private const val CLIENT_MESSAGE_EVENT_TYPE = 33
    private const val PROPERTY_CHANGE_EVENT_MASK = 1L shl 22
    private const val EIGHT_BIT_MESSAGE_FORMAT = 8
    private const val BYTES_PER_MESSAGE_CHUNK = 20
    private const val OFF_SCREEN_SENDER_POSITION = -200

    fun markLaunchAsFinished() {
        val startupId = System.getenv("DESKTOP_STARTUP_ID")
        if (startupId.isNullOrBlank()) return
        if (!System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)) return
        runCatching { announceLaunchIsFinished(startupId) }
    }

    private fun announceLaunchIsFinished(startupId: String) {
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return
        try {
            val rootWindow = x11.XDefaultRootWindow(display) ?: return
            val senderWindow = x11.XCreateSimpleWindow(
                display,
                rootWindow,
                OFF_SCREEN_SENDER_POSITION,
                OFF_SCREEN_SENDER_POSITION,
                1,
                1,
                0,
                0,
                0
            ) ?: return

            val firstChunkAtom = x11.XInternAtom(display, "_NET_STARTUP_INFO_BEGIN", false)
            val laterChunkAtom = x11.XInternAtom(display, "_NET_STARTUP_INFO", false)

            val messageBytes = "remove: ID=${quoteValueIfNeeded(startupId)}"
                .toByteArray(Charsets.UTF_8) + 0.toByte()

            messageBytes.asList()
                .chunked(BYTES_PER_MESSAGE_CHUNK)
                .forEachIndexed { chunkIndex, chunkBytes ->
                    val messageAtom = if (chunkIndex == 0) firstChunkAtom else laterChunkAtom
                    x11.XSendEvent(
                        display,
                        rootWindow,
                        0,
                        NativeLong(PROPERTY_CHANGE_EVENT_MASK),
                        buildChunkEvent(display, senderWindow, messageAtom, chunkBytes.toByteArray())
                    )
                }

            x11.XSync(display, false)
            x11.XDestroyWindow(display, senderWindow)
            x11.XFlush(display)
        } finally {
            runCatching { x11.XCloseDisplay(display) }
        }
    }

    private fun buildChunkEvent(
        display: X11.Display,
        senderWindow: X11.Window,
        messageAtom: X11.Atom,
        chunkBytes: ByteArray
    ): X11.XEvent {
        val event = X11.XEvent()
        event.setType(X11.XClientMessageEvent::class.java)
        event.xclient.type = CLIENT_MESSAGE_EVENT_TYPE
        event.xclient.serial = NativeLong(0)
        event.xclient.send_event = 1
        event.xclient.display = display
        event.xclient.window = senderWindow
        event.xclient.message_type = messageAtom
        event.xclient.format = EIGHT_BIT_MESSAGE_FORMAT
        event.xclient.data.setType(ByteArray::class.java)
        chunkBytes.copyInto(event.xclient.data.b)
        event.write()
        return event
    }

    private fun quoteValueIfNeeded(value: String): String {
        val needsQuotes = value.any { character -> character == ' ' || character == '"' || character == '\\' }
        if (!needsQuotes) return value
        val escapedValue = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return "\"$escapedValue\""
    }
}

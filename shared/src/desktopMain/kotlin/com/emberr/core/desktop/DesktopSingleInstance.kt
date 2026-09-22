package com.emberr.core.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.FileLock
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

object DesktopSingleInstance {

    private const val LOCK_FILE_NAME = "instance.lock"
    private const val HANDOFF_SOCKET_FILE_NAME = "instance.socket"

    private val showWindowRequestFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showWindowRequests: SharedFlow<Unit> = showWindowRequestFlow.asSharedFlow()

    private var ownershipLock: FileLock? = null
    private var handoffListener: ServerSocketChannel? = null

    private val instanceFolder: File by lazy {
        val runtimeFolderPath = System.getenv("XDG_RUNTIME_DIR")
        val folder = if (!runtimeFolderPath.isNullOrBlank() && File(runtimeFolderPath).isDirectory) {
            File(runtimeFolderPath, "emberr")
        } else {
            File(System.getProperty("user.home"), ".emberr")
        }
        folder.mkdirs()
        folder
    }

    private val handoffSocketFile: File
        get() = File(instanceFolder, HANDOFF_SOCKET_FILE_NAME)

    fun claimOwnershipOrWakeRunningApp(): Boolean {
        val lockAttempt = tryLockInstanceFile()
        if (lockAttempt.isFailure) return true

        val claimedLock = lockAttempt.getOrNull()
        if (claimedLock == null) {
            askRunningAppToShowItsWindow()
            StartupNotificationCompleter.markLaunchAsFinished()
            return false
        }

        ownershipLock = claimedLock
        startListeningForWakeRequests()
        return true
    }

    private fun tryLockInstanceFile(): Result<FileLock?> = runCatching {
        val lockFileChannel = RandomAccessFile(File(instanceFolder, LOCK_FILE_NAME), "rw").channel
        val lock = lockFileChannel.tryLock()
        if (lock == null) lockFileChannel.close()
        lock
    }

    private fun askRunningAppToShowItsWindow() {
        runCatching {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(UnixDomainSocketAddress.of(handoffSocketFile.toPath()))
            }
        }
    }

    private fun startListeningForWakeRequests() {
        runCatching {
            handoffSocketFile.delete()
            val listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            listener.bind(UnixDomainSocketAddress.of(handoffSocketFile.toPath()))
            handoffListener = listener

            Thread({ acceptWakeRequestsUntilClosed(listener) }, "emberr-single-instance").apply {
                isDaemon = true
                start()
            }

            Runtime.getRuntime().addShutdownHook(Thread { releaseOwnership() })
        }
    }

    private fun acceptWakeRequestsUntilClosed(listener: ServerSocketChannel) {
        while (listener.isOpen) {
            val incomingRequest = runCatching { listener.accept() }.getOrNull() ?: break
            runCatching { incomingRequest.close() }
            showWindowRequestFlow.tryEmit(Unit)
        }
    }

    fun releaseOwnership() {
        runCatching { handoffListener?.close() }
        runCatching { handoffSocketFile.delete() }
        runCatching { ownershipLock?.release() }
        runCatching { ownershipLock?.channel()?.close() }
        handoffListener = null
        ownershipLock = null
    }
}

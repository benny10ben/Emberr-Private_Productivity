package com.emberr.domain.reminders

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val HELP_PROBE_TIMEOUT_SECONDS = 2L

class DesktopReminderScheduler : ReminderScheduler {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val activeTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()

    private val os = System.getProperty("os.name").lowercase()

    private val clickWatchers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "emberr-reminder-click").apply { isDaemon = true }
    }

    private val linuxNotificationsSupportActions: Boolean by lazy {
        runCatching {
            val help = ProcessBuilder("notify-send", "--help").redirectErrorStream(true).start()
            val helpText = help.inputStream.bufferedReader().use { it.readText() }
            help.waitFor(HELP_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            helpText.contains("--action")
        }.getOrDefault(false)
    }

    override fun schedule(blockId: String, noteTitle: String, text: String, timestamp: Long) {
        cancel(blockId)
        val delay = timestamp - System.currentTimeMillis()
        if (delay <= 0) return

        val task = scheduler.schedule({
            triggerNativeNotification(blockId, noteTitle, text)
            activeTasks.remove(blockId)
        }, delay, TimeUnit.MILLISECONDS)

        activeTasks[blockId] = task
    }

    override fun cancel(blockId: String) {
        activeTasks[blockId]?.cancel(false)
        activeTasks.remove(blockId)
    }

    private fun triggerNativeNotification(blockId: String, title: String, message: String) {
        try {
            when {
                os.contains("win") -> {
                    val psScript = """
                        [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                        [Windows.UI.Notifications.ToastNotification, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                        
                        ${'$'}template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
                        ${'$'}textNodes = ${'$'}template.GetElementsByTagName('text')
                        ${'$'}textNodes.Item(0).AppendChild(${'$'}template.CreateTextNode('$title')) | Out-Null
                        ${'$'}textNodes.Item(1).AppendChild(${'$'}template.CreateTextNode('$message')) | Out-Null
                        
                        ${'$'}notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Emberr')
                        ${'$'}notifier.Show([Windows.UI.Notifications.ToastNotification]::new(${'$'}template))
                    """.trimIndent()

                    ProcessBuilder("powershell", "-NoProfile", "-Command", psScript).start()
                }
                os.contains("mac") -> {
                    val script = "display notification \"$message\" with title \"$title\""
                    ProcessBuilder("osascript", "-e", script).start()
                }
                else -> showClickableLinuxNotification(blockId, title, message)
            }
        } catch (e: Exception) {
            println("Notification failed: ${e.message}")
        }
    }

    private fun showClickableLinuxNotification(blockId: String, title: String, message: String) {
        if (!linuxNotificationsSupportActions) {
            ProcessBuilder("notify-send", "-a", "Emberr", "-u", "normal", title, message).start()
            return
        }

        val notification = ProcessBuilder(
            "notify-send",
            "-a", "Emberr",
            "-u", "normal",
            "-A", "default=Open",
            "-A", "open=Open",
            title,
            message
        ).start()

        clickWatchers.submit {
            val chosenAction = runCatching {
                notification.inputStream.bufferedReader().use { it.readLine() }
            }.getOrNull()

            if (!chosenAction.isNullOrBlank()) {
                ReminderClickBus.requestOpen(blockId)
            }
        }
    }
}
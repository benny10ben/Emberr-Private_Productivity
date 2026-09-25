package com.emberr.domain.reminders

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.emberr.domain.reminders.ReminderRescheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Listens for alarms triggered by the system and pushes the actual notification to the user.
 * Runs independently in the background, even if the app is completely closed.
 */
class AndroidReminderReceiver : BroadcastReceiver(), KoinComponent {

    private val reminderRescheduler: ReminderRescheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val noteTitle = intent.getStringExtra("note_title") ?: "Reminder"
        val blockText = intent.getStringExtra("block_text") ?: "You have a task to check."
        val notificationId = intent.getStringExtra("block_id")?.hashCode() ?: 1

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "emberr_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_lock_idle_alarm)
            .setContentTitle(noteTitle)
            .setContentText(blockText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reminderRescheduler.rescheduleUpcomingReminders()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
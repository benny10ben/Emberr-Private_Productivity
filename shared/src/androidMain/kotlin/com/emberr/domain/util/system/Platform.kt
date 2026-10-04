package com.emberr.domain.util.system

import android.app.ActivityManager
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import org.koin.mp.KoinPlatform

actual val isDesktopPlatform = false
actual val appVersionName: String?
    get() {
        val context = KoinPlatform.getKoin().get<Context>()
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

actual fun showFeedback(message: String) {
    val context = KoinPlatform.getKoin().get<android.content.Context>()
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

actual fun triggerHapticFeedback() {
    val context = KoinPlatform.getKoin().get<Context>()
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
}

actual fun restartApplication() {}

actual suspend fun eraseAllAppData(): Boolean {
    val context = KoinPlatform.getKoin().get<Context>()
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
    return activityManager.clearApplicationUserData()
}

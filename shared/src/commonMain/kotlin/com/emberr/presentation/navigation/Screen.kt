package com.emberr.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Defines all the navigation routes used across the app.
 */
sealed class Screen(val route: String, val title: String? = null, val icon: ImageVector? = null) {

    // Main bottom-bar tabs
    //
    // Daily carries an optional "date" query arg so other screens (e.g. search results) can
    // deep-link straight to a specific day. It's nullable with no default, so every existing
    // navigate(Screen.Daily.route) call - which never passes ?date=... - is unaffected and
    // still opens on whatever date DailyEditorViewModel currently holds.
    object Daily : Screen("daily_screen?date={date}", "Daily", Icons.Default.CalendarToday) {
        fun createRoute(date: String? = null) = if (date != null) "daily_screen?date=$date" else "daily_screen"
    }
    object Home : Screen("home_screen", "Home", Icons.AutoMirrored.Filled.Notes)

    // Sub-screens for organizing specific types of blocks
    object Reminders : Screen("reminders")
    object Bookmarks : Screen("bookmarks")
    object Images : Screen("images")
    object Documents : Screen("documents")
    object Calendar : Screen("calendar_screen")

    object Settings : Screen("settings")

    object SelfHostSetup : Screen("self_host_setup")

    object AiChat : Screen("rag_chat")

    object Splash : Screen("splash_screen")

    object Onboarding : Screen("onboarding")

    /**
     * Route for opening a specific note.
     */
    object Note : Screen("editor_screen/{noteId}") {
        fun createRoute(noteId: String) = "editor_screen/$noteId"
    }
}
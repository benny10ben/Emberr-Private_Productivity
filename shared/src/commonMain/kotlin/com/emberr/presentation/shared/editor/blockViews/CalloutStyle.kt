package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.emberr.domain.model.CalloutType

private val CalloutBlue = Color(0xFF086DDD)
private val CalloutCyan = Color(0xFF00BFBC)
private val CalloutGreen = Color(0xFF08B94E)
private val CalloutOrange = Color(0xFFEC7500)
private val CalloutRed = Color(0xFFE93147)
private val CalloutPurple = Color(0xFF7852EE)
private val CalloutGray = Color(0xFF9E9E9E)
private const val CalloutTintedBackgroundAlpha = 0.12f

fun CalloutType.accentColor(): Color = when (this) {
    CalloutType.INFO, CalloutType.TODO -> CalloutBlue
    CalloutType.ABSTRACT, CalloutType.TIP -> CalloutCyan
    CalloutType.SUCCESS -> CalloutGreen
    CalloutType.QUESTION, CalloutType.WARNING -> CalloutOrange
    CalloutType.FAILURE, CalloutType.DANGER, CalloutType.BUG -> CalloutRed
    CalloutType.EXAMPLE -> CalloutPurple
    CalloutType.NOTE, CalloutType.QUOTE -> CalloutGray
}

fun CalloutType.backgroundColor(surfaceColor: Color): Color = when (this) {
    CalloutType.NOTE, CalloutType.QUOTE -> surfaceColor
    else -> accentColor().copy(alpha = CalloutTintedBackgroundAlpha)
}

fun CalloutType.icon(): ImageVector = when (this) {
    CalloutType.NOTE -> Icons.Outlined.Edit
    CalloutType.ABSTRACT -> Icons.AutoMirrored.Outlined.Assignment
    CalloutType.INFO -> Icons.Outlined.Info
    CalloutType.TODO -> Icons.Outlined.TaskAlt
    CalloutType.TIP -> Icons.Outlined.LocalFireDepartment
    CalloutType.SUCCESS -> Icons.Outlined.Check
    CalloutType.QUESTION -> Icons.AutoMirrored.Outlined.HelpOutline
    CalloutType.WARNING -> Icons.Outlined.WarningAmber
    CalloutType.FAILURE -> Icons.Outlined.Close
    CalloutType.DANGER -> Icons.Outlined.Bolt
    CalloutType.BUG -> Icons.Outlined.BugReport
    CalloutType.EXAMPLE -> Icons.AutoMirrored.Outlined.FormatListBulleted
    CalloutType.QUOTE -> Icons.Outlined.FormatQuote
}

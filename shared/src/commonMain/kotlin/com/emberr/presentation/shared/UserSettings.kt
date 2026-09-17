package com.emberr.presentation.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrDesktopMenuItem
import com.emberr.presentation.shared.components.EmberrDesktopMenuItems
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.cog
import emberr.shared.generated.resources.trash
import org.jetbrains.compose.resources.painterResource

@Composable
fun UserSettings(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrash: () -> Unit,
) {
    if (isDesktopPlatform) {
        EmberrDesktopMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            UserSettingsDesktopMenu(
                onDismiss = onDismiss,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToTrash = onNavigateToTrash,
            )
        }
    } else {
        UserSettingsBottomSheet(
            expanded = expanded,
            onDismiss = onDismiss,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToTrash = onNavigateToTrash
        )
    }
}

// Desktop Popup Menu
@Composable
private fun UserSettingsDesktopMenu(
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrash: () -> Unit,
) {
    EmberrDesktopMenuItems {

        EmberrDesktopMenuItem(
            text = "Settings",
            icon = painterResource(Res.drawable.cog),
            onClick = {
                onDismiss()
                onNavigateToSettings()
            }
        )

        EmberrDesktopMenuItem(
            text = "Trash",
            icon = painterResource(Res.drawable.trash),
            onClick = {
                onDismiss()
                onNavigateToTrash()
            }
        )
    }
}

// Mobile Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserSettingsBottomSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrash: () -> Unit
) {
    EmberrBottomSheet(expanded = expanded, onDismiss = onDismiss, title = "More") { closeAnd ->

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            BottomSheetItem(
                "Settings",
                painterResource(Res.drawable.cog)
            ) { closeAnd { onNavigateToSettings() } }

            BottomSheetItem(
                "Trash",
                painterResource(Res.drawable.trash)
            ) { closeAnd { onNavigateToTrash() } }

            EmberrButtonPrimary(
                text = "Close",
                onClick = { closeAnd(onDismiss) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun BottomSheetItem(text: String, icon: Painter, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
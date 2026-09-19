package com.emberr.presentation.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.NoRippleIndicationNodeFactory
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.cog
import emberr.shared.generated.resources.trash
import org.jetbrains.compose.resources.painterResource

private val MenuWidth = 220.dp

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
    Column(modifier = Modifier.width(MenuWidth).padding(vertical = 4.dp)) {

        UserSettingsMenuRow(
            text = "Settings",
            icon = painterResource(Res.drawable.cog),
            onClick = {
                onDismiss()
                onNavigateToSettings()
            }
        )

        UserSettingsMenuRow(
            text = "Trash",
            icon = painterResource(Res.drawable.trash),
            onClick = {
                onDismiss()
                onNavigateToTrash()
            }
        )
    }
}

@Composable
private fun UserSettingsMenuRow(
    text: String,
    icon: Painter,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
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
            UserSettingsSheetRow(
                text = "Settings",
                icon = painterResource(Res.drawable.cog)
            ) { closeAnd { onNavigateToSettings() } }

            UserSettingsSheetRow(
                text = "Trash",
                icon = painterResource(Res.drawable.trash)
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
private fun UserSettingsSheetRow(
    text: String,
    icon: Painter,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val baseColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val contentColor = if (enabled) baseColor else baseColor.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}

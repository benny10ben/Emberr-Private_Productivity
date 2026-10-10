package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.CalloutBlock
import com.emberr.domain.model.CalloutType
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrBottomSheetOption
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrDesktopMenuOption
import com.emberr.presentation.shared.components.EmberrSwitch

@Composable
fun CalloutOptionsMenu(
    expanded: Boolean,
    block: CalloutBlock,
    onDismiss: () -> Unit,
    onUpdateStyle: (calloutType: CalloutType, isFoldable: Boolean) -> Unit
) {
    val toggleFoldable = { onUpdateStyle(block.calloutType, !block.isFoldable) }
    val foldableSwitch: @Composable () -> Unit = { EmberrSwitch(isOn = block.isFoldable) }
    val calloutTypeIcon: @Composable (CalloutType) -> Unit = { calloutType ->
        Icon(
            imageVector = calloutType.icon(),
            contentDescription = null,
            tint = calloutType.accentColor(),
            modifier = Modifier.size(18.dp)
        )
    }

    if (isDesktopPlatform) {
        EmberrDesktopMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(240.dp)
        ) {
            EmberrDesktopMenuOption(label = "Foldable", onClick = toggleFoldable, trailing = foldableSwitch)
            CalloutType.entries.forEach { calloutType ->
                EmberrDesktopMenuOption(
                    label = calloutType.label,
                    isSelected = calloutType == block.calloutType,
                    icon = { calloutTypeIcon(calloutType) },
                    onClick = {
                        onUpdateStyle(calloutType, block.isFoldable)
                        onDismiss()
                    }
                )
            }
        }
    } else {
        EmberrBottomSheet(
            expanded = expanded,
            onDismiss = onDismiss,
            title = "Callout",
            contentHorizontalPadding = 0.dp
        ) { closeAnd ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                EmberrBottomSheetOption(label = "Foldable", onClick = toggleFoldable, trailing = foldableSwitch)
                CalloutType.entries.forEach { calloutType ->
                    EmberrBottomSheetOption(
                        label = calloutType.label,
                        isSelected = calloutType == block.calloutType,
                        icon = { calloutTypeIcon(calloutType) },
                        onClick = { closeAnd { onUpdateStyle(calloutType, block.isFoldable) } }
                    )
                }
            }
        }
    }
}

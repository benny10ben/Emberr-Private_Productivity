@file:OptIn(ExperimentalFoundationApi::class)

package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.valueTypeOf
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrBottomSheetOption
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrDesktopMenuOption
import com.emberr.presentation.shared.components.SheetBringIntoViewSpec
import com.emberr.presentation.shared.editor.blockViews.iconResource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.notes2
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val DesktopDatabaseMenuWidth = 260.dp
private val DesktopMenuVerticalPadding = 4.dp
private val SheetBottomPadding = 16.dp

internal val DatabaseMenuRowInset: Dp
    get() = if (isDesktopPlatform) 8.dp else 20.dp

internal val DatabaseMenuTextInset: Dp
    get() = if (isDesktopPlatform) 20.dp else 34.dp

internal fun DatabaseBlock.iconOf(column: DatabaseColumnTarget): DrawableResource = when (column) {
    DatabaseColumnTarget.NotesTitle -> Res.drawable.notes2
    is DatabaseColumnTarget.Property -> column.propertyType.iconResource()
    is DatabaseColumnTarget.CustomProperty -> (valueTypeOf(column) ?: PropertyValueType.TEXT).iconResource()
}

@Composable
internal fun DatabaseMenu(
    expanded: Boolean,
    title: String,
    onDismiss: () -> Unit,
    desktopWidth: Dp = DesktopDatabaseMenuWidth,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    if (isDesktopPlatform) {
        EmberrDesktopMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(desktopWidth)
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides SheetBringIntoViewSpec) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = DesktopMenuVerticalPadding)) {
                    content { action ->
                        action()
                        onDismiss()
                    }
                }
            }
        }
    } else {
        EmberrBottomSheet(
            expanded = expanded,
            onDismiss = onDismiss,
            title = title,
            contentHorizontalPadding = 0.dp
        ) { closeAnd ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = SheetBottomPadding)) {
                content(closeAnd)
            }
        }
    }
}

@Composable
internal fun DatabaseMenuLayer(
    title: String,
    anchor: @Composable (openLayer: () -> Unit) -> Unit,
    content: @Composable ColumnScope.(closeLayerAnd: (() -> Unit) -> Unit) -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }

    Box {
        anchor { isOpen = true }
        DatabaseMenu(expanded = isOpen, title = title, onDismiss = { isOpen = false }, content = content)
    }
}

@Composable
internal fun DatabaseMenuOption(
    label: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    labelColor: Color? = null
) {
    if (isDesktopPlatform) {
        EmberrDesktopMenuOption(
            label = label,
            onClick = onClick,
            isSelected = isSelected,
            icon = icon,
            trailing = trailing,
            labelColor = labelColor
        )
    } else {
        EmberrBottomSheetOption(
            label = label,
            onClick = onClick,
            isSelected = isSelected,
            icon = icon,
            trailing = trailing,
            labelColor = labelColor
        )
    }
}

@Composable
internal fun DatabaseOptionIcon(icon: DrawableResource, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
internal fun DatabaseMenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = DatabaseMenuTextInset, end = DatabaseMenuTextInset, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
internal fun DatabaseMenuMessage(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier.padding(horizontal = DatabaseMenuTextInset, vertical = 8.dp)
    )
}

@Composable
internal fun DatabaseMenuContent(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DatabaseMenuRowInset), content = content)
}

@Composable
internal fun DatabaseMenuButtons(
    cancelText: String,
    onCancel: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit
) {
    DatabaseMenuContent {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmberrButtonSecondary(text = cancelText, onClick = onCancel, modifier = Modifier.weight(1f))
            EmberrButtonPrimary(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

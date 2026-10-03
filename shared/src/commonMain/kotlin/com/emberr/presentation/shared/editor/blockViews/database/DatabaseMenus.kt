@file:OptIn(ExperimentalFoundationApi::class)

package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.emberr.presentation.shared.components.MenuAtTap
import com.emberr.presentation.shared.components.SheetBringIntoViewSpec
import com.emberr.presentation.shared.components.menuTapAnchor
import com.emberr.presentation.shared.components.rememberMenuTapAnchor
import com.emberr.presentation.shared.editor.blockViews.property.iconResource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.notes2
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal val DesktopDatabaseMenuMinWidth = 240.dp
internal val DesktopDatabaseMenuMaxWidth = 300.dp
private val DesktopMenuVerticalPadding = 4.dp
private val SheetBottomPadding = 16.dp
private val SheetOptionVerticalPadding = 10.dp
private val SheetActionRowShape = RoundedCornerShape(12.dp)
private val SheetActionRowSpacing = 2.dp
private val SheetActionRowIconGap = 12.dp

internal val DatabaseMenuRowInset: Dp
    get() = if (isDesktopPlatform) 8.dp else 20.dp

internal val DatabaseMenuTextInset: Dp = 20.dp

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
    desktopWidth: Dp? = null,
    showsCloseButton: Boolean = true,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    if (isDesktopPlatform) {
        EmberrDesktopMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = if (desktopWidth == null) {
                Modifier.widthIn(min = DesktopDatabaseMenuMinWidth, max = DesktopDatabaseMenuMaxWidth)
            } else {
                Modifier.width(desktopWidth)
            }
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
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = SheetBottomPadding)) {
                content(closeAnd)
                if (showsCloseButton) {
                    DatabaseMenuContent {
                        EmberrButtonPrimary(
                            text = "Close",
                            onClick = { closeAnd { } },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DatabaseMenuLayer(
    title: String,
    anchor: @Composable (openLayer: () -> Unit) -> Unit,
    desktopWidth: Dp? = null,
    showsCloseButton: Boolean = true,
    content: @Composable ColumnScope.(closeLayerAnd: (() -> Unit) -> Unit) -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }
    val tapAnchor = rememberMenuTapAnchor()

    Box(modifier = Modifier.menuTapAnchor(tapAnchor)) {
        anchor { isOpen = true }
        MenuAtTap(tapAnchor, menuWidth = desktopWidth ?: DesktopDatabaseMenuMaxWidth) {
            DatabaseMenu(
                expanded = isOpen,
                title = title,
                onDismiss = { isOpen = false },
                desktopWidth = desktopWidth,
                showsCloseButton = showsCloseButton,
                content = content
            )
        }
    }
}

@Composable
internal fun DatabaseMenuOption(
    label: String,
    onClick: () -> Unit,
    isSelected: Boolean? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    labelColor: Color? = null
) {
    val isPartOfAnOptionList = isSelected != null
    if (isDesktopPlatform) {
        EmberrDesktopMenuOption(
            label = label,
            onClick = onClick,
            isSelected = isSelected == true,
            icon = icon,
            trailing = trailing,
            labelColor = labelColor
        )
    } else if (!isPartOfAnOptionList) {
        DatabaseSheetActionRow(label = label, onClick = onClick, icon = icon, trailing = trailing, labelColor = labelColor)
    } else {
        EmberrBottomSheetOption(
            label = label,
            onClick = onClick,
            isSelected = isSelected,
            icon = icon,
            trailing = trailing,
            outerHorizontalPadding = DatabaseMenuRowInset,
            innerVerticalPadding = SheetOptionVerticalPadding,
            labelColor = labelColor
        )
    }
}

@Composable
private fun DatabaseSheetActionRow(
    label: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    labelColor: Color?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DatabaseMenuRowInset, vertical = SheetActionRowSpacing)
            .heightIn(min = 48.dp)
            .clip(SheetActionRowShape)
            .clickable(onClick = onClick)
            .padding(vertical = SheetOptionVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(SheetActionRowIconGap))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(SheetActionRowIconGap))
            trailing()
        }
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
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = DatabaseMenuTextInset, end = DatabaseMenuTextInset, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
internal fun DatabaseMenuSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = DatabaseMenuTextInset, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
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

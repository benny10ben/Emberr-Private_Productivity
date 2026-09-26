@file:OptIn(ExperimentalFoundationApi::class)

package com.emberr.presentation.properties

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.settings.SettingsActionRow
import com.emberr.presentation.settings.SettingsDivider
import com.emberr.presentation.settings.SettingsGroup
import com.emberr.presentation.settings.SettingsRowIcon
import com.emberr.presentation.settings.SettingsValuePill
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrBottomSheetOption
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrDesktopMenuOption
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.components.SheetBringIntoViewSpec
import com.emberr.presentation.shared.components.topHeaderBarPadding
import com.emberr.presentation.shared.editor.blockViews.iconResource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_right
import emberr.shared.generated.resources.plus
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

private val PropertiesPaneMaxWidth = 760.dp
private val PropertyEditorMenuWidth = 300.dp

private data class PropertyEditorState(
    val propertyId: String?,
    val originalName: String,
    val name: String,
    val valueType: PropertyValueType,
    val isConfirmingDelete: Boolean = false
)

@Composable
fun PropertiesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PropertiesViewModel = koinViewModel()
) {
    val customProperties by viewModel.customProperties.collectAsState()
    var editorState by remember { mutableStateOf<PropertyEditorState?>(null) }

    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }
    val backgroundColor = if (isDesktopPlatform) Color.Transparent else MaterialTheme.colorScheme.background
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val topEdgeFadeAlpha by remember { derivedStateOf { if (scrollState.value > 0) 1f else 0f } }

    fun saveEditor() {
        val state = editorState ?: return
        val cleanedName = state.name.trim()
        if (cleanedName.isEmpty() || viewModel.isNameTaken(cleanedName, state.propertyId)) return
        val propertyId = state.propertyId
        if (propertyId == null) {
            viewModel.createProperty(cleanedName, state.valueType)
        } else if (cleanedName != state.originalName) {
            viewModel.renameProperty(propertyId, cleanedName)
        }
        editorState = null
    }

    fun deleteEditedProperty() {
        val propertyId = editorState?.propertyId ?: return
        viewModel.deleteProperty(propertyId)
        editorState = null
    }

    val editorContent = @Composable { state: PropertyEditorState ->
        PropertyEditor(
            state = state,
            isNameTaken = viewModel.isNameTaken(state.name.trim(), state.propertyId),
            onNameChange = { editorState = state.copy(name = it) },
            onValueTypeChange = { editorState = state.copy(valueType = it) },
            onDeleteRequest = { editorState = state.copy(isConfirmingDelete = true) },
            onDeleteCancel = { editorState = state.copy(isConfirmingDelete = false) },
            onDeleteConfirm = { deleteEditedProperty() },
            onCancel = { editorState = null },
            onSave = { saveEditor() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(backgroundColor)
                .verticalScroll(scrollState)
                .padding(top = topBarHeightDp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = PropertiesPaneMaxWidth).fillMaxWidth()) {
                SettingsGroup(title = "Built-in") {
                    PropertyType.entries.forEachIndexed { index, propertyType ->
                        if (index > 0) SettingsDivider()
                        PropertyRow(
                            icon = propertyType.iconResource(),
                            name = propertyType.label,
                            valueTypeLabel = propertyType.valueType.label,
                            onClick = null
                        )
                    }
                }
                Text(
                    text = "Built-in properties can't be renamed or deleted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 22.dp, end = 16.dp, top = 8.dp)
                )

                SettingsGroup(title = "Custom") {
                    customProperties.forEach { property ->
                        Box {
                            PropertyRow(
                                icon = property.valueType.iconResource(),
                                name = property.name,
                                valueTypeLabel = property.valueType.label,
                                onClick = {
                                    editorState = PropertyEditorState(
                                        propertyId = property.propertyId,
                                        originalName = property.name,
                                        name = property.name,
                                        valueType = property.valueType
                                    )
                                }
                            )
                            val state = editorState
                            if (isDesktopPlatform && state != null && state.propertyId == property.propertyId) {
                                PropertyEditorMenu(onDismiss = { editorState = null }) { editorContent(state) }
                            }
                        }
                        SettingsDivider()
                    }
                    Box {
                        SettingsActionRow(
                            icon = painterResource(Res.drawable.plus),
                            title = "Add property",
                            onClick = {
                                editorState = PropertyEditorState(
                                    propertyId = null,
                                    originalName = "",
                                    name = "",
                                    valueType = PropertyValueType.TEXT
                                )
                            }
                        )
                        val state = editorState
                        if (isDesktopPlatform && state != null && state.propertyId == null) {
                            PropertyEditorMenu(onDismiss = { editorState = null }) { editorContent(state) }
                        }
                    }
                }
            }
        }

        EmberrVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = topBarHeightDp + 8.dp, bottom = 24.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> topBarHeightPx = coordinates.size.height.toFloat() }
        ) {
            EmberrTopHeaderBar(
                title = "Properties",
                hazeState = hazeState,
                contentPadding = topHeaderBarPadding(bottom = 16.dp),
                topEdgeFadeAlpha = topEdgeFadeAlpha,
                onBackClick = onNavigateBack
            )
        }

        if (!isDesktopPlatform) {
            val state = editorState
            EmberrBottomSheet(
                expanded = state != null,
                onDismiss = { editorState = null },
                title = if (state?.propertyId == null) "New Property" else "Edit Property"
            ) {
                if (state != null) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                        editorContent(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(
    icon: DrawableResource,
    name: String,
    valueTypeLabel: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon = painterResource(icon))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        SettingsValuePill(label = valueTypeLabel)
        if (onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(Res.drawable.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PropertyEditorMenu(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    EmberrDesktopMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(PropertyEditorMenuWidth)
    ) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides SheetBringIntoViewSpec) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PropertyEditor(
    state: PropertyEditorState,
    isNameTaken: Boolean,
    onNameChange: (String) -> Unit,
    onValueTypeChange: (PropertyValueType) -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val isNew = state.propertyId == null

    if (state.isConfirmingDelete) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Delete \"${state.originalName}\"? It will be removed from every note that uses it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmberrButtonSecondary(text = "Cancel", onClick = onDeleteCancel, modifier = Modifier.weight(1f))
                EmberrButtonPrimary(text = "Delete", onClick = onDeleteConfirm, modifier = Modifier.weight(1f))
            }
        }
    } else {
        val nameFocusRequester = remember { FocusRequester() }

        Column(modifier = Modifier.fillMaxWidth()) {
            EmberrTextField(
                value = state.name,
                onValueChange = onNameChange,
                placeholder = "Property name",
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester),
                onSubmit = onSave
            )
            if (isNameTaken) {
                Text(
                    text = "A property with this name already exists",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }

            Text(
                text = "Value type",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
            )
            if (isNew) {
                PropertyValueType.entries.forEach { valueType ->
                    val isSelected = valueType == state.valueType
                    val valueTypeIcon = @Composable {
                        Icon(
                            painter = painterResource(valueType.iconResource()),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (isDesktopPlatform) {
                        EmberrDesktopMenuOption(
                            label = valueType.label,
                            isSelected = isSelected,
                            onClick = { onValueTypeChange(valueType) },
                            icon = valueTypeIcon,
                            outerHorizontalPadding = 0.dp
                        )
                    } else {
                        EmberrBottomSheetOption(
                            label = valueType.label,
                            isSelected = isSelected,
                            onClick = { onValueTypeChange(valueType) },
                            icon = valueTypeIcon,
                            outerHorizontalPadding = 0.dp
                        )
                    }
                }
            } else {
                Text(
                    text = state.valueType.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Text(
                    text = "The value type can't be changed after the property is created.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
                TextButton(onClick = onDeleteRequest, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Delete property",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmberrButtonSecondary(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                EmberrButtonPrimary(
                    text = if (isNew) "Add" else "Save",
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = state.name.isNotBlank() && !isNameTaken
                )
            }
        }

        LaunchedEffect(Unit) {
            if (isDesktopPlatform) runCatching { nameFocusRequester.requestFocus() }
        }
    }
}

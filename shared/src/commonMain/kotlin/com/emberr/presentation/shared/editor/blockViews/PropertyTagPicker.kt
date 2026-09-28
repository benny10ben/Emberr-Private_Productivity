@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.cleanPropertyTagName
import com.emberr.domain.model.withPropertyTagReplaced
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrBottomSheetOption
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrDesktopMenuOption
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.MenuAtTap
import com.emberr.presentation.shared.components.NoRippleIndicationNodeFactory
import com.emberr.presentation.shared.components.SheetBringIntoViewSpec
import com.emberr.presentation.shared.components.menuTapAnchor
import com.emberr.presentation.shared.components.rememberMenuTapAnchor
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import com.emberr.ui.theme.HighlightColor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.check
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.trash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private val DesktopTagMenuWidth = 260.dp

fun propertyTagColor(tagName: String, isDarkTheme: Boolean, colorName: String? = null): Color {
    val colors = HighlightColor.entries
    val chosenColor = colorName?.let { name -> colors.firstOrNull { it.storageName == name } }
    return (chosenColor ?: colors[tagName.lowercase().hashCode().mod(colors.size)]).backgroundFor(isDarkTheme)
}

@Composable
fun PropertyTagChip(
    tagName: String,
    modifier: Modifier = Modifier,
    tagPoolKey: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Text(
        text = tagName,
        style = textStyle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(rememberPropertyTagColor(tagPoolKey, tagName))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun PropertyTagPicker(
    tagPoolKey: String,
    title: String,
    allowsManyTags: Boolean,
    selectedTags: List<String>,
    onDismiss: () -> Unit,
    onSelectedTagsChange: (List<String>) -> Unit
) {
    val noteRepository = koinInject<NoteRepository>()
    val savedTags by remember(tagPoolKey) { noteRepository.getPropertyTags(tagPoolKey) }
        .collectAsState(initial = emptyList())
    val appScope = koinInject<CoroutineScope>(qualifier = named("AppScope"))
    var searchText by remember { mutableStateOf("") }
    var tagBeingEdited by remember { mutableStateOf<String?>(null) }
    var editedTagName by remember { mutableStateOf("") }

    val allTagNames = (savedTags.map { it.name } + selectedTags).distinctBy { it.lowercase() }
    val cleanedSearch = cleanPropertyTagName(searchText)
    val matchingTagNames = allTagNames.filter { cleanedSearch.isEmpty() || it.contains(cleanedSearch, ignoreCase = true) }
    val canCreateTag = cleanedSearch.isNotEmpty() && allTagNames.none { it.equals(cleanedSearch, ignoreCase = true) }

    val cleanedEditedName = cleanPropertyTagName(editedTagName)
    val editedNameIsTaken = tagBeingEdited?.let { oldName ->
        allTagNames.any { !it.equals(oldName, ignoreCase = true) && it.equals(cleanedEditedName, ignoreCase = true) }
    } ?: false
    val canSaveRename = cleanedEditedName.isNotEmpty() && !editedNameIsTaken

    fun isSelected(tagName: String) = selectedTags.any { it.equals(tagName, ignoreCase = true) }

    fun toggleTag(tagName: String) {
        if (allowsManyTags) {
            val updatedTags = if (isSelected(tagName)) {
                selectedTags.filterNot { it.equals(tagName, ignoreCase = true) }
            } else {
                selectedTags + tagName
            }
            onSelectedTagsChange(updatedTags)
        } else {
            onSelectedTagsChange(if (isSelected(tagName)) emptyList() else listOf(tagName))
            onDismiss()
        }
    }

    fun createTagFromSearch() {
        val newTagName = cleanedSearch
        appScope.launch {
            try {
                noteRepository.createPropertyTag(tagPoolKey, newTagName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        searchText = ""
        toggleTag(newTagName)
    }

    fun openTagEditor(tagName: String) {
        tagBeingEdited = tagName
        editedTagName = tagName
    }

    fun closeTagEditor() {
        tagBeingEdited = null
    }

    fun changeTagInEveryNote(oldName: String, newName: String?) {
        ActiveEditorRegistry.rewriteBlocksInOpenEditors { block, now ->
            block.withPropertyTagReplaced(tagPoolKey, oldName, newName, now)
        }
        appScope.launch(Dispatchers.Main) {
            try {
                ActiveEditorRegistry.flushAllPending()
                if (newName == null) {
                    noteRepository.deletePropertyTag(tagPoolKey, oldName)
                } else {
                    noteRepository.renamePropertyTag(tagPoolKey, oldName, newName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun chooseTagColor(tagName: String, colorName: String?) {
        appScope.launch {
            try {
                noteRepository.setPropertyTagColor(tagPoolKey, tagName, colorName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveRename(closeEditorAnd: (() -> Unit) -> Unit) {
        val oldName = tagBeingEdited ?: return
        if (!canSaveRename) return
        val newName = cleanedEditedName
        closeEditorAnd {
            if (newName != oldName) changeTagInEveryNote(oldName, newName)
        }
    }

    fun deleteEditedTag(closeEditorAnd: (() -> Unit) -> Unit) {
        val tagName = tagBeingEdited ?: return
        closeEditorAnd { changeTagInEveryNote(tagName, null) }
    }

    fun submitSearch() {
        when {
            canCreateTag -> createTagFromSearch()
            matchingTagNames.isNotEmpty() -> {
                toggleTag(matchingTagNames.first())
                searchText = ""
            }
        }
    }

    val tagListContent = @Composable { searchFieldModifier: Modifier ->
        Column(modifier = Modifier.fillMaxWidth()) {
            EmberrTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "Search or create...",
                modifier = searchFieldModifier.fillMaxWidth(),
                onSubmit = { submitSearch() }
            )
            Spacer(modifier = Modifier.height(8.dp))

            matchingTagNames.forEach { tagName ->
                val tapAnchor = rememberMenuTapAnchor()
                Box(modifier = Modifier.menuTapAnchor(tapAnchor)) {
                    PropertyTagOptionRow(
                        tagName = tagName,
                        tagPoolKey = tagPoolKey,
                        isSelected = isSelected(tagName),
                        onClick = { toggleTag(tagName) },
                        onEdit = { openTagEditor(tagName) }
                    )
                    if (tagName == tagBeingEdited) {
                        MenuAtTap(tapAnchor, menuWidth = DesktopTagMenuWidth) {
                            PropertyTagLayer(
                                title = "Edit tag",
                                onDismiss = { closeTagEditor() }
                            ) { closeEditorAnd ->
                                EditTagContent(
                                    originalTagName = tagName,
                                    colorName = savedTags.firstOrNull { it.name.equals(tagName, ignoreCase = true) }?.colorName,
                                    onColorChosen = { colorName -> chooseTagColor(tagName, colorName) },
                                    tagName = editedTagName,
                                    onTagNameChange = { editedTagName = it },
                                    isNameTaken = editedNameIsTaken,
                                    canSave = canSaveRename,
                                    onSave = { saveRename(closeEditorAnd) },
                                    onCancel = { closeEditorAnd { } },
                                    onDeleteConfirmed = { deleteEditedTag(closeEditorAnd) }
                                )
                            }
                        }
                    }
                }
            }

            if (canCreateTag) {
                CreatePropertyTagRow(tagName = cleanedSearch, onClick = { createTagFromSearch() })
            }

            if (matchingTagNames.isEmpty() && !canCreateTag) {
                Text(
                    text = "Type a name to create one",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
        }
    }

    PropertyTagLayer(title = title, onDismiss = onDismiss) { closeAnd ->
        if (isDesktopPlatform) {
            val searchFocusRequester = remember { FocusRequester() }
            tagListContent(Modifier.focusRequester(searchFocusRequester))
            LaunchedEffect(Unit) {
                runCatching { searchFocusRequester.requestFocus() }
            }
        } else {
            CompositionLocalProvider(
                LocalIndication provides NoRippleIndicationNodeFactory,
                LocalRippleConfiguration provides null
            ) {
                tagListContent(Modifier)
                EmberrButtonPrimary(
                    text = "Done",
                    onClick = { closeAnd(onDismiss) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun PropertyTagLayer(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    if (isDesktopPlatform) {
        EmberrDesktopMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(DesktopTagMenuWidth)
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides SheetBringIntoViewSpec) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    content { action ->
                        action()
                        onDismiss()
                    }
                }
            }
        }
    } else {
        EmberrBottomSheet(expanded = true, onDismiss = onDismiss, title = title) { closeAnd ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                content(closeAnd)
            }
        }
    }
}

@Composable
private fun PropertyTagOptionRow(tagName: String, tagPoolKey: String, isSelected: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isDesktopPlatform) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            PropertyTagChip(tagName = tagName, tagPoolKey = tagPoolKey)
        }
        if (isSelected) {
            Icon(
                painter = painterResource(Res.drawable.check),
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }
        Icon(
            painter = painterResource(Res.drawable.pen),
            contentDescription = "Edit $tagName",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(CircleShape)
                .clickable(onClick = onEdit)
                .padding(4.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun EditTagContent(
    originalTagName: String,
    colorName: String?,
    onColorChosen: (String?) -> Unit,
    tagName: String,
    onTagNameChange: (String) -> Unit,
    isNameTaken: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDeleteConfirmed: () -> Unit
) {
    val nameFocusRequester = remember { FocusRequester() }
    var isConfirmingDelete by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        EmberrTextField(
            value = tagName,
            onValueChange = onTagNameChange,
            placeholder = "Tag name",
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
            onSubmit = onSave
        )
        if (isNameTaken) {
            Text(
                text = "A tag with this name already exists",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
        Text(
            text = "Color",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
        )
        PropertyTagColorChoices(
            tagName = originalTagName,
            selectedColorName = colorName,
            onColorChosen = onColorChosen,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        val deleteTapAnchor = rememberMenuTapAnchor()
        Box(modifier = Modifier.padding(top = 8.dp).menuTapAnchor(deleteTapAnchor)) {
            DeleteTagOption(onClick = { isConfirmingDelete = true })
            if (isConfirmingDelete) {
                MenuAtTap(deleteTapAnchor, menuWidth = DesktopTagMenuWidth) {
                    PropertyTagLayer(
                        title = "Delete tag",
                        onDismiss = { isConfirmingDelete = false }
                    ) { closeConfirmationAnd ->
                        DeleteTagConfirmation(
                            tagName = originalTagName,
                            onCancel = { closeConfirmationAnd { } },
                            onDelete = { closeConfirmationAnd(onDeleteConfirmed) }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmberrButtonSecondary(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            EmberrButtonPrimary(text = "Save", onClick = onSave, modifier = Modifier.weight(1f), enabled = canSave)
        }
    }

    LaunchedEffect(Unit) {
        if (isDesktopPlatform) runCatching { nameFocusRequester.requestFocus() }
    }
}

@Composable
private fun DeleteTagOption(onClick: () -> Unit) {
    val deleteIcon = @Composable {
        Icon(
            painter = painterResource(Res.drawable.trash),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
    }
    if (isDesktopPlatform) {
        EmberrDesktopMenuOption(
            label = "Delete tag",
            onClick = onClick,
            icon = deleteIcon,
            outerHorizontalPadding = 0.dp,
            labelColor = MaterialTheme.colorScheme.error
        )
    } else {
        EmberrBottomSheetOption(
            label = "Delete tag",
            onClick = onClick,
            icon = deleteIcon,
            outerHorizontalPadding = 0.dp,
            labelColor = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DeleteTagConfirmation(tagName: String, onCancel: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Delete \"$tagName\"? It will be removed from every note that uses it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmberrButtonSecondary(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            EmberrButtonPrimary(text = "Delete", onClick = onDelete, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CreatePropertyTagRow(tagName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isDesktopPlatform) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.plus),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Create",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        PropertyTagChip(tagName = tagName)
    }
}

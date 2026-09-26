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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.cleanPropertyTagName
import com.emberr.domain.model.withPropertyTagReplaced
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.NoRippleIndicationNodeFactory
import com.emberr.presentation.shared.components.SheetBringIntoViewSpec
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import com.emberr.ui.theme.HighlightColor
import com.emberr.ui.theme.LocalAppIsDark
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.check
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private val DesktopTagMenuWidth = 260.dp

fun propertyTagColor(tagName: String, isDarkTheme: Boolean): Color {
    val colors = HighlightColor.entries
    return colors[tagName.lowercase().hashCode().mod(colors.size)].backgroundFor(isDarkTheme)
}

@Composable
fun PropertyTagChip(tagName: String, modifier: Modifier = Modifier) {
    Text(
        text = tagName,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(propertyTagColor(tagName, LocalAppIsDark.current))
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
    var isConfirmingDelete by remember { mutableStateOf(false) }

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
        isConfirmingDelete = false
    }

    fun closeTagEditor() {
        tagBeingEdited = null
        isConfirmingDelete = false
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

    fun saveRename() {
        val oldName = tagBeingEdited ?: return
        if (!canSaveRename) return
        if (cleanedEditedName != oldName) changeTagInEveryNote(oldName, cleanedEditedName)
        closeTagEditor()
    }

    fun deleteEditedTag() {
        val tagName = tagBeingEdited ?: return
        changeTagInEveryNote(tagName, null)
        closeTagEditor()
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
                PropertyTagOptionRow(
                    tagName = tagName,
                    isSelected = isSelected(tagName),
                    onClick = { toggleTag(tagName) },
                    onEdit = { openTagEditor(tagName) }
                )
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

    val pickerContent = @Composable { searchFieldModifier: Modifier ->
        val editingTag = tagBeingEdited
        when {
            editingTag != null && isConfirmingDelete -> DeleteTagConfirmation(
                tagName = editingTag,
                onCancel = { isConfirmingDelete = false },
                onDelete = { deleteEditedTag() }
            )
            editingTag != null -> EditTagContent(
                tagName = editedTagName,
                onTagNameChange = { editedTagName = it },
                isNameTaken = editedNameIsTaken,
                canSave = canSaveRename,
                onSave = { saveRename() },
                onCancel = { closeTagEditor() },
                onDelete = { isConfirmingDelete = true }
            )
            else -> tagListContent(searchFieldModifier)
        }
    }

    if (isDesktopPlatform) {
        val searchFocusRequester = remember { FocusRequester() }
        EmberrDesktopMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(DesktopTagMenuWidth)
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides SheetBringIntoViewSpec) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    pickerContent(Modifier.focusRequester(searchFocusRequester))
                }
            }
            LaunchedEffect(Unit) {
                runCatching { searchFocusRequester.requestFocus() }
            }
        }
    } else {
        EmberrBottomSheet(expanded = true, onDismiss = onDismiss, title = title) { closeAnd ->
            CompositionLocalProvider(
                LocalIndication provides NoRippleIndicationNodeFactory,
                LocalRippleConfiguration provides null
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    pickerContent(Modifier)
                    if (tagBeingEdited == null) {
                        EmberrButtonPrimary(
                            text = "Done",
                            onClick = { closeAnd(onDismiss) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyTagOptionRow(tagName: String, isSelected: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            PropertyTagChip(tagName = tagName)
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
    tagName: String,
    onTagNameChange: (String) -> Unit,
    isNameTaken: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val nameFocusRequester = remember { FocusRequester() }

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
        TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "Delete tag",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
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

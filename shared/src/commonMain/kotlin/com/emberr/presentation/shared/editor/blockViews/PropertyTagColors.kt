package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emberr.domain.repository.NoteRepository
import com.emberr.ui.theme.HighlightColor
import com.emberr.ui.theme.LocalAppIsDark
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.check
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

class PropertyTagColorStore(repository: NoteRepository, appScope: CoroutineScope) {
    val colorNamesByTag: StateFlow<Map<String, String>> = repository.getAllPropertyTags()
        .map { tags ->
            tags.mapNotNull { tag -> tag.colorName?.let { colorName -> propertyTagColorKey(tag.propertyKey, tag.name) to colorName } }.toMap()
        }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

fun propertyTagColorKey(tagPoolKey: String, tagName: String): String = "$tagPoolKey:${tagName.lowercase()}"

@Composable
fun rememberPropertyTagColorName(tagPoolKey: String?, tagName: String): String? {
    if (tagPoolKey == null) return null
    val colorStore = koinInject<PropertyTagColorStore>()
    val colorNamesByTag by colorStore.colorNamesByTag.collectAsState()
    return colorNamesByTag[propertyTagColorKey(tagPoolKey, tagName)]
}

@Composable
fun rememberPropertyTagColor(tagPoolKey: String?, tagName: String): Color =
    propertyTagColor(tagName, LocalAppIsDark.current, rememberPropertyTagColorName(tagPoolKey, tagName))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertyTagColorChoices(
    tagName: String,
    selectedColorName: String?,
    onColorChosen: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalAppIsDark.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PropertyTagColorSwatch(
            color = propertyTagColor(tagName, isDarkTheme),
            label = "Automatic",
            isSelected = selectedColorName == null,
            isAutomatic = true,
            onClick = { onColorChosen(null) }
        )
        HighlightColor.entries.forEach { highlightColor ->
            PropertyTagColorSwatch(
                color = highlightColor.backgroundFor(isDarkTheme),
                label = highlightColor.displayName,
                isSelected = selectedColorName == highlightColor.storageName,
                isAutomatic = false,
                onClick = { onColorChosen(highlightColor.storageName) }
            )
        }
    }
}

@Composable
private fun PropertyTagColorSwatch(color: Color, label: String, isSelected: Boolean, isAutomatic: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 0.6.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        when {
            isSelected -> Icon(
                painter = painterResource(Res.drawable.check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
            isAutomatic -> Text(text = "A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

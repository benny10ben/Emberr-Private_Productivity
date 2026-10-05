package com.emberr.presentation.settings.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.presentation.settings.SettingsGroupTitle
import com.emberr.presentation.settings.SettingsValuePill
import com.emberr.presentation.settings.desktopSettingsSidePadding
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.components.topHeaderBarPadding
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private const val LIBRARY_DEFINITIONS_PATH = "files/aboutlibraries.json"
private const val FONT_ID_PREFIX = "font:"
private val LibraryCardCornerRadius = 18.dp

@Composable
fun OpenSourceLicensesScreen(onNavigateBack: () -> Unit) {
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    val libraries by produceState<List<Library>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.Default) {
            val definitionsJson = Res.readBytes(LIBRARY_DEFINITIONS_PATH).decodeToString()
            Libs.Builder().withJson(definitionsJson).build().libraries
        }
    }
    val (fonts, codeLibraries) = remember(libraries) {
        libraries.partition { it.uniqueId.startsWith(FONT_ID_PREFIX) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePadding = desktopSettingsSidePadding(maxWidth)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = sidePadding,
                top = topBarHeightDp + 8.dp,
                end = sidePadding,
                bottom = 48.dp
            )
        ) {
            libraryGroup(title = "Libraries", libraries = codeLibraries, onLibraryClick = { selectedLibrary = it })
            libraryGroup(title = "Fonts", libraries = fonts, onLibraryClick = { selectedLibrary = it })
        }

        EmberrVerticalScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = topBarHeightDp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> topBarHeightPx = coordinates.size.height.toFloat() }
        ) {
            EmberrTopHeaderBar(
                title = "Open-Source Licenses",
                hazeState = hazeState,
                contentPadding = topHeaderBarPadding(bottom = 16.dp),
                onBackClick = onNavigateBack
            )
        }
    }

    selectedLibrary?.let { library ->
        LibraryLicenseSheet(library = library, onDismiss = { selectedLibrary = null })
    }
}

private fun LazyListScope.libraryGroup(
    title: String,
    libraries: List<Library>,
    onLibraryClick: (Library) -> Unit
) {
    if (libraries.isEmpty()) return

    item(key = "group_title_$title") {
        Box(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 22.dp)) {
            SettingsGroupTitle(title = title)
        }
    }

    itemsIndexed(libraries, key = { _, library -> library.uniqueId }) { index, library ->
        LibraryRow(
            library = library,
            isFirstInGroup = index == 0,
            isLastInGroup = index == libraries.lastIndex,
            onClick = { onLibraryClick(library) }
        )
    }
}

@Composable
private fun LibraryRow(
    library: Library,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onClick: () -> Unit
) {
    val topCornerRadius = if (isFirstInGroup) LibraryCardCornerRadius else 0.dp
    val bottomCornerRadius = if (isLastInGroup) LibraryCardCornerRadius else 0.dp
    val rowShape = RoundedCornerShape(
        topStart = topCornerRadius,
        topEnd = topCornerRadius,
        bottomStart = bottomCornerRadius,
        bottomEnd = bottomCornerRadius
    )

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(rowShape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (!isFirstInGroup) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val licenseNames = remember(library) {
                    library.licensesToShow().map { it.name }.filter { it.isNotBlank() }.distinct()
                }
                if (licenseNames.isNotEmpty()) {
                    Text(
                        text = licenseNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            library.artifactVersion?.let { version ->
                Spacer(modifier = Modifier.width(8.dp))
                SettingsValuePill(label = version)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                painterResource(Res.drawable.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LibraryLicenseSheet(library: Library, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val authorNames = library.organization?.name?.takeIf { it.isNotBlank() }
        ?: library.developers.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }.joinToString(", ")
    val sheetSubtitle = listOfNotNull(
        library.artifactVersion?.let { "Version $it" },
        authorNames.takeIf { it.isNotBlank() }?.let { "By $it" }
    ).joinToString(" · ").takeIf { it.isNotBlank() }

    EmberrBottomSheet(
        expanded = true,
        onDismiss = onDismiss,
        title = library.name,
        subtitle = sheetSubtitle
    ) { _ ->
        library.licensesToShow().forEach { license ->
            Text(
                text = license.name.ifBlank { "License" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = license.licenseContent?.takeIf { it.isNotBlank() }
                    ?: "The full text is available at ${license.url.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        library.website?.takeIf { it.isNotBlank() }?.let { website ->
            EmberrButtonSecondary(
                text = "Visit Website",
                onClick = { uriHandler.openUri(website) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
        }
    }
}

internal fun Library.licensesToShow(): List<License> {
    val readableLicenses = licenses.filter { license ->
        !license.licenseContent.isNullOrBlank() || (license.name.isNotBlank() && !license.url.isNullOrBlank())
    }
    return readableLicenses.filterNot { license ->
        val isGenericTemplate = license.spdxId != null && license.hash == license.spdxId
        isGenericTemplate && readableLicenses.any { other -> other.hash != license.hash && other.spdxId == license.spdxId }
    }
}

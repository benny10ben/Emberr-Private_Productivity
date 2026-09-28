package com.emberr.presentation.shared.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.util.system.isDesktopPlatform
import org.koin.compose.koinInject

@Composable
fun rememberScrollbarsVisible(): Boolean {
    if (!isDesktopPlatform) return false
    val settingsManager = koinInject<SettingsManager>()
    val isVisible by settingsManager.showScrollbarFlow.collectAsState(
        initial = settingsManager.isShowScrollbarEnabled()
    )
    return isVisible
}

@Composable
fun EmberrVerticalScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    if (!rememberScrollbarsVisible()) return
    PlatformVerticalScrollbar(listState, modifier)
}

@Composable
fun EmberrVerticalScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    if (!rememberScrollbarsVisible()) return
    PlatformVerticalScrollbar(scrollState, modifier)
}

@Composable
fun EmberrVerticalScrollbar(gridState: LazyGridState, modifier: Modifier = Modifier) {
    if (!rememberScrollbarsVisible()) return
    PlatformVerticalScrollbar(gridState, modifier)
}

@Composable
fun EmberrHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    showsWhenScrollbarsAreOff: Boolean = false
) {
    val isVisible = if (showsWhenScrollbarsAreOff) isDesktopPlatform else rememberScrollbarsVisible()
    if (!isVisible) return
    PlatformHorizontalScrollbar(scrollState, modifier)
}

@Composable
internal expect fun PlatformVerticalScrollbar(listState: LazyListState, modifier: Modifier)

@Composable
internal expect fun PlatformVerticalScrollbar(scrollState: ScrollState, modifier: Modifier)

@Composable
internal expect fun PlatformVerticalScrollbar(gridState: LazyGridState, modifier: Modifier)

@Composable
internal expect fun PlatformHorizontalScrollbar(scrollState: ScrollState, modifier: Modifier)

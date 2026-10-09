package com.emberr.presentation.settings.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.domain.util.system.appVersionName
import com.emberr.presentation.settings.SettingsActionRow
import com.emberr.presentation.settings.SettingsDivider
import com.emberr.presentation.settings.SettingsGroup
import com.emberr.presentation.settings.desktopSettingsSidePadding
import com.emberr.presentation.shared.components.EmberrGhost
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.topHeaderBarPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.code
import emberr.shared.generated.resources.doc_text
import emberr.shared.generated.resources.files
import org.jetbrains.compose.resources.painterResource

private const val SOURCE_CODE_URL = "https://github.com/emberr-app/Emberr"
private const val AGPL_LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.html"

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit
) {
    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }
    val uriHandler = LocalUriHandler.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePadding = desktopSettingsSidePadding(maxWidth)
        LazyColumn(
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
            item(key = "emberr_ghost") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    EmberrGhost(modifier = Modifier.size(88.dp))
                }
            }
            item(key = "emberr_notice") {
                SettingsGroup(title = "Emberr") {
                    AppLicenseNotice()
                    SettingsDivider()
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.code),
                        title = "Source Code",
                        onClick = { uriHandler.openUri(SOURCE_CODE_URL) }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.doc_text),
                        title = "GNU AGPL v3.0",
                        onClick = { uriHandler.openUri(AGPL_LICENSE_URL) }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.files),
                        title = "Open-Source Licenses",
                        onClick = onOpenSourceLicensesClick
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> topBarHeightPx = coordinates.size.height.toFloat() }
        ) {
            EmberrTopHeaderBar(
                title = "About",
                hazeState = hazeState,
                contentPadding = topHeaderBarPadding(bottom = 16.dp),
                onBackClick = onNavigateBack
            )
        }
    }
}

@Composable
private fun AppLicenseNotice() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = listOfNotNull("Emberr", appVersionName?.let { "v$it" }).joinToString(" "),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Open-source, offline-first notes and productivity app",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = "Copyright © 2026 Benny",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        Text(
            text = "Licensed under the GNU AGPL v3.0. Provided without any warranty.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

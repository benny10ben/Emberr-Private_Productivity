package com.emberr.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.emberr.domain.sync.SyncPairingData
import com.emberr.domain.sync.SyncServerStatus
import com.emberr.domain.util.system.AppPermission
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.domain.util.system.restartApplication
import com.emberr.domain.util.system.rememberAppPermissionCoordinator
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrAlertDialog
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.components.EmberrBottomSheetOption
import com.emberr.presentation.shared.components.SelectedOptionBackground
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.topHeaderBarPadding
import com.emberr.presentation.sync.SyncPairingDialog
import com.emberr.presentation.sync.SyncScannerDialog
import com.emberr.presentation.sync.SyncViewModel
import com.emberr.domain.util.system.showNativeToast
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.emberr.ui.theme.FontSizePreference
import com.emberr.ui.theme.FontStylePreference
import com.emberr.ui.theme.ThemePreference
import com.emberr.ui.theme.fontFamilyFor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.astroid
import emberr.shared.generated.resources.badge_plus
import emberr.shared.generated.resources.badge_question_mark
import emberr.shared.generated.resources.bell
import emberr.shared.generated.resources.calendar_clock
import emberr.shared.generated.resources.chevron_right
import emberr.shared.generated.resources.file_down
import emberr.shared.generated.resources.files
import emberr.shared.generated.resources.folder_input
import emberr.shared.generated.resources.folder_sync
import emberr.shared.generated.resources.info
import emberr.shared.generated.resources.palette
import emberr.shared.generated.resources.qr_code
import emberr.shared.generated.resources.refresh_cw
import emberr.shared.generated.resources.scan_line
import emberr.shared.generated.resources.shield_alert
import emberr.shared.generated.resources.sidebar
import emberr.shared.generated.resources.sliders_horizontal
import emberr.shared.generated.resources.text_type
import emberr.shared.generated.resources.timer_reset
import emberr.shared.generated.resources.triangle_alert
import com.emberr.presentation.shared.SubNoteOpenMode
import com.emberr.presentation.shared.TopBarFadeStyle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

private val SettingsCardShape = RoundedCornerShape(18.dp)
private val SettingsRowIconShape = RoundedCornerShape(14.dp)
private val SettingsSidebarWidth = 244.dp
private val SettingsPaneMaxWidth = 760.dp
private val BackupFrequencies = listOf("Hourly", "Daily", "Weekly")
private val FontSizeOptions = listOf(
    FontSizePreference.SMALL.name to "Small",
    FontSizePreference.DEFAULT.name to "Default",
    FontSizePreference.LARGE.name to "Large"
)

private class SettingsCategory(
    val title: String,
    val icon: DrawableResource,
    val isDestructive: Boolean = false,
    val content: @Composable () -> Unit
)

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onImportClick: () -> Unit = {},
    onExportReady: () -> Unit = {},
    onRequestBackupFolder: () -> Unit = {},
    onNavigateToSelfHostSetup: () -> Unit = {},
    showBackButton: Boolean = true,
    viewModel: SettingsViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel()
) {
    var showImportExportSheet by remember { mutableStateOf(false) }

    val isPaired by syncViewModel.isPaired.collectAsState()
    val syncStatus by syncViewModel.syncStatus.collectAsState()
    val serverStatus = syncViewModel.serverStatus?.collectAsState()?.value
    val serverUnavailableReason = (serverStatus as? SyncServerStatus.Unavailable)?.reason
    var showPairingDialog by remember { mutableStateOf(false) }
    var activePairingData by remember { mutableStateOf<SyncPairingData?>(null) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showUnpairConfirmation by remember { mutableStateOf(false) }
    var syncPortInput by remember { mutableStateOf(syncViewModel.getSyncPort().toString()) }

    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val backupFrequency by viewModel.backupFrequency.collectAsState()
    val backupDirectoryUri by viewModel.backupDirectoryUri.collectAsState()

    val backupTime by viewModel.backupTime.collectAsState()
    val backupDay by viewModel.backupDay.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }

    val themePreference by viewModel.themePreference.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }

    val fontSizePreference by viewModel.fontSizePreference.collectAsState()
    val fontStylePreference by viewModel.fontStylePreference.collectAsState()
    var showFontSizeSheet by remember { mutableStateOf(false) }
    var showFontStyleSheet by remember { mutableStateOf(false) }

    val topBarFadeStyle by viewModel.topBarFadeStyle.collectAsState()
    var showTopBarFadeStyleSheet by remember { mutableStateOf(false) }

    val subNoteOpenMode by viewModel.subNoteOpenMode.collectAsState()
    var showSubNoteOpenModeSheet by remember { mutableStateOf(false) }

    val showScrollbar by viewModel.showScrollbar.collectAsState()
    val customWindowFrameEnabled by viewModel.customWindowFrameEnabled.collectAsState()
    val autoHideTitleBar by viewModel.autoHideTitleBar.collectAsState()

    val aiFeaturesDisabled by viewModel.aiFeaturesDisabled.collectAsState()
    val isPurgingAiData by viewModel.isPurgingAiData.collectAsState()
    val aiPurgeResultMessage by viewModel.aiPurgeResultMessage.collectAsState()
    var showDisableAiConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(aiPurgeResultMessage) {
        aiPurgeResultMessage?.let { message ->
            showNativeToast(message)
            viewModel.consumeAiPurgeResultMessage()
        }
    }

    LaunchedEffect(syncStatus) {
        if (syncStatus != "Idle" && syncStatus != "Syncing...") {
            showNativeToast(syncStatus)
            syncViewModel.resetSyncStatus()
        }
    }

    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }

    val categories = buildList {
        if (!isDesktopPlatform) {
            add(
                SettingsCategory(title = "Reminders", icon = Res.drawable.bell) {
                    RemindersSettingsSection()
                }
            )
        }

        add(
            SettingsCategory(title = "Data", icon = Res.drawable.files) {
                DataSettingsSection(
                    autoBackupEnabled = autoBackupEnabled,
                    backupFrequency = backupFrequency,
                    backupTime = backupTime,
                    backupDay = backupDay,
                    onImportExportClick = { showImportExportSheet = true },
                    onAutoBackupChange = { isChecked ->
                        when {
                            !isChecked -> viewModel.setAutoBackupEnabled(false)
                            backupDirectoryUri == null -> onRequestBackupFolder()
                            else -> viewModel.setAutoBackupEnabled(true)
                        }
                    },
                    onBackupFrequencySelected = { frequency ->
                        viewModel.saveBackupSchedule(frequency, backupTime, backupDay)
                    },
                    onPickBackupDay = { showDayPicker = true },
                    onPickBackupTime = { showTimePicker = true },
                    onChooseBackupFolder = onRequestBackupFolder
                )
            }
        )

        add(
            SettingsCategory(title = "Sync", icon = Res.drawable.folder_sync) {
                SyncSettingsSection(
                    isPaired = isPaired,
                    syncStatus = syncStatus,
                    serverUnavailableReason = serverUnavailableReason,
                    syncPortInput = syncPortInput,
                    onSyncPortChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }.take(5)
                        syncPortInput = digitsOnly
                        val port = digitsOnly.toIntOrNull()
                        if (port != null && port in 1024..65535) {
                            syncViewModel.setSyncPort(port)
                        }
                    },
                    onOpenSelfHostSetup = onNavigateToSelfHostSetup,
                    onSyncNow = { syncViewModel.triggerManualSync() },
                    onPairMobileDevice = {
                        syncViewModel.startServerForPairing()
                        activePairingData = syncViewModel.generatePairingData()
                        showPairingDialog = true
                    },
                    onPairWithDesktop = { showScannerDialog = true },
                    onUnpair = { showUnpairConfirmation = true }
                )
            }
        )

        if (isDesktopPlatform) {
            add(
                SettingsCategory(title = "Security", icon = Res.drawable.shield_alert) {
                    SecretStorageSettingsSection()
                }
            )
        }

        add(
            SettingsCategory(title = "Appearance", icon = Res.drawable.palette) {
                AppearanceSettingsSection(
                    themeLabel = runCatching { ThemePreference.valueOf(themePreference) }
                        .getOrDefault(ThemePreference.SYSTEM).displayName,
                    fontSizeLabel = fontSizeDisplayNameFor(fontSizePreference),
                    fontStyleLabel = runCatching { FontStylePreference.valueOf(fontStylePreference) }
                        .getOrDefault(FontStylePreference.POPPINS).displayName,
                    subNoteOpenModeLabel = runCatching { SubNoteOpenMode.valueOf(subNoteOpenMode) }
                        .getOrDefault(SubNoteOpenMode.SIDE_PANEL).displayName,
                    topBarFadeStyleLabel = topBarFadeStyleFor(topBarFadeStyle).displayName,
                    showScrollbar = showScrollbar,
                    customWindowFrameEnabled = customWindowFrameEnabled,
                    autoHideTitleBar = autoHideTitleBar,
                    onThemeClick = { showThemeSheet = true },
                    onFontSizeClick = { showFontSizeSheet = true },
                    onFontStyleClick = { showFontStyleSheet = true },
                    onSubNoteOpenModeClick = { showSubNoteOpenModeSheet = true },
                    onTopBarFadeStyleClick = { showTopBarFadeStyleSheet = true },
                    onShowScrollbarChange = { viewModel.setShowScrollbar(it) },
                    onCustomWindowFrameChange = { viewModel.setCustomWindowFrameEnabled(it) },
                    onAutoHideTitleBarChange = { viewModel.setAutoHideTitleBar(it) }
                )
            }
        )

        add(
            SettingsCategory(title = "AI", icon = Res.drawable.astroid) {
                AiSettingsSection(
                    aiFeaturesDisabled = aiFeaturesDisabled,
                    isPurgingAiData = isPurgingAiData,
                    onDisableRequested = { showDisableAiConfirmation = true },
                    onEnableRequested = { viewModel.setAiFeaturesDisabled(false) }
                )
            }
        )

        add(
            SettingsCategory(title = "Help", icon = Res.drawable.badge_question_mark) {
                HelpSettingsSection()
            }
        )

        add(
            SettingsCategory(
                title = "Danger Zone",
                icon = Res.drawable.triangle_alert,
                isDestructive = true
            ) {
                DangerZoneSettingsSection(onClearAllData = {})
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val surfaceModifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .background(MaterialTheme.colorScheme.background)

        if (isDesktopPlatform) {
            DesktopSettingsPanes(
                categories = categories,
                contentTopPadding = topBarHeightDp,
                modifier = surfaceModifier
            )
        } else {
            MobileSettingsList(
                categories = categories,
                listState = listState,
                contentTopPadding = topBarHeightDp + 8.dp,
                modifier = surfaceModifier
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> topBarHeightPx = coordinates.size.height.toFloat() }
        ) {
            EmberrTopHeaderBar(
                title = "Settings",
                hazeState = hazeState,
                contentPadding = topHeaderBarPadding(bottom = 16.dp),
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        }

        if (showImportExportSheet) {
            EmberrBottomSheet(
                expanded = true,
                onDismiss = { showImportExportSheet = false },
                title = "Import / Export",
                subtitle = "Backup your data securely to a local file, or restore a previous backup."
            ) { closeAnd ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmberrButtonPrimary(
                        text = "Export",
                        onClick = {
                            showImportExportSheet = false
                            onExportReady()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    EmberrButtonSecondary(
                        text = "Import",
                        onClick = {
                            showImportExportSheet = false
                            onImportClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    EmberrButtonPrimary(
                        text = "Close",
                        onClick = { closeAnd { showImportExportSheet = false } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        val timeParts = backupTime.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 2
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
        }

        com.emberr.presentation.shared.components.MinimalTimePickerDialog(
            expanded = showTimePicker,
            initialTimestamp = cal.timeInMillis,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                val formattedTime = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
                viewModel.saveBackupSchedule(backupFrequency, formattedTime, backupDay)
            }
        )
    }

    if (showDayPicker) {
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        var selectedIndex by remember { mutableIntStateOf(days.indexOf(backupDay).coerceAtLeast(0)) }

        val wheelContent = @Composable {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                com.emberr.presentation.shared.components.WheelPicker(
                    items = days,
                    selectedIndex = selectedIndex,
                    onItemSelected = { selectedIndex = it },
                    itemHeight = if (isDesktopPlatform) 40.dp else 44.dp
                )
            }
        }

        if (isDesktopPlatform) {
            com.emberr.presentation.shared.components.EmberrDesktopMenu(
                expanded = showDayPicker,
                onDismissRequest = { showDayPicker = false }
            ) {
                Column(modifier = Modifier.width(280.dp).wrapContentHeight()) {
                    wheelContent()
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EmberrButtonSecondary(text = "Cancel", onClick = { showDayPicker = false }, modifier = Modifier.weight(1f))
                        EmberrButtonPrimary(text = "Save", onClick = { viewModel.saveBackupSchedule(backupFrequency, backupTime, days[selectedIndex]); showDayPicker = false }, modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            EmberrBottomSheet(
                expanded = showDayPicker,
                onDismiss = { showDayPicker = false },
                title = "Select Backup Day"
            ) {
                wheelContent()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmberrButtonSecondary(text = "Cancel", onClick = { showDayPicker = false }, modifier = Modifier.weight(1f))
                    EmberrButtonPrimary(text = "Save", onClick = { viewModel.saveBackupSchedule(backupFrequency, backupTime, days[selectedIndex]); showDayPicker = false }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showThemeSheet) {
        EmberrBottomSheet(
            expanded = true,
            onDismiss = { showThemeSheet = false },
            title = "Theme",
            contentHorizontalPadding = 0.dp
        ) {
            val selectedTheme = runCatching { ThemePreference.valueOf(themePreference) }
                .getOrDefault(ThemePreference.SYSTEM)

            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                ThemePreference.entries.forEach { option ->
                    EmberrBottomSheetOption(
                        label = option.displayName,
                        isSelected = option == selectedTheme,
                        onClick = {
                            viewModel.setThemePreference(option.name)
                            showThemeSheet = false
                        }
                    )
                }

                EmberrButtonPrimary(
                    text = "Close",
                    onClick = { showThemeSheet = false },
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                )
            }
        }
    }

    if (showFontSizeSheet) {
        EmberrBottomSheet(
            expanded = true,
            onDismiss = { showFontSizeSheet = false },
            title = "Font Size",
            contentHorizontalPadding = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                FontSizeOptions.forEach { (preferenceName, displayName) ->
                    EmberrBottomSheetOption(
                        label = displayName,
                        isSelected = preferenceName == fontSizePreference,
                        onClick = {
                            viewModel.setFontSizePreference(preferenceName)
                            showFontSizeSheet = false
                        }
                    )
                }

                EmberrButtonPrimary(
                    text = "Close",
                    onClick = { showFontSizeSheet = false },
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                )
            }
        }
    }

    if (showFontStyleSheet) {
        EmberrBottomSheet(
            expanded = true,
            onDismiss = { showFontStyleSheet = false },
            title = "Font Style",
            contentHorizontalPadding = 0.dp
        ) {
            val selectedFontStyle = runCatching { FontStylePreference.valueOf(fontStylePreference) }
                .getOrDefault(FontStylePreference.POPPINS)

            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                FontStylePreference.entries.forEach { option ->
                    EmberrBottomSheetOption(
                        label = option.displayName,
                        isSelected = option == selectedFontStyle,
                        labelFontFamily = fontFamilyFor(option),
                        onClick = {
                            viewModel.setFontStylePreference(option.name)
                            showFontStyleSheet = false
                        }
                    )
                }

                EmberrButtonPrimary(
                    text = "Close",
                    onClick = { showFontStyleSheet = false },
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                )
            }
        }
    }

    if (showTopBarFadeStyleSheet) {
        EmberrBottomSheet(
            expanded = true,
            onDismiss = { showTopBarFadeStyleSheet = false },
            title = "Top Bar Fade",
            subtitle = "Choose how content fades out behind the top bar while you scroll.",
            contentHorizontalPadding = 0.dp
        ) {
            val selectedFadeStyle = topBarFadeStyleFor(topBarFadeStyle)

            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                TopBarFadeStyle.entries.forEach { option ->
                    EmberrBottomSheetOption(
                        label = option.displayName,
                        isSelected = option == selectedFadeStyle,
                        onClick = {
                            viewModel.setTopBarFadeStyle(option.name)
                            showTopBarFadeStyleSheet = false
                        }
                    )
                }

                EmberrButtonPrimary(
                    text = "Close",
                    onClick = { showTopBarFadeStyleSheet = false },
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                )
            }
        }
    }

    if (showSubNoteOpenModeSheet) {
        EmberrBottomSheet(
            expanded = true,
            onDismiss = { showSubNoteOpenModeSheet = false },
            title = "Subnote Opening",
            subtitle = "Choose how notes linked inside another note (subnotes) open on desktop.",
            contentHorizontalPadding = 0.dp
        ) {
            val selectedMode = runCatching { SubNoteOpenMode.valueOf(subNoteOpenMode) }
                .getOrDefault(SubNoteOpenMode.SIDE_PANEL)

            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                SubNoteOpenMode.entries.forEach { option ->
                    EmberrBottomSheetOption(
                        label = option.displayName,
                        isSelected = option == selectedMode,
                        onClick = {
                            viewModel.setSubNoteOpenMode(option.name)
                            showSubNoteOpenModeSheet = false
                        }
                    )
                }

                EmberrButtonPrimary(
                    text = "Close",
                    onClick = { showSubNoteOpenModeSheet = false },
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                )
            }
        }
    }

    if (showDisableAiConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisableAiConfirmation = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Text(
                    text = "Turn Off AI Features?",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "This permanently deletes the downloaded embedding and language models, the note search index, and every saved provider API key. Your notes and saved chats are untouched. Turning AI back on later means downloading and re-indexing everything again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmberrButtonSecondary(
                        text = "Cancel",
                        onClick = { showDisableAiConfirmation = false },
                        modifier = Modifier.weight(1f)
                    )
                    EmberrButtonPrimary(
                        text = "Turn Off",
                        onClick = {
                            showDisableAiConfirmation = false
                            viewModel.setAiFeaturesDisabled(true)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        )
    }

    if (showPairingDialog && activePairingData != null) {
        SyncPairingDialog(
            pairingData = activePairingData,
            onDismiss = {
                showPairingDialog = false
                syncViewModel.stopServerUnlessPaired()
            }
        )
    }

    if (showScannerDialog) {
        SyncScannerDialog(
            onDismiss = { showScannerDialog = false },
            onScanned = { pairingData ->
                showScannerDialog = false
                syncViewModel.applyScannedPairing(pairingData)
            }
        )
    }

    if (showUnpairConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnpairConfirmation = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Text(
                    text = "Unpair Device?",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = if (isDesktopPlatform) {
                        "This removes the LAN sync credentials stored on this desktop. Your phone won't be notified automatically - unpair from this desktop on your phone as well, or it will keep trying to reach it."
                    } else {
                        "This removes the LAN sync credentials stored on this device. Keep the desktop app open so it can be notified - if you can't, unpair from Desktop manually as well, or it will keep thinking it's still paired."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                if (isDesktopPlatform) {
                    EmberrButtonPrimary(
                        text = "Unpair",
                        onClick = {
                            showUnpairConfirmation = false
                            syncViewModel.unpair()
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EmberrButtonSecondary(
                            text = "Cancel",
                            onClick = { showUnpairConfirmation = false },
                            modifier = Modifier.weight(1f)
                        )
                        EmberrButtonPrimary(
                            text = "Unpair",
                            onClick = {
                                showUnpairConfirmation = false
                                syncViewModel.unpair()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            dismissButton = if (isDesktopPlatform) {
                {
                    EmberrButtonSecondary(
                        text = "Cancel",
                        onClick = { showUnpairConfirmation = false }
                    )
                }
            } else null
        )
    }
}

@Composable
private fun MobileSettingsList(
    categories: List<SettingsCategory>,
    listState: LazyListState,
    contentTopPadding: Dp,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = contentTopPadding, bottom = 56.dp)
    ) {
        categories.forEach { category ->
            item(key = category.title) { category.content() }
        }
    }
}

@Composable
private fun DesktopSettingsPanes(
    categories: List<SettingsCategory>,
    contentTopPadding: Dp,
    modifier: Modifier = Modifier
) {
    var selectedCategoryTitle by remember { mutableStateOf(categories.first().title) }
    val selectedCategory = categories.firstOrNull { it.title == selectedCategoryTitle } ?: categories.first()

    Row(modifier = modifier.padding(top = contentTopPadding)) {
        SettingsCategorySidebar(
            categories = categories,
            selectedCategoryTitle = selectedCategory.title,
            onCategorySelected = { title -> selectedCategoryTitle = title }
        )

        val paneScrollState = remember(selectedCategory.title) { ScrollState(0) }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(paneScrollState)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = SettingsPaneMaxWidth)
                        .padding(bottom = 48.dp)
                ) {
                    key(selectedCategory.title) { selectedCategory.content() }
                }
            }

            EmberrVerticalScrollbar(
                scrollState = paneScrollState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SettingsCategorySidebar(
    categories: List<SettingsCategory>,
    selectedCategoryTitle: String,
    onCategorySelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .width(SettingsSidebarWidth)
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 20.dp)
            .clip(SettingsCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category.title == selectedCategoryTitle
            val accentColor = if (category.isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) accentColor.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { onCategorySelected(category.title) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(category.icon),
                    contentDescription = null,
                    tint = if (isSelected) accentColor else accentColor.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = category.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RemindersSettingsSection() {
    SettingsGroup(title = "Reminders") {
        val permissionCoordinator = rememberAppPermissionCoordinator()
        val isUnrestricted = permissionCoordinator.isGranted(AppPermission.UnrestrictedBackground)

        SettingsActionRow(
            icon = painterResource(Res.drawable.timer_reset),
            title = "Unrestricted Background",
            trailingLabel = if (isUnrestricted) "On" else "Off",
            onClick = { permissionCoordinator.request(AppPermission.UnrestrictedBackground) }
        )
    }
}

@Composable
private fun DataSettingsSection(
    autoBackupEnabled: Boolean,
    backupFrequency: String,
    backupTime: String,
    backupDay: String,
    onImportExportClick: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onBackupFrequencySelected: (String) -> Unit,
    onPickBackupDay: () -> Unit,
    onPickBackupTime: () -> Unit,
    onChooseBackupFolder: () -> Unit
) {
    SettingsGroup(title = "Data & Storage") {
        SettingsActionRow(
            icon = painterResource(Res.drawable.file_down),
            title = "Import / Export",
            onClick = onImportExportClick
        )

        if (!isDesktopPlatform) {
            SettingsDivider()
            SettingsToggleRow(
                icon = painterResource(Res.drawable.folder_sync),
                title = "Automatic Backups",
                isChecked = autoBackupEnabled,
                onCheckedChange = onAutoBackupChange
            )

            AnimatedVisibility(
                visible = autoBackupEnabled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSegmentedOptions(
                        options = BackupFrequencies,
                        selectedOption = backupFrequency,
                        onOptionSelected = onBackupFrequencySelected,
                        modifier = Modifier.padding(start = 68.dp, end = 14.dp, bottom = 14.dp)
                    )

                    if (backupFrequency == "Weekly") {
                        SettingsDivider()
                        SettingsActionRow(
                            icon = painterResource(Res.drawable.calendar_clock),
                            title = "Backup Day",
                            trailingLabel = backupDay,
                            onClick = onPickBackupDay
                        )
                    }

                    if (backupFrequency != "Hourly") {
                        SettingsDivider()
                        SettingsActionRow(
                            icon = painterResource(Res.drawable.timer_reset),
                            title = "Backup Time",
                            trailingLabel = backupTime,
                            onClick = onPickBackupTime
                        )
                    }

                    SettingsDivider()
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.folder_input),
                        title = "Backup Location",
                        trailingLabel = "Change",
                        onClick = onChooseBackupFolder
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncSettingsSection(
    isPaired: Boolean,
    syncStatus: String,
    serverUnavailableReason: String?,
    syncPortInput: String,
    onSyncPortChange: (String) -> Unit,
    onOpenSelfHostSetup: () -> Unit,
    onSyncNow: () -> Unit,
    onPairMobileDevice: () -> Unit,
    onPairWithDesktop: () -> Unit,
    onUnpair: () -> Unit
) {
    SettingsGroup(title = "Sync & Backup") {
        SettingsActionRow(
            icon = painterResource(Res.drawable.refresh_cw),
            title = "Self-Host",
            onClick = onOpenSelfHostSetup
        )
    }

    SettingsGroup(title = "LAN Sync") {
        if (isDesktopPlatform && serverUnavailableReason != null) {
            SettingsWarningNotice(message = serverUnavailableReason)
        }

        when {
            isPaired -> {
                if (isDesktopPlatform) {
                    SettingsFootnote(
                        text = "Paired. This desktop syncs automatically whenever your phone connects over LAN.",
                        startPadding = 16.dp
                    )
                } else {
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.refresh_cw),
                        title = "Sync Now",
                        trailingLabel = syncStatus,
                        onClick = onSyncNow
                    )
                }

                SettingsDivider()
                SettingsActionRow(
                    icon = rememberVectorPainter(Icons.Default.LinkOff),
                    title = if (isDesktopPlatform) "Unpair from Mobile Device" else "Unpair from Desktop",
                    isDestructive = true,
                    onClick = onUnpair
                )
            }

            isDesktopPlatform -> {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "Sync Port",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    EmberrTextField(
                        value = syncPortInput,
                        onValueChange = onSyncPortChange,
                        placeholder = "8080",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Only takes effect after restarting Emberr. Change this if another " +
                            "program on this computer is already using the current port.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
                SettingsDivider()
                SettingsActionRow(
                    icon = painterResource(Res.drawable.qr_code),
                    title = "Pair Mobile Device",
                    onClick = onPairMobileDevice
                )
            }

            else -> {
                SettingsActionRow(
                    icon = painterResource(Res.drawable.scan_line),
                    title = "Pair with Desktop",
                    onClick = onPairWithDesktop
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsSection(
    themeLabel: String,
    fontSizeLabel: String,
    fontStyleLabel: String,
    subNoteOpenModeLabel: String,
    topBarFadeStyleLabel: String,
    showScrollbar: Boolean,
    customWindowFrameEnabled: Boolean,
    autoHideTitleBar: Boolean,
    onThemeClick: () -> Unit,
    onFontSizeClick: () -> Unit,
    onFontStyleClick: () -> Unit,
    onSubNoteOpenModeClick: () -> Unit,
    onTopBarFadeStyleClick: () -> Unit,
    onShowScrollbarChange: (Boolean) -> Unit,
    onCustomWindowFrameChange: (Boolean) -> Unit,
    onAutoHideTitleBarChange: (Boolean) -> Unit
) {
    SettingsGroup(title = "Appearance") {
        SettingsActionRow(
            icon = painterResource(Res.drawable.palette),
            title = "Theme",
            trailingLabel = themeLabel,
            onClick = onThemeClick
        )
        SettingsDivider()
        SettingsActionRow(
            icon = painterResource(Res.drawable.sliders_horizontal),
            title = "Font Size",
            trailingLabel = fontSizeLabel,
            onClick = onFontSizeClick
        )
        SettingsDivider()
        SettingsActionRow(
            icon = painterResource(Res.drawable.text_type),
            title = "Font Style",
            trailingLabel = fontStyleLabel,
            onClick = onFontStyleClick
        )

        if (!isDesktopPlatform) {
            SettingsDivider()
            SettingsActionRow(
                icon = painterResource(Res.drawable.palette),
                title = "Top Bar Fade",
                trailingLabel = topBarFadeStyleLabel,
                onClick = onTopBarFadeStyleClick
            )

            SettingsFootnote(
                text = "Controls how content disappears behind the top bar as you scroll."
            )
        }
    }

    if (isDesktopPlatform) {
        SettingsGroup(title = "Desktop Window") {
            SettingsActionRow(
                icon = painterResource(Res.drawable.sidebar),
                title = "Subnote Opening",
                trailingLabel = subNoteOpenModeLabel,
                onClick = onSubNoteOpenModeClick
            )

            SettingsDivider()
            SettingsToggleRow(
                icon = painterResource(Res.drawable.sidebar),
                title = "Show Scrollbar",
                isChecked = showScrollbar,
                onCheckedChange = onShowScrollbarChange
            )

            SettingsFootnote(
                text = "Shows scrollbars in the sidebar, editor, note lists, tables and databases. " +
                    "Scrolling with the wheel or trackpad works either way."
            )

            SettingsDivider()

            var showCustomWindowFrameNotice by remember { mutableStateOf(false) }

            SettingsToggleRow(
                icon = painterResource(Res.drawable.sidebar),
                title = "Custom Title Bar",
                isChecked = customWindowFrameEnabled,
                onCheckedChange = { enabled ->
                    onCustomWindowFrameChange(enabled)
                    showCustomWindowFrameNotice = true
                }
            )

            SettingsFootnote(
                text = "Draws the window corners and the minimise, maximise and close buttons " +
                    "inside Emberr instead of letting the desktop draw a separate title bar. " +
                    "Takes effect the next time you start Emberr."
            )

            if (customWindowFrameEnabled) {
                SettingsDivider()
                SettingsToggleRow(
                    icon = painterResource(Res.drawable.sidebar),
                    title = "Auto-hide Title Bar",
                    isChecked = autoHideTitleBar,
                    onCheckedChange = onAutoHideTitleBarChange
                )

                SettingsFootnote(
                    text = "Keeps the title bar out of the way and slides it in when you move " +
                        "the pointer to the top of the window. Turn this off to keep it on " +
                        "screen all the time."
                )
            }

            if (showCustomWindowFrameNotice) {
                EmberrAlertDialog(
                    onDismissRequest = { showCustomWindowFrameNotice = false },
                    title = "Restart to apply"
                ) {
                    Text(
                        text = "The window cannot switch between its own title bar and the " +
                            "desktop's one while it is open, so Emberr needs to restart to " +
                            "apply this. Unsaved work is saved as you type, so restarting now " +
                            "is safe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EmberrButtonSecondary(
                            text = "Later",
                            onClick = { showCustomWindowFrameNotice = false },
                            modifier = Modifier.weight(1f)
                        )
                        EmberrButtonPrimary(
                            text = "Restart Now",
                            onClick = {
                                showCustomWindowFrameNotice = false
                                restartApplication()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSettingsSection(
    aiFeaturesDisabled: Boolean,
    isPurgingAiData: Boolean,
    onDisableRequested: () -> Unit,
    onEnableRequested: () -> Unit
) {
    SettingsGroup(title = "AI") {
        SettingsToggleRow(
            icon = painterResource(Res.drawable.astroid),
            title = "Turn Off AI Features",
            isChecked = aiFeaturesDisabled,
            onCheckedChange = { isChecked ->
                if (!isPurgingAiData) {
                    if (isChecked) onDisableRequested() else onEnableRequested()
                }
            }
        )

        if (isPurgingAiData) {
            SettingsFootnote(text = "Removing models, embeddings and saved keys…")
        } else if (isDesktopPlatform) {
            SettingsFootnote(
                text = if (aiFeaturesDisabled) {
                    "AI is off. The assistant button is hidden, notes are no longer indexed, and all model files, embeddings and API keys have been removed. Saved chats are kept."
                } else {
                    "Hides the AI assistant, stops note indexing, and permanently deletes downloaded models, the search index and stored API keys to free up storage. Saved chats are kept."
                }
            )
        }
    }
}

@Composable
private fun HelpSettingsSection() {
    SettingsGroup(title = "Need Help?") {
        SettingsActionRow(
            icon = painterResource(Res.drawable.badge_question_mark),
            title = "FAQ",
            onClick = {}
        )
        SettingsDivider()
        SettingsActionRow(
            icon = painterResource(Res.drawable.badge_plus),
            title = "What's New",
            onClick = {}
        )
        SettingsDivider()
        SettingsActionRow(
            icon = painterResource(Res.drawable.shield_alert),
            title = "Privacy Policy",
            onClick = {}
        )
        SettingsDivider()
        SettingsActionRow(
            icon = painterResource(Res.drawable.info),
            title = "About Emberr",
            trailingLabel = "v1.0.0",
            onClick = {}
        )
    }
}

@Composable
private fun DangerZoneSettingsSection(onClearAllData: () -> Unit) {
    SettingsGroup(title = "Danger Zone") {
        SettingsActionRow(
            icon = painterResource(Res.drawable.triangle_alert),
            title = "Clear All Data",
            isDestructive = true,
            onClick = onClearAllData
        )

        if (isDesktopPlatform) {
            SettingsFootnote(
                text = "Deletes every note, reminder and attachment stored on this device. This cannot be undone."
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    )
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 22.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SettingsCardShape)
                .background(MaterialTheme.colorScheme.surface),
            content = content
        )
    }
}

@Composable
private fun SettingsRowIcon(icon: Painter, isDestructive: Boolean = false) {
    val accentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(SettingsRowIconShape)
            .background(accentColor.copy(alpha = if (isDestructive) 0.14f else 0.09f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accentColor.copy(alpha = if (isDestructive) 1f else 0.8f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsValuePill(label: String, isDestructive: Boolean = false) {
    val labelColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = labelColor.copy(alpha = if (isDestructive) 0.9f else 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(labelColor.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun SettingsFootnote(text: String, startPadding: Dp = 68.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = startPadding, end = 16.dp, top = 2.dp, bottom = 14.dp)
    )
}

@Composable
private fun SettingsWarningNotice(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(Res.drawable.triangle_alert),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SettingsSegmentedOptions(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) SelectedOptionBackground else Color.Transparent)
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsActionRow(
    icon: Painter,
    title: String,
    trailingLabel: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val titleColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon = icon, isDestructive = isDestructive)

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isDestructive) FontWeight.Medium else FontWeight.Normal,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )

        if (trailingLabel != null) {
            SettingsValuePill(label = trailingLabel, isDestructive = isDestructive)
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (!isDestructive) {
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
fun SettingsToggleRow(
    icon: Painter,
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon = icon)

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

private fun fontSizeDisplayNameFor(preferenceName: String): String =
    FontSizeOptions.firstOrNull { it.first == preferenceName }?.second
        ?: FontSizeOptions[1].second

private fun topBarFadeStyleFor(styleName: String): TopBarFadeStyle =
    runCatching { TopBarFadeStyle.valueOf(styleName) }.getOrDefault(TopBarFadeStyle.BLUR)

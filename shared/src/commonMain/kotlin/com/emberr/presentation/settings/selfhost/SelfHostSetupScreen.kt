package com.emberr.presentation.settings.selfhost

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.emberr.domain.selfhost.sync.SelfHostSyncLog
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.settings.SettingsGroup
import com.emberr.presentation.shared.components.EmberrAlertDialog
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.stableStatusBarsPadding
import com.emberr.ui.theme.LocalEmberrFontStyle
import com.emberr.ui.theme.fontFamilyFor
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SelfHostSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: SelfHostSetupViewModel = koinViewModel()
) {
    val screenState by viewModel.screenState.collectAsState()

    val internalHazeState = remember { HazeState() }
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        when (val state = screenState) {
            SelfHostScreenState.Checking -> CheckingIndicator(topBarHeightDp = topBarHeightDp)
            is SelfHostScreenState.Unconfigured -> SetupForm(
                form = state.form,
                viewModel = viewModel,
                hazeState = internalHazeState,
                topBarHeightDp = topBarHeightDp
            )
            is SelfHostScreenState.Connected -> ConnectedDashboard(
                state = state.connectedState,
                viewModel = viewModel,
                hazeState = internalHazeState,
                topBarHeightDp = topBarHeightDp
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> topBarHeightPx = coordinates.size.height.toFloat() }
        ) {
            SelfHostSetupTopBar(onNavigateBack = onNavigateBack, hazeState = internalHazeState)
        }
    }
}

@Composable
private fun CheckingIndicator(topBarHeightDp: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = topBarHeightDp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun SetupForm(
    form: SelfHostSetupFormState,
    viewModel: SelfHostSetupViewModel,
    hazeState: HazeState,
    topBarHeightDp: Dp
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = topBarHeightDp + 8.dp, bottom = 48.dp)
    ) {
        item {
            SettingsGroup(title = "Server Details") {
                ServerDetailsCard(form = form, viewModel = viewModel)
            }
        }

        if (form.vaultMode == VaultMode.CREATE_VAULT) {
            item {
                SettingsGroup(title = "Recovery Passphrase") {
                    PassphraseCard(passphrase = form.passphrase, onRegenerate = viewModel::regeneratePassphrase)
                }
            }

            item {
                SettingsGroup(title = "Before You Continue") {
                    ZeroKnowledgeWarningCard(
                        acknowledged = form.hasAcknowledgedRisk,
                        onAcknowledgedChanged = viewModel::onAcknowledgeRiskChanged
                    )
                }
            }
        }

        if (form.vaultMode == VaultMode.RESTORE_VAULT) {
            item {
                SettingsGroup(title = "Restore Vault") {
                    RestorePassphraseCard(
                        passphraseInput = form.existingPassphraseInput,
                        onPassphraseChanged = viewModel::onExistingPassphraseChanged,
                        onSubmit = { if (form.canFinishSetup) viewModel.completeSetup() }
                    )
                }
            }
        }

        if (form.vaultMode != VaultMode.UNKNOWN) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    EmberrButtonPrimary(
                        text = when {
                            form.setupPhase == SetupPhase.FINALIZING -> "Setting up..."
                            form.vaultMode == VaultMode.RESTORE_VAULT -> "Restore Vault"
                            else -> "Complete Setup"
                        },
                        onClick = viewModel::completeSetup,
                        enabled = form.canFinishSetup,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        form.errorMessage?.let { message ->
            item {
                ErrorMessageCard(message = message)
            }
        }
    }

    if (form.configurationWarningMessage != null) {
        EmberrAlertDialog(
            onDismissRequest = viewModel::dismissConfigurationWarning,
            title = "Server Address Rejected"
        ) {
            Text(
                text = form.configurationWarningMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(20.dp))
            EmberrButtonPrimary(
                text = "Got it",
                onClick = viewModel::dismissConfigurationWarning,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConnectedDashboard(
    state: SelfHostConnectedState,
    viewModel: SelfHostSetupViewModel,
    hazeState: HazeState,
    topBarHeightDp: Dp
) {
    var showDisconnectConfirmation by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = topBarHeightDp + 8.dp, bottom = 48.dp)
    ) {
        item {
            SettingsGroup(title = "Status") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Status: Connected",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = state.serverUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    EmberrButtonPrimary(
                        text = if (state.syncStatus == ManualSyncStatus.SYNCING) "Syncing..." else "Sync Now",
                        onClick = {
                            SelfHostSyncLog.d("UI: Sync Now button tapped")
                            viewModel.syncNow()
                        },
                        enabled = state.syncStatus != ManualSyncStatus.SYNCING,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.syncStatus == ManualSyncStatus.SYNCING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (state.syncStatus == ManualSyncStatus.SYNCING) {
                                "Syncing..."
                            } else {
                                formatLastSynced(state.lastSyncedAtMillis)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    state.syncError?.let { error ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.07f))
                    .clickable(enabled = !state.isDisconnecting) { showDisconnectConfirmation = true }
                    .padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isDisconnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Disconnect Vault",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(text = "Disconnect Vault?", fontFamily = fontFamilyFor(LocalEmberrFontStyle.current), fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    text = "This permanently removes your encryption key and server credentials from this " +
                        "device. You'll need your recovery passphrase to reconnect.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirmation = false
                    viewModel.disconnectVault()
                }) {
                    Text(
                        text = "Disconnect",
                        fontFamily = fontFamilyFor(LocalEmberrFontStyle.current),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmation = false }) {
                    Text(text = "Cancel", fontFamily = fontFamilyFor(LocalEmberrFontStyle.current), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@Composable
private fun ServerDetailsCard(form: SelfHostSetupFormState, viewModel: SelfHostSetupViewModel) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val onSubmitServerDetails: () -> Unit = { if (form.canTestConnection) viewModel.testConnection() }

        EmberrTextField(
            value = form.serverUrl,
            onValueChange = viewModel::onServerUrlChanged,
            placeholder = "https://cloud.example.com/remote.php/dav",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            onSubmit = onSubmitServerDetails
        )

        EmberrTextField(
            value = form.username,
            onValueChange = viewModel::onUsernameChanged,
            placeholder = "Username",
            modifier = Modifier.fillMaxWidth(),
            onSubmit = onSubmitServerDetails
        )

        EmberrTextField(
            value = form.password,
            onValueChange = viewModel::onPasswordChanged,
            placeholder = "Password",
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            onSubmit = onSubmitServerDetails
        )

        Spacer(modifier = Modifier.height(4.dp))

        EmberrButtonPrimary(
            text = if (form.connectionTestStatus == ConnectionTestStatus.TESTING) "Testing..." else "Test Connection",
            onClick = viewModel::testConnection,
            enabled = form.canTestConnection,
            modifier = Modifier.fillMaxWidth()
        )

        if (form.connectionTestStatus == ConnectionTestStatus.TESTING) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Contacting server...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        form.connectionTestMessage?.let { message ->
            val isSuccess = form.connectionTestStatus == ConnectionTestStatus.VERIFIED
            val tint = if (isSuccess) SuccessColor else MaterialTheme.colorScheme.error

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}

@Composable
private fun RestorePassphraseCard(
    passphraseInput: String,
    onPassphraseChanged: (String) -> Unit,
    onSubmit: (() -> Unit)? = null
) {
    var isPassphraseVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "An existing vault was found on this server. Enter the recovery passphrase you set up " +
                "on your other device to unlock it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        EmberrTextField(
            value = passphraseInput,
            onValueChange = onPassphraseChanged,
            placeholder = "16-character passphrase",
            visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                    Icon(
                        imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPassphraseVisible) "Hide passphrase" else "Show passphrase",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            onSubmit = onSubmit
        )
    }
}

@Composable
private fun PassphraseCard(passphrase: String, onRegenerate: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "This passphrase is the only way to recover your data on a new device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = passphrase,
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = { clipboardManager.setText(AnnotatedString(passphrase)) }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy passphrase",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onRegenerate) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Generate a new passphrase",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ZeroKnowledgeWarningCard(acknowledged: Boolean, onAcknowledgedChanged: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.07f))
                .padding(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Emberr uses zero-knowledge encryption. Your passphrase is never sent to us or " +
                    "stored on the server. If you lose it, your synced notes cannot be recovered — " +
                    "not even by Emberr.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = onAcknowledgedChanged,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error)
            )
            Text(
                text = "I understand this passphrase cannot be recovered if lost",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorMessageCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.07f))
            .padding(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
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
private fun SelfHostSetupTopBar(onNavigateBack: () -> Unit, hazeState: HazeState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            .padding(
                top = if (isDesktopPlatform) 16.dp else 10.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        TopBarIconButton(
            icon = painterResource(Res.drawable.chevron_left),
            contentDescription = "Back",
            bgColor = Color.Transparent,
            tint = MaterialTheme.colorScheme.primary,
            hazeState = hazeState,
            hazeStyle = EmberrBlur.Regular,
            onClick = onNavigateBack
        )

        Text(
            text = "Self-Host",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

private val SuccessColor
    @Composable get() = Color(0xFF4CAF50)

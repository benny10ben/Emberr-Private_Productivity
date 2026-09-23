package com.emberr.presentation.mobile.voice

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.components.MinimalTimePickerDialog
import com.emberr.presentation.shared.components.ReminderPresetMenu
import com.emberr.presentation.shared.components.TimePresetMenu
import com.emberr.ui.theme.LocalAppIsDark
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.calendar_add
import emberr.shared.generated.resources.clock_circle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.painterResource

private val VoiceTaskDialogShape = RoundedCornerShape(24.dp)
private val OrbIndigo = Color(0xFF4F5B8A)
private val OrbPeriwinkle = Color(0xFF9AA6E0)
private val OrbLavender = Color(0xFF8E7CC3)
private val OrbMidnight = Color(0xFF2C3354)
private val OrbGlacier = Color(0xFF6FA8C7)
private const val ORB_OUTLINE_POINTS = 90
private val KEYBOARD_CLOSE_WAIT = 500.milliseconds

@Composable
fun VoiceTaskDialog(
    state: VoiceTaskSessionState,
    onOrbClick: () -> Unit,
    onTaskEditStart: () -> Unit,
    onTaskTextChange: (index: Int, newText: String) -> Unit,
    onTaskReminderChange: (index: Int, reminder: Long?) -> Unit,
    onAdd: () -> Unit,
    onDiscard: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var editingTaskIndex by remember { mutableStateOf<Int?>(null) }
    val orbSize by animateDpAsState(
        targetValue = if (editingTaskIndex != null) 72.dp else 160.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    )
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(timeZone) }
    val statusText = when {
        state.isListening -> "Listening..."
        state.errorMessage != null -> state.errorMessage
        else -> "Tap the orb to speak"
    }
    val taskListScrollState = rememberScrollState()
    LaunchedEffect(taskListScrollState, state.isListening) {
        if (!state.isListening) return@LaunchedEffect
        snapshotFlow { taskListScrollState.maxValue }
            .filter { bottom -> bottom != Int.MAX_VALUE }
            .collect { bottom ->
                if (!taskListScrollState.isScrollInProgress) taskListScrollState.animateScrollTo(bottom)
            }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp)
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(0.88f)
                    .clip(VoiceTaskDialogShape)
                    .background(
                        if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.background
                    )
                    .animateContentSize(tween(durationMillis = 300, easing = FastOutSlowInEasing))
                    .padding(24.dp)
            ) {
                VoiceOrb(
                    isListening = state.isListening,
                    onClick = {
                        focusManager.clearFocus()
                        onOrbClick()
                    },
                    modifier = Modifier.size(orbSize)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "(add tasks with voice)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                if (state.tasks.isNotEmpty() || state.partialText.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .verticalScroll(taskListScrollState)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        state.tasks.forEachIndexed { index, task ->
                            EditableVoiceTaskRow(
                                text = task.taskText,
                                reminder = task.timestamp,
                                today = today,
                                timeZone = timeZone,
                                isKeyboardOpen = editingTaskIndex != null,
                                onReminderPickerOpen = {
                                    focusManager.clearFocus()
                                    onTaskEditStart()
                                },
                                onReminderChange = { reminder -> onTaskReminderChange(index, reminder) },
                                onEditStart = {
                                    editingTaskIndex = index
                                    onTaskEditStart()
                                },
                                onEditEnd = { if (editingTaskIndex == index) editingTaskIndex = null },
                                onTextChange = { newText -> onTaskTextChange(index, newText) }
                            )
                        }
                        if (state.partialText.isNotBlank()) {
                            BeingHeardRow(text = state.partialText)
                        }
                    }
                    if (state.tasks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap a task to fix it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EmberrButtonSecondary(
                        text = "Discard",
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f)
                    )
                    EmberrButtonPrimary(
                        text = "Add",
                        onClick = onAdd,
                        enabled = state.tasks.any { it.taskText.isNotBlank() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableVoiceTaskRow(
    text: String,
    reminder: Long?,
    today: LocalDate,
    timeZone: TimeZone,
    isKeyboardOpen: Boolean,
    onReminderPickerOpen: () -> Unit,
    onReminderChange: (Long?) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf(text) }
    LaunchedEffect(text, isFocused) {
        if (!isFocused) draftText = text
    }
    val textColor = MaterialTheme.colorScheme.onSurface
    var isDateMenuOpen by remember { mutableStateOf(false) }
    var isTimeMenuOpen by remember { mutableStateOf(false) }
    var isCustomDatePickerOpen by remember { mutableStateOf(false) }
    var isCustomTimePickerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val openPickerOnceKeyboardCloses: (() -> Unit) -> Unit = { openPicker ->
        val shouldWaitForKeyboard = isKeyboardOpen
        onReminderPickerOpen()
        scope.launch {
            if (shouldWaitForKeyboard) delay(KEYBOARD_CLOSE_WAIT)
            openPicker()
        }
    }

    VoiceTaskRowFrame(
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReminderChip(
                    icon = painterResource(Res.drawable.calendar_add),
                    label = voiceTaskDayLabel(reminder, today, timeZone),
                    onClick = { openPickerOnceKeyboardCloses { isDateMenuOpen = true } }
                )
                ReminderChip(
                    icon = painterResource(Res.drawable.clock_circle),
                    label = voiceTaskTimeLabel(reminder, timeZone),
                    onClick = { openPickerOnceKeyboardCloses { isTimeMenuOpen = true } }
                )
            }
        }
    ) {
        BasicTextField(
            value = draftText,
            onValueChange = { newText ->
                val singleLineText = newText.replace('\n', ' ')
                draftText = singleLineText
                onTextChange(singleLineText)
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused == isFocused) return@onFocusChanged
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) onEditStart() else onEditEnd()
                },
            decorationBox = { innerTextField ->
                Box {
                    if (draftText.isEmpty()) {
                        Text(
                            text = "Empty tasks are skipped",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor.copy(alpha = 0.35f)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }

    ReminderPresetMenu(
        expanded = isDateMenuOpen,
        onDismiss = { isDateMenuOpen = false },
        onPresetSelected = onReminderChange,
        onCustomSelected = { isCustomDatePickerOpen = true },
        onRemove = if (reminder != null) {
            { onReminderChange(null) }
        } else null
    )
    TimePresetMenu(
        expanded = isTimeMenuOpen,
        onDismiss = { isTimeMenuOpen = false },
        onPresetSelected = onReminderChange,
        onCustomSelected = { isCustomTimePickerOpen = true }
    )
    if (isCustomDatePickerOpen) {
        MinimalDatePickerDialog(
            initialTimestamp = reminder,
            onDismiss = { isCustomDatePickerOpen = false },
            onConfirm = { pickedDate ->
                onReminderChange(reminderMovedToDate(reminder, pickedDate, timeZone))
                isCustomDatePickerOpen = false
            }
        )
    }
    if (isCustomTimePickerOpen) {
        MinimalTimePickerDialog(
            initialTimestamp = reminder,
            onDismiss = { isCustomTimePickerOpen = false },
            onConfirm = { hour, minute ->
                onReminderChange(reminderMovedToTime(reminder, hour, minute, today, timeZone))
                isCustomTimePickerOpen = false
            }
        )
    }
}

@Composable
private fun ReminderChip(icon: Painter, label: String, onClick: () -> Unit) {
    val chipColor = MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(chipColor.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = chipColor.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = chipColor.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun BeingHeardRow(text: String) {
    VoiceTaskRowFrame(
        footer = {
            Text(
                text = "…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VoiceTaskRowFrame(footer: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            content()
            footer()
        }
    }
}

@Composable
private fun VoiceOrb(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val energy by animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
    )
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isListening) {
        if (!isListening) return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                phase += (frameNanos - previousFrameNanos) / 1_000_000_000f
                previousFrameNanos = frameNanos
            }
        }
    }
    val outerBlob = remember { Path() }
    val innerBlob = remember { Path() }

    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isListening) onClick() }
            )
    ) {
        val halfSize = size.minDimension / 2f
        val orbRadius = halfSize * 0.6f * (0.9f + 0.1f * energy)
        val wobble = 0.07f * energy
        val pulse = 0.5f + 0.5f * sin(phase * 2.4f)
        val glowRadius = halfSize * (0.82f + 0.18f * energy * pulse)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbIndigo.copy(alpha = 0.2f + 0.3f * energy),
                    OrbLavender.copy(alpha = 0.08f + 0.12f * energy),
                    Color.Transparent
                ),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius
        )

        outerBlob.traceBlob(center, orbRadius, wobble, phase, lobeCount = 3, spinDirection = 1f)
        rotate(degrees = phase * 40f, pivot = center) {
            drawPath(
                path = outerBlob,
                brush = Brush.sweepGradient(
                    colors = listOf(OrbIndigo, OrbLavender, OrbMidnight, OrbGlacier, OrbIndigo),
                    center = center
                ),
                alpha = 0.8f + 0.2f * energy
            )
        }

        val hotCoreCenter = Offset(center.x - orbRadius * 0.12f, center.y - orbRadius * 0.12f)
        innerBlob.traceBlob(hotCoreCenter, orbRadius * 0.7f, wobble * 1.4f, phase, lobeCount = 2, spinDirection = -1f)
        drawPath(
            path = innerBlob,
            brush = Brush.radialGradient(
                colors = listOf(OrbPeriwinkle.copy(alpha = 0.95f), OrbIndigo.copy(alpha = 0f)),
                center = hotCoreCenter,
                radius = orbRadius * 0.75f
            )
        )

        val shineCenter = Offset(center.x - orbRadius * 0.35f, center.y - orbRadius * 0.4f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                center = shineCenter,
                radius = orbRadius * 0.45f
            ),
            radius = orbRadius * 0.45f,
            center = shineCenter
        )
    }
}

private fun Path.traceBlob(
    center: Offset,
    radius: Float,
    wobble: Float,
    phase: Float,
    lobeCount: Int,
    spinDirection: Float
) {
    reset()
    for (point in 0..ORB_OUTLINE_POINTS) {
        val angle = point.toFloat() / ORB_OUTLINE_POINTS * 2f * PI.toFloat()
        val ripple = 0.6f * sin(lobeCount * angle + phase * 1.6f * spinDirection) +
            0.4f * sin((lobeCount + 2) * angle - phase * 2.3f * spinDirection)
        val pointRadius = radius * (1f + wobble * ripple)
        val x = center.x + pointRadius * cos(angle)
        val y = center.y + pointRadius * sin(angle)
        if (point == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

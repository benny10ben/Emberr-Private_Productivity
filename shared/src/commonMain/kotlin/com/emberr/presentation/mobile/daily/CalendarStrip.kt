package com.emberr.presentation.mobile.daily

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.CalendarTaskEntity
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.NoRippleIndicationNodeFactory
import com.emberr.presentation.shared.components.emberrBlur
import dev.chrisbanes.haze.HazeState
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import emberr.shared.generated.resources.chevron_right
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.datetime.number
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CollapsedWeekStrip(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    onDateSelected: (LocalDate) -> Unit,
    pagerState: PagerState? = null,
    initialPage: Int = 0,
    initialDate: LocalDate = selectedDate
) {
    var anchorDate by remember { mutableStateOf(selectedDate) }
    LaunchedEffect(selectedDate) {
        if (abs(anchorDate.daysUntil(selectedDate)) > 7) anchorDate = selectedDate
    }

    val dates = remember(anchorDate) { (-15..15).map { anchorDate.plus(it, DateTimeUnit.DAY) } }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val leadingOffset = if (isDesktopPlatform) 1 else 2

    val chipWidth = 74.dp
    val chipHeight = 32.dp
    val chipSpacing = 8.dp

    if (pagerState != null) {
        LaunchedEffect(pagerState, initialPage, initialDate, anchorDate, dates) {
            val itemExtentPx = with(density) { (chipWidth + chipSpacing).toPx() }
            snapshotFlow { pagerState.currentPage - initialPage + pagerState.currentPageOffsetFraction }
                .collect { continuousOffsetFromInitial ->
                    val continuousOffsetFromAnchor = anchorDate.daysUntil(initialDate) + continuousOffsetFromInitial
                    val continuousIndex = 15f + continuousOffsetFromAnchor - leadingOffset
                    val flooredIndex = floor(continuousIndex).toInt().coerceIn(0, dates.lastIndex)
                    val fraction = continuousIndex - floor(continuousIndex)
                    listState.scrollToItem(flooredIndex, (fraction * itemExtentPx).roundToInt())
                }
        }
    } else {
        LaunchedEffect(selectedDate, anchorDate) {
            val targetIndex = dates.indexOf(selectedDate)
            if (targetIndex != -1 && !listState.isScrollInProgress) {
                listState.animateScrollToItem(maxOf(0, targetIndex - leadingOffset))
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta.y
                            coroutineScope.launch {
                                listState.animateScrollBy(delta * 60f)
                            }
                        }
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(chipSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(dates, key = { it.toString() }) { date ->
            WeekStripChip(
                date = date,
                isToday = date == today,
                width = chipWidth,
                height = chipHeight,
                hazeState = null,
                isSelected = date == selectedDate,
                onClick = { onDateSelected(date) }
            )
        }
    }
}

private const val DAYS_AROUND_ANCHOR = 20

@Composable
fun DailyBottomWeekStrip(
    modifier: Modifier = Modifier,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    hazeState: HazeState,
    isCompact: Boolean = false
) {
    var anchorDate by remember { mutableStateOf(selectedDate) }
    LaunchedEffect(selectedDate) {
        if (abs(anchorDate.daysUntil(selectedDate)) > 10) anchorDate = selectedDate
    }

    val dates = remember(anchorDate) {
        (-DAYS_AROUND_ANCHOR..DAYS_AROUND_ANCHOR).map { anchorDate.plus(it, DateTimeUnit.DAY) }
    }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = DAYS_AROUND_ANCHOR - 1
    )

    val sizeSpec = tween<Dp>(durationMillis = 350, easing = FastOutSlowInEasing)
    val pillWidth = 74.dp
    val maxPillHeight = 32.dp
    val pillHeight by animateDpAsState(if (isCompact) 30.dp else maxPillHeight, sizeSpec)
    val pillSpacing = 8.dp
    val collapsedWidth = pillWidth * 3 + pillSpacing * 2

    var isExpanded by remember { mutableStateOf(false) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    LaunchedEffect(isScrolling) {
        if (isScrolling && !isProgrammaticScroll) {
            isExpanded = true
        } else if (!isScrolling) {
            delay(3000.milliseconds)
            if (!listState.isScrollInProgress) isExpanded = false
        }
    }

    val selectedIndex = remember(dates, selectedDate) { dates.indexOf(selectedDate) }
    var hasCompletedFirstScroll by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex, isExpanded, anchorDate) {
        if (!isExpanded && selectedIndex != -1) {
            val targetIndex = (selectedIndex - 1).coerceAtLeast(0)
            isProgrammaticScroll = true
            if (hasCompletedFirstScroll) {
                listState.animateScrollToItem(targetIndex)
            } else {
                listState.scrollToItem(targetIndex)
                hasCompletedFirstScroll = true
            }
            isProgrammaticScroll = false
        }
    }

    val fadeWidth by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 22.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(maxPillHeight),
        contentAlignment = Alignment.Center
    ) {
        val fullWidth = maxWidth
        val animatedWidth by animateDpAsState(
            targetValue = if (isExpanded) fullWidth else collapsedWidth,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )

        LazyRow(
            state = listState,
            modifier = Modifier
                .align(Alignment.Center)
                .width(animatedWidth)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },

//                .drawWithContent {
//                    drawContent()
//                    val fw = fadeWidth.toPx()
//                    if (fw > 0f) {
//                        drawRect(
//                            brush = Brush.horizontalGradient(
//                                0f to Color.Transparent, 1f to Color.Black,
//                                startX = 0f, endX = fw
//                            ),
//                            blendMode = BlendMode.DstIn
//                        )
//                        drawRect(
//                            brush = Brush.horizontalGradient(
//                                0f to Color.Black, 1f to Color.Transparent,
//                                startX = size.width - fw, endX = size.width
//                            ),
//                            blendMode = BlendMode.DstIn
//                        )
//                    }
//                },

            horizontalArrangement = Arrangement.spacedBy(pillSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(dates, key = { it.toString() }) { date ->
                WeekStripChip(
                    date = date,
                    isToday = date == today,
                    width = pillWidth,
                    height = pillHeight,
                    hazeState = hazeState,
                    onClick = { onDateSelected(date) }
                )
            }
        }
    }
}

@Composable
private fun WeekStripChip(
    date: LocalDate,
    isToday: Boolean,
    width: Dp,
    height: Dp,
    hazeState: HazeState?,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val shortDayName = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val primaryTextColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .then(if (hazeState != null) Modifier.emberrBlur(hazeState, EmberrBlur.Regular) else Modifier)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    hazeState != null -> Color.Transparent
                    else -> Color.Transparent
                }
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory
            ) { onClick() }
    ) {
        if (isToday) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = primaryTextColor
            )
        } else {
            Text(
                text = shortDayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = mutedTextColor
            )
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = primaryTextColor
            )
        }
    }
}

@Composable
fun BottomSheetMonthCalendar(
    selectedDate: LocalDate,
    today: LocalDate,
    taskMap: Map<LocalDate, List<CalendarTaskEntity>>,
    onDateSelected: (LocalDate) -> Unit,
    onGoToToday: () -> Unit
) {
    var currentMonth by remember(selectedDate) { mutableStateOf(LocalDate(selectedDate.year, selectedDate.month, 1)) }
    val months = arrayOf("", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minus(1, DateTimeUnit.MONTH) }) {
                Icon(painterResource(Res.drawable.chevron_left), contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${months[currentMonth.month.number]} ${currentMonth.year}",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Go to Today",
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onGoToToday() }.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            IconButton(onClick = { currentMonth = currentMonth.plus(1, DateTimeUnit.MONTH) }) {
                Icon(painterResource(Res.drawable.chevron_right), contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(
                    text = day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val daysInMonth = remember(currentMonth) { currentMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day }
        val startOffset = currentMonth.dayOfWeek.ordinal
        val totalCells = daysInMonth + startOffset
        val rows = if (totalCells % 7 == 0) totalCells / 7 else totalCells / 7 + 1

        Column(modifier = Modifier.fillMaxWidth()) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (col in 0..6) {
                        val dayIndex = (row * 7) + col - startOffset
                        if (dayIndex in 0 until daysInMonth) {
                            val cellDate = LocalDate(currentMonth.year, currentMonth.month, dayIndex + 1)
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                BottomSheetDateCell(
                                    date = cellDate,
                                    isSelected = cellDate == selectedDate,
                                    isToday = cellDate == today,
                                    hasTasks = (taskMap[cellDate]?.size ?: 0) > 0,
                                    onClick = { onDateSelected(cellDate) }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSheetDateCell(
    date: LocalDate, isSelected: Boolean, isToday: Boolean, hasTasks: Boolean, onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.day.toString(), style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected || isToday) FontWeight.Medium else FontWeight.Normal,
            color = textColor, modifier = Modifier.offset(y = if (hasTasks) (-3).dp else 0.dp)
        )
        if (hasTasks) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(4.dp)
                    .clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            )
        }
    }
}

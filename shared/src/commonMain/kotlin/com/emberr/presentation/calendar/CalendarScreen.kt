package com.emberr.presentation.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import com.emberr.domain.util.eventbus.WidgetCalendarDateBus
import com.emberr.domain.util.eventbus.WidgetCalendarEventBus
import com.emberr.domain.util.eventbus.WidgetComposeRequest
import com.emberr.domain.util.eventbus.WidgetComposeRequestBus
import com.emberr.presentation.shared.stableStatusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.domain.model.RecurrenceEditScope
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.customEmberrShadow
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.SelectedOptionBackground
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import emberr.shared.generated.resources.tablet
import emberr.shared.generated.resources.widget2
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

enum class CalendarViewMode { DAY, THREE_DAY, WEEK, MONTH }

private object NoRippleIndicationNodeFactory : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode(): Int = -1
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateBack: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    bottomBarAnimatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: CalendarViewModel = koinViewModel()
) {
    val internalHazeState = remember { HazeState() }

    var showViewsSheet by remember { mutableStateOf(false) }
    var showCategoriesSheet by remember { mutableStateOf(false) }
    val viewMode by viewModel.viewMode.collectAsState()
    var selectedDate by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    val categories by viewModel.categories.collectAsState()
    val events by remember(selectedDate) { viewModel.eventsForDate(selectedDate.toString()) }
        .collectAsState(initial = emptyList())
    var eventEditorState by remember { mutableStateOf<EventEditorState?>(null) }
    // Holds the continuation to run once the user picks a scope in RecurrenceScopeChooser -
    // set by whichever of Edit/Delete triggered it, null the rest of the time.
    var pendingScopeAction by remember { mutableStateOf<((RecurrenceEditScope) -> Unit)?>(null) }
    var slideDirection by remember { mutableStateOf(AnimatedContentTransitionScope.SlideDirection.Left) }

    val scrollState = remember(viewMode) { ScrollState(0) }
    val isScrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }

    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }

    LaunchedEffect(Unit) {
        WidgetCalendarEventBus.requestedEvents.collect { requestedBlockId ->
            WidgetCalendarEventBus.consumeRequestedEvent()
            val event = viewModel.eventForBlock(requestedBlockId).first()
            if (event != null) {
                selectedDate = LocalDate.parse(event.dateString)
                eventEditorState = event.toEditorState()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (WidgetComposeRequestBus.consume(WidgetComposeRequest.NEW_EVENT)) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            selectedDate = now.date
            eventEditorState = EventEditorState(
                original = null,
                name = "",
                date = now.date,
                hour = now.hour,
                minute = 0,
                categoryId = null,
                durationMinutes = 30
            )
        }
    }

    LaunchedEffect(Unit) {
        WidgetCalendarDateBus.requestedDates.collect { requestedDate ->
            WidgetCalendarDateBus.consumeRequestedDate()
            try {
                selectedDate = LocalDate.parse(requestedDate)
                viewModel.setViewMode(CalendarViewMode.DAY)
            } catch (cause: IllegalArgumentException) {
                cause.printStackTrace()
            }
        }
    }

    val dayCount = if (viewMode == CalendarViewMode.THREE_DAY) 3 else 7
    val multiDayDates = remember(selectedDate, dayCount) {
        val anchor = if (dayCount == 7) selectedDate.startOfWeek() else selectedDate
        (0 until dayCount).map { offset -> anchor.plus(offset.toLong(), DateTimeUnit.DAY) }
    }

    var isBottomBarCompact by remember { mutableStateOf(false) }
    val bottomBarScrollAccumulator = remember { FloatArray(1) }
    val bottomBarNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta == 0f) return Offset.Zero

                val accumulated = bottomBarScrollAccumulator[0]
                if ((delta < 0f && accumulated > 0f) || (delta > 0f && accumulated < 0f)) {
                    bottomBarScrollAccumulator[0] = 0f
                }
                bottomBarScrollAccumulator[0] += delta

                val toggleThresholdPx = 60f
                if (bottomBarScrollAccumulator[0] <= -toggleThresholdPx && !isBottomBarCompact) {
                    isBottomBarCompact = true
                    bottomBarScrollAccumulator[0] = 0f
                } else if (bottomBarScrollAccumulator[0] >= toggleThresholdPx && isBottomBarCompact) {
                    isBottomBarCompact = false
                    bottomBarScrollAccumulator[0] = 0f
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .nestedScroll(bottomBarNestedScrollConnection)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = internalHazeState)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val onEventClick: (CalendarEvent) -> Unit = { event ->
                    eventEditorState = event.toEditorState()
                }

                when (viewMode) {
                    CalendarViewMode.DAY -> {
                        CalendarTimeGrid(
                            selectedDate = selectedDate,
                            slideDirection = slideDirection,
                            events = events,
                            categories = categories,
                            scrollState = scrollState,
                            topBarHeightDp = topBarHeightDp,
                            onSwipePreviousDay = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Right
                                selectedDate = selectedDate.plus(-1, DateTimeUnit.DAY)
                            },
                            onSwipeNextDay = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Left
                                selectedDate = selectedDate.plus(1, DateTimeUnit.DAY)
                            },
                            onHourClick = { hour ->
                                eventEditorState = EventEditorState(
                                    original = null,
                                    name = "",
                                    date = selectedDate,
                                    hour = hour,
                                    minute = 0,
                                    categoryId = null,
                                    durationMinutes = 30
                                )
                            },
                            onEventClick = onEventClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    CalendarViewMode.MONTH -> {
                        MonthGrid(
                            anchorMonth = selectedDate,
                            slideDirection = slideDirection,
                            viewModel = viewModel,
                            categories = categories,
                            onSwipePreviousMonth = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Right
                                selectedDate =
                                    LocalDate(selectedDate.year, selectedDate.month, 1).plus(-1, DateTimeUnit.MONTH)
                            },
                            onSwipeNextMonth = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Left
                                selectedDate =
                                    LocalDate(selectedDate.year, selectedDate.month, 1).plus(1, DateTimeUnit.MONTH)
                            },
                            onDayClick = { date ->
                                selectedDate = date
                                viewModel.setViewMode(CalendarViewMode.DAY)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = topBarHeightDp)
                        )
                    }
                    else -> {
                        MultiDayTimeGrid(
                            startDate = selectedDate,
                            dayCount = dayCount,
                            slideDirection = slideDirection,
                            viewModel = viewModel,
                            categories = categories,
                            scrollState = scrollState,
                            topBarHeightDp = topBarHeightDp,
                            onSwipePrevious = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Right
                                selectedDate = selectedDate.plus(-dayCount.toLong(), DateTimeUnit.DAY)
                            },
                            onSwipeNext = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Left
                                selectedDate = selectedDate.plus(dayCount.toLong(), DateTimeUnit.DAY)
                            },
                            onHourClick = { date, hour ->
                                eventEditorState = EventEditorState(
                                    original = null,
                                    name = "",
                                    date = date,
                                    hour = hour,
                                    minute = 0,
                                    categoryId = null,
                                    durationMinutes = 30
                                )
                            },
                            onEventClick = onEventClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
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
                    .pointerInput(Unit) { detectTapGestures {} }
                    .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            ) {
                CalendarTopBar(
                    selectedDate = selectedDate,
                    viewMode = viewMode,
                    slideDirection = slideDirection,
                    onBackClick = onNavigateBack,
                    onViewModeChange = viewModel::setViewMode,
                    categories = categories,
                    onAddCategory = viewModel::addCategory,
                    onUpdateCategory = viewModel::updateCategory,
                    onDeleteCategory = viewModel::deleteCategory,
                    hazeState = internalHazeState
                )
            }

            if (viewMode == CalendarViewMode.THREE_DAY || viewMode == CalendarViewMode.WEEK) {
                MultiDayHeaderBar(
                    dates = multiDayDates,
                    slideDirection = slideDirection,
                    hazeState = internalHazeState,
                    isScrolled = isScrolled,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(9f)
                        .padding(top = topBarHeightDp)
                )
            }

            if (!isDesktopPlatform) {
                CalendarBottomBar(
                    hazeState = internalHazeState,
                    isCompact = isBottomBarCompact,
                    sharedTransitionScope = sharedTransitionScope!!,
                    bottomBarAnimatedVisibilityScope = bottomBarAnimatedVisibilityScope!!,
                    onViewsClick = { showViewsSheet = true },
                    onCategoriesClick = { showCategoriesSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(11f)
                )
            }
        }
    }

    EmberrBottomSheet(
        expanded = showViewsSheet,
        onDismiss = { showViewsSheet = false },
        title = "View",
        contentHorizontalPadding = 0.dp
    ) {
      CompositionLocalProvider(
        LocalIndication provides NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides null
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            ViewModeSection(
                viewMode = viewMode,
                onViewModeChange = { mode ->
                    viewModel.setViewMode(mode)
                    showViewsSheet = false
                }
            )

            EmberrButtonPrimary(
                text = "Close",
                onClick = { showViewsSheet = false },
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            )
        }
      }
    }

    EmberrBottomSheet(
        expanded = showCategoriesSheet,
        onDismiss = { showCategoriesSheet = false },
        title = "Categories",
        contentHorizontalPadding = 0.dp
    ) {
      CompositionLocalProvider(
        LocalIndication provides NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides null
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            CategorySection(
                categories = categories,
                onAddCategory = viewModel::addCategory,
                onUpdateCategory = viewModel::updateCategory,
                onDeleteCategory = viewModel::deleteCategory
            )

            EmberrButtonPrimary(
                text = "Close",
                onClick = { showCategoriesSheet = false },
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            )
        }
      }
    }

    EventEditorSheet(
        state = eventEditorState,
        categories = categories,
        onNameChange = { name -> eventEditorState = eventEditorState?.copy(name = name) },
        onDateChange = { date -> eventEditorState = eventEditorState?.copy(date = date) },
        onTimeChange = { hour, minute -> eventEditorState = eventEditorState?.copy(hour = hour, minute = minute) },
        onDurationChange = { minutes -> eventEditorState = eventEditorState?.copy(durationMinutes = minutes) },
        onCategoryChange = { categoryId -> eventEditorState = eventEditorState?.copy(categoryId = categoryId) },
        onUrlChange = { url -> eventEditorState = eventEditorState?.copy(url = url) },
        onDescriptionChange = { description -> eventEditorState = eventEditorState?.copy(description = description) },
        onRecurrenceChange = { rule -> eventEditorState = eventEditorState?.copy(recurrenceRule = rule) },
        onEditClick = {
            val recurring = eventEditorState?.original?.recurrenceRule != null
            if (recurring) {
                pendingScopeAction = { scope -> eventEditorState = eventEditorState?.copy(isEditing = true, editScope = scope) }
            } else {
                eventEditorState = eventEditorState?.copy(isEditing = true)
            }
        },
        onSave = {
            eventEditorState?.let { state ->
                viewModel.saveEvent(
                    original = state.original,
                    dateString = state.date.toString(),
                    timestamp = state.toEpochMillis(),
                    name = state.name,
                    categoryId = state.categoryId,
                    durationMinutes = state.durationMinutes,
                    url = state.url,
                    description = state.description,
                    recurrenceRule = state.recurrenceRule,
                    editScope = state.editScope
                )
            }
            eventEditorState = null
        },
        onDelete = eventEditorState?.original?.let { original ->
            {
                if (original.recurrenceRule != null) {
                    pendingScopeAction = { scope ->
                        viewModel.deleteEvent(original, scope)
                        eventEditorState = null
                    }
                } else {
                    viewModel.deleteEvent(original)
                    eventEditorState = null
                }
            }
        },
        onDismiss = { eventEditorState = null }
    )

    pendingScopeAction?.let { runWithScope ->
        RecurrenceScopeChooser(
            onDismiss = { pendingScopeAction = null },
            onScopeSelected = { scope ->
                pendingScopeAction = null
                runWithScope(scope)
            }
        )
    }
}

@Composable
private fun CalendarTopBar(
    selectedDate: LocalDate,
    viewMode: CalendarViewMode,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    onBackClick: () -> Unit,
    onViewModeChange: (CalendarViewMode) -> Unit,
    categories: List<CalendarCategory>,
    onAddCategory: (name: String, colorHex: String) -> Unit,
    onUpdateCategory: (id: String, name: String, colorHex: String) -> Unit,
    onDeleteCategory: (id: String) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val defaultContentColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (isDesktopPlatform) 16.dp else 10.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.align(Alignment.Top)) {
            TopBarIconButton(
                icon = painterResource(Res.drawable.chevron_left),
                contentDescription = "Back",
                bgColor = Color.Transparent,
                tint = MaterialTheme.colorScheme.primary,
                hazeState = hazeState,
                hazeStyle = EmberrBlur.Regular,
                onClick = onBackClick
            )
        }

        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "CalendarTitleTransition",
            modifier = Modifier.height(72.dp)
        ) { date ->
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (viewMode == CalendarViewMode.MONTH) {
                    Text(
                        text = formatMonthYear(date),
                        style = MaterialTheme.typography.bodyLarge,
                        color = defaultContentColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = formatSelectedDateTitle(date),
                            style = MaterialTheme.typography.bodyLarge,
                            color = defaultContentColor,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = formatDayOfWeek(date),
                            style = MaterialTheme.typography.labelSmall,
                            color = defaultContentColor.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (isDesktopPlatform) {
            var showOptionsMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.align(Alignment.Top)) {
                TopBarIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    bgColor = Color.Transparent,
                    tint = MaterialTheme.colorScheme.primary,
                    hazeState = hazeState,
                    hazeStyle = EmberrBlur.Regular,
                    onClick = { showOptionsMenu = true }
                )
                EmberrDesktopMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                    modifier = Modifier.width(260.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp).padding(top = 6.dp)) {
                        ViewModeSection(
                            viewMode = viewMode,
                            onViewModeChange = {
                                onViewModeChange(it)
                                showOptionsMenu = false
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )

                        CategorySection(
                            categories = categories,
                            onAddCategory = onAddCategory,
                            onUpdateCategory = onUpdateCategory,
                            onDeleteCategory = onDeleteCategory
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun MultiDayHeaderBar(
    dates: List<LocalDate>,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    hazeState: HazeState,
    isScrolled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .then(
                if (isScrolled) {
                    Modifier
                        .emberrBlur(hazeState, EmberrBlur.Regular)
                        .background(Color.Transparent)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                }
            )
    ) {
        AnimatedContent(
            targetState = dates,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "MultiDayHeaderTransition"
        ) { windowDates ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(56.dp))
                windowDates.forEachIndexed { index, date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (windowDates.size >= 7) formatSingleLetterDayLabel(date) else formatShortDayLabel(date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    if (index != windowDates.lastIndex) {
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)
    }
}

internal fun formatFullDate(date: LocalDate): String {
    val monthAbbrev = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$monthAbbrev ${date.day}${ordinalSuffix(date.day)}, ${date.year}"
}

private fun formatSelectedDateTitle(date: LocalDate): String = formatFullDate(date)

private fun formatDayOfWeek(date: LocalDate): String =
    date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

private fun formatMonthYear(date: LocalDate): String {
    val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$monthName ${date.year}"
}

internal fun ordinalSuffix(day: Int): String = when {
    day in 11..13 -> "th"
    else -> when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

@Composable
private fun CalendarTimeGrid(
    selectedDate: LocalDate,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    events: List<CalendarEvent>,
    categories: List<CalendarCategory>,
    scrollState: ScrollState,
    topBarHeightDp: Dp,
    onSwipePreviousDay: () -> Unit,
    onSwipeNextDay: () -> Unit,
    onHourClick: (hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val nowMillis = rememberNowMillis()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDragPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragPx += dragAmount
                    },
                    onDragEnd = {
                        if (accumulatedDragPx <= -swipeThresholdPx) {
                            onSwipeNextDay()
                        } else if (accumulatedDragPx >= swipeThresholdPx) {
                            onSwipePreviousDay()
                        }
                        accumulatedDragPx = 0f
                    },
                    onDragCancel = { accumulatedDragPx = 0f }
                )
            }
    ) {
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "CalendarDayGridTransition",
            modifier = Modifier.fillMaxSize()
        ) { date ->
            DayHourGrid(
                date = date,
                events = events,
                categories = categories,
                nowMillis = nowMillis,
                scrollState = scrollState,
                topBarHeightDp = topBarHeightDp,
                onHourClick = onHourClick,
                onEventClick = onEventClick
            )
        }
    }
}

@Composable
private fun DayHourGrid(
    date: LocalDate,
    events: List<CalendarEvent>,
    categories: List<CalendarCategory>,
    nowMillis: Long,
    scrollState: ScrollState,
    topBarHeightDp: Dp,
    onHourClick: (hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit
) {
    val hourHeight = 72.dp
    val hours = remember { 0..23 }
    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .smoothWheelScroll(scrollState)
            .padding(top = topBarHeightDp, bottom = if (isDesktopPlatform) 0.dp else 56.dp)
    ) {
        HourLabelColumn(hours = hours, hourHeight = hourHeight)

        Box(modifier = Modifier.fillMaxWidth().height(hourHeight * hours.count())) {
            DayColumnBody(
                date = date,
                hours = hours,
                hourHeight = hourHeight,
                hourHeightPx = hourHeightPx,
                events = events,
                categories = categories,
                nowMillis = nowMillis,
                onHourClick = onHourClick,
                onEventClick = onEventClick
            )
        }
    }

        EmberrVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = topBarHeightDp, bottom = if (isDesktopPlatform) 0.dp else 56.dp)
        )
    }
}

@Composable
private fun HourLabelColumn(hours: IntRange, hourHeight: Dp) {
    Column(modifier = Modifier.width(56.dp)) {
        hours.forEach { hour ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hourHeight),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = formatHourLabel(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DayColumnBody(
    date: LocalDate,
    hours: IntRange,
    hourHeight: Dp,
    hourHeightPx: Float,
    events: List<CalendarEvent>,
    categories: List<CalendarCategory>,
    nowMillis: Long,
    onHourClick: (hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(hourHeight * hours.count())
            .pointerInput(hourHeightPx) {
                detectTapGestures { offset ->
                    val hour = (offset.y / hourHeightPx).toInt().coerceIn(0, 23)
                    onHourClick(hour)
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            hours.forEach { _ ->
                Box(modifier = Modifier.fillMaxWidth().height(hourHeight)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                }
            }
        }

        val horizontalInset = 6.dp
        val chipGap = 4.dp
        val availableWidth = maxWidth - horizontalInset * 2
        val positionedEvents = remember(events) { layoutEventsForColumn(events) }

        positionedEvents.forEach { positioned ->
            val event = positioned.event
            val dt = Instant.fromEpochMilliseconds(event.reminderTimestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val topOffset = hourHeight * dt.hour + hourHeight * (dt.minute / 60f)
            val chipHeight = maxOf(24.dp, hourHeight * (event.durationMinutes / 60f))
            val category = categories.firstOrNull { it.id == event.categoryId }

            val chipWidth = (availableWidth - chipGap * (positioned.columnCount - 1)) / positioned.columnCount
            val xOffset = horizontalInset + (chipWidth + chipGap) * positioned.columnIndex

            EventChip(
                text = event.text,
                color = category?.colorHex?.toCategoryColor() ?: MaterialTheme.colorScheme.surface,
                hasCategory = category != null,
                height = chipHeight,
                onClick = { onEventClick(event) },
                modifier = Modifier
                    .width(chipWidth)
                    .padding(top = topOffset)
                    .offset(x = xOffset)
            )
        }

        val nowDt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        if (date == nowDt.date) {
            val nowOffset = hourHeight * nowDt.hour + hourHeight * (nowDt.minute / 60f)
            CurrentTimeLine(modifier = Modifier.fillMaxWidth().padding(top = nowOffset))
        }
    }
}

@Composable
private fun rememberNowMillis(): Long {
    var nowMillis by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000.milliseconds)
            nowMillis = Clock.System.now().toEpochMilliseconds()
        }
    }
    return nowMillis
}

@Composable
private fun CurrentTimeLine(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private fun formatHourLabel(hour: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val period = if (hour < 12) "AM" else "PM"
    return "$displayHour $period"
}

private fun formatShortDayLabel(date: LocalDate): String {
    val shortDay = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$shortDay ${date.day}"
}

private fun formatSingleLetterDayLabel(date: LocalDate): String {
    val initial = date.dayOfWeek.name.take(1)
    return "$initial ${date.day}"
}

private fun LocalDate.startOfWeek(): LocalDate {
    val daysSinceSunday = dayOfWeek.isoDayNumber % 7
    return this.plus(-daysSinceSunday.toLong(), DateTimeUnit.DAY)
}

@Composable
private fun MultiDayTimeGrid(
    startDate: LocalDate,
    dayCount: Int,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    viewModel: CalendarViewModel,
    categories: List<CalendarCategory>,
    scrollState: ScrollState,
    topBarHeightDp: Dp,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    onHourClick: (date: LocalDate, hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val nowMillis = rememberNowMillis()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(dayCount) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDragPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragPx += dragAmount
                    },
                    onDragEnd = {
                        if (accumulatedDragPx <= -swipeThresholdPx) {
                            onSwipeNext()
                        } else if (accumulatedDragPx >= swipeThresholdPx) {
                            onSwipePrevious()
                        }
                        accumulatedDragPx = 0f
                    },
                    onDragCancel = { accumulatedDragPx = 0f }
                )
            }
    ) {
        AnimatedContent(
            targetState = startDate,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "MultiDayGridTransition",
            modifier = Modifier.fillMaxSize()
        ) { windowStart ->
            MultiDayGridContent(
                windowStart = windowStart,
                dayCount = dayCount,
                viewModel = viewModel,
                categories = categories,
                nowMillis = nowMillis,
                scrollState = scrollState,
                topBarHeightDp = topBarHeightDp,
                onHourClick = onHourClick,
                onEventClick = onEventClick
            )
        }
    }
}

@Composable
private fun MultiDayGridContent(
    windowStart: LocalDate,
    dayCount: Int,
    viewModel: CalendarViewModel,
    categories: List<CalendarCategory>,
    nowMillis: Long,
    scrollState: ScrollState,
    topBarHeightDp: Dp,
    onHourClick: (date: LocalDate, hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit
) {
    val dates = remember(windowStart, dayCount) {
        val anchor = if (dayCount == 7) windowStart.startOfWeek() else windowStart
        (0 until dayCount).map { offset -> anchor.plus(offset.toLong(), DateTimeUnit.DAY) }
    }
    val eventsByDate = dates.associateWith { date ->
        val state by remember(date) { viewModel.eventsForDate(date.toString()) }
            .collectAsState(initial = emptyList())
        state
    }

    val hourHeight = 72.dp
    val hours = remember { 0..23 }
    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    val headerHeight = 40.dp

    Box(modifier = Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .smoothWheelScroll(scrollState)
            .padding(top = topBarHeightDp + headerHeight)
    ) {
        HourLabelColumn(hours = hours, hourHeight = hourHeight)

        dates.forEachIndexed { index, date ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(hourHeight * hours.count())
            ) {
                DayColumnBody(
                    date = date,
                    hours = hours,
                    hourHeight = hourHeight,
                    hourHeightPx = hourHeightPx,
                    events = eventsByDate[date] ?: emptyList(),
                    categories = categories,
                    nowMillis = nowMillis,
                    onHourClick = { hour -> onHourClick(date, hour) },
                    onEventClick = onEventClick
                )
            }
            if (index != dates.lastIndex) {
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.height(hourHeight * hours.count())
                )
            }
        }
    }

        EmberrVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = topBarHeightDp + headerHeight)
        )
    }
}

private fun buildMonthGridDates(anchorMonth: LocalDate): List<LocalDate> {
    val firstOfMonth = LocalDate(anchorMonth.year, anchorMonth.month, 1)
    val leadingBlanks = firstOfMonth.dayOfWeek.isoDayNumber % 7
    val gridStart = firstOfMonth.plus(-leadingBlanks.toLong(), DateTimeUnit.DAY)
    val nextMonthFirst = firstOfMonth.plus(1, DateTimeUnit.MONTH)
    val daysInMonth = firstOfMonth.daysUntil(nextMonthFirst)
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
    return (0 until totalCells).map { offset -> gridStart.plus(offset.toLong(), DateTimeUnit.DAY) }
}

@Composable
private fun MonthGrid(
    anchorMonth: LocalDate,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    viewModel: CalendarViewModel,
    categories: List<CalendarCategory>,
    onSwipePreviousMonth: () -> Unit,
    onSwipeNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDragPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragPx += dragAmount
                    },
                    onDragEnd = {
                        if (accumulatedDragPx <= -swipeThresholdPx) {
                            onSwipeNextMonth()
                        } else if (accumulatedDragPx >= swipeThresholdPx) {
                            onSwipePreviousMonth()
                        }
                        accumulatedDragPx = 0f
                    },
                    onDragCancel = { accumulatedDragPx = 0f }
                )
            }
    ) {
        AnimatedContent(
            targetState = anchorMonth,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "MonthGridTransition",
            modifier = Modifier.fillMaxSize()
        ) { month ->
            MonthGridContent(
                anchorMonth = month,
                viewModel = viewModel,
                categories = categories,
                onDayClick = onDayClick
            )
        }
    }
}

@Composable
private fun MonthGridContent(
    anchorMonth: LocalDate,
    viewModel: CalendarViewModel,
    categories: List<CalendarCategory>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val gridDates = remember(anchorMonth) { buildMonthGridDates(anchorMonth) }
    val yearMonth = remember(anchorMonth) {
        "${anchorMonth.year}-${anchorMonth.month.number.toString().padStart(2, '0')}"
    }
    val monthEvents by remember(yearMonth) { viewModel.eventsForMonth(yearMonth) }
        .collectAsState(initial = emptyList())
    val eventsByDate = remember(monthEvents) { monthEvents.groupBy { it.dateString } }
    val weekRows = remember(gridDates) { gridDates.chunked(7) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = if (isDesktopPlatform) 0.dp else 56.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            weekRows.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    week.forEach { date ->
                        MonthDayCell(
                            date = date,
                            isCurrentMonth = date.month == anchorMonth.month,
                            isToday = date == today,
                            events = eventsByDate[date.toString()] ?: emptyList(),
                            categories = categories,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    events: List<CalendarEvent>,
    categories: List<CalendarCategory>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(26.dp)
                .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 4.dp).height(5.dp)
        ) {
            events.take(3).forEach { event ->
                val category = categories.firstOrNull { it.id == event.categoryId }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            category?.colorHex?.toCategoryColor() ?: MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CalendarBottomBar(
    hazeState: HazeState,
    isCompact: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    bottomBarAnimatedVisibilityScope: AnimatedVisibilityScope,
    onViewsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.primary

    val barAnimationSpec = tween<Dp>(durationMillis = 350, easing = FastOutSlowInEasing)
    val barSize by animateDpAsState(
        targetValue = if (isCompact) 44.dp else 52.dp,
        animationSpec = barAnimationSpec
    )
    val bottomInset by animateDpAsState(
        targetValue = if (isCompact) 0.dp else 6.dp,
        animationSpec = barAnimationSpec
    )
    val navItemHeight = barSize - 12.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = bottomInset, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val isMorphing = bottomBarAnimatedVisibilityScope.transition.isRunning
        val shadowElevation by animateDpAsState(
            targetValue = if (isMorphing) 0.dp else 14.dp,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        )
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier
                .wrapContentWidth()
                .height(barSize)
                .then(
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "calendarBottomBarPill"),
                            animatedVisibilityScope = bottomBarAnimatedVisibilityScope,
                            boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
                        )
                    }
                )
                .customEmberrShadow(CircleShape, elevation = shadowElevation)
                .clip(CircleShape)
                .emberrBlur(hazeState, EmberrBlur.Regular)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        ) {
            Row(
                modifier = with(sharedTransitionScope) {
                    Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .skipToLookaheadSize()
                },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarBottomBarItem(
                    icon = painterResource(Res.drawable.tablet),
                    contentDescription = "Views",
                    contentColor = contentColor,
                    modifier = Modifier.size(navItemHeight),
                    onClick = onViewsClick
                )
                CalendarBottomBarItem(
                    icon = painterResource(Res.drawable.widget2),
                    contentDescription = "Categories",
                    contentColor = contentColor,
                    modifier = Modifier.size(navItemHeight),
                    onClick = onCategoriesClick
                )
            }
        }
    }
}

@Composable
private fun CalendarBottomBarItem(
    icon: Painter,
    contentDescription: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun ViewModeSection(
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        if (isDesktopPlatform) {
            Text(
                text = "View",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp).padding(horizontal = 12.dp)
            )
        }

        ViewModeRow(
            label = "Day",
            isSelected = viewMode == CalendarViewMode.DAY,
            onClick = { onViewModeChange(CalendarViewMode.DAY) }
        )
        ViewModeRow(
            label = "3 Day",
            isSelected = viewMode == CalendarViewMode.THREE_DAY,
            onClick = { onViewModeChange(CalendarViewMode.THREE_DAY) }
        )
        ViewModeRow(
            label = "Week",
            isSelected = viewMode == CalendarViewMode.WEEK,
            onClick = { onViewModeChange(CalendarViewMode.WEEK) }
        )
        ViewModeRow(
            label = "Month",
            isSelected = viewMode == CalendarViewMode.MONTH,
            onClick = { onViewModeChange(CalendarViewMode.MONTH) }
        )
    }
}

@Composable
private fun ViewModeRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isDesktopPlatform) 0.dp else 12.dp,
                vertical = 2.dp
            )
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) SelectedOptionBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isDesktopPlatform) 12.dp else 14.dp,
                vertical = if (isDesktopPlatform) 10.dp else 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}


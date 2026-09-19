package com.emberr.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.navigation.Screen
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.ui.theme.LocalEmberrFontStyle
import com.emberr.ui.theme.fontFamilyFor
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.astroid
import emberr.shared.generated.resources.daily
import emberr.shared.generated.resources.house
import emberr.shared.generated.resources.microphone
import emberr.shared.generated.resources.search
import emberr.shared.generated.resources.x
import org.jetbrains.compose.resources.painterResource

internal fun Modifier.customEmberrShadow(shape: Shape, elevation: Dp = 14.dp): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    spotColor = Color.Black.copy(alpha = 0.35f),
    ambientColor = Color.Black.copy(alpha = 0.20f)
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EmberrBottomBar(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    hazeState: HazeState,
    sharedTransitionScope: SharedTransitionScope,
    bottomBarAnimatedVisibilityScope: AnimatedVisibilityScope,
    currentRoute: String?,
    activeTab: String,
    onSearchClick: () -> Unit,
    onMicClick: () -> Unit,
    onAiIconTap: () -> Unit = {},
    isAiEnabled: Boolean = true,
    isListening: Boolean = false,
    partialText: String = "",
    isCompact: Boolean = false,
    isSearchMode: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {}
) {
    val defaultBgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
    val defaultContentColor = MaterialTheme.colorScheme.onSurface

    val barAnimationSpec = tween<Dp>(durationMillis = 350, easing = FastOutSlowInEasing)
    val barSize by animateDpAsState(
        targetValue = if (isCompact && !isSearchMode) 44.dp else 52.dp,
        animationSpec = barAnimationSpec
    )
    val bottomInset by animateDpAsState(
        targetValue = if (isCompact && !isSearchMode) 0.dp else 6.dp,
        animationSpec = barAnimationSpec
    )
    val horizontalInset by animateDpAsState(
        targetValue = when {
            isSearchMode -> 0.dp
            isCompact -> 24.dp
            else -> 12.dp
        },
        animationSpec = barAnimationSpec
    )
    val navItemHeight = barSize - 12.dp

    // The bar only rides the keyboard while searching, and keeps riding it on the way out, so
    // closing search lets it sink back down with the keyboard instead of snapping to the bottom
    // while the keyboard is still on screen. snapshotFlow watches the inset without pulling the
    // per-frame keyboard animation into composition.
    val density = LocalDensity.current
    val keyboardInsets = WindowInsets.ime
    val navigationBarInsets = WindowInsets.navigationBars
    var isFollowingKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            isFollowingKeyboard = true
        } else {
            snapshotFlow { keyboardInsets.getBottom(density) }.first { it == 0 }
            isFollowingKeyboard = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // offset, not imePadding: this must move the bar without changing its measured
            // height, which the screens behind it use as their bottom content padding.
            .offset {
                val keyboardLift = keyboardInsets.getBottom(this) - navigationBarInsets.getBottom(this)
                IntOffset(0, if (isFollowingKeyboard) -keyboardLift.coerceAtLeast(0) else 0)
            }
            .navigationBarsPadding()
            .padding(bottom = bottomInset, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = currentRoute != Screen.Note.route,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(300)),
            modifier = Modifier.fillMaxWidth()
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
                    .fillMaxWidth()
                    .padding(horizontal = horizontalInset)
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
                AnimatedContent(
                    targetState = isSearchMode,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    modifier = with(sharedTransitionScope) {
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .skipToLookaheadSize()
                    },
                    label = "bottom_bar_search_morph"
                ) { showSearchField ->
                    if (showSearchField) {
                        BottomBarSearchField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onClose = onCloseSearch
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomNavItem(
                                icon = painterResource(Res.drawable.daily),
                                isSelected = activeTab == Screen.Daily.route,
                                modifier = Modifier.weight(1f).height(navItemHeight)
                            ) {
                                if (currentRoute != Screen.Daily.route) navController.navigate(Screen.Daily.createRoute()) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            BottomNavItem(
                                icon = painterResource(Res.drawable.house),
                                isSelected = activeTab == Screen.Home.route,
                                modifier = Modifier.weight(1f).height(navItemHeight)
                            ) {
                                if (currentRoute != Screen.Home.route) navController.navigate(Screen.Home.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            if (isAiEnabled) {
                                BottomNavItem(
                                    icon = painterResource(Res.drawable.astroid),
                                    isSelected = false,
                                    modifier = Modifier.weight(1f).height(navItemHeight),
                                    iconModifier = with(sharedTransitionScope) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "aiIcon"),
                                            animatedVisibilityScope = bottomBarAnimatedVisibilityScope,
                                            boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
                                        )
                                    },
                                    onClick = onAiIconTap
                                )
                            }
                            BottomNavItem(
                                icon = painterResource(Res.drawable.search),
                                isSelected = false,
                                modifier = Modifier.weight(1f).height(navItemHeight),
                                onClick = onSearchClick
                            )
                            if (!isDesktopPlatform) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isListening) defaultContentColor else Color.Transparent,
                                    contentColor = if (isListening) MaterialTheme.colorScheme.background else defaultContentColor.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(navItemHeight)
                                        .clip(CircleShape)
                                        .clickable { onMicClick() }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(painterResource(Res.drawable.microphone), "Mic", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isDesktopPlatform) {
            AnimatedVisibility(
                visible = isListening || partialText.isNotEmpty(),
                enter = fadeIn(tween(200)) + expandHorizontally(
                    expandFrom = Alignment.CenterHorizontally,
                    animationSpec = tween(200)
                ),
                exit = fadeOut(tween(200)) + shrinkHorizontally(
                    shrinkTowards = Alignment.CenterHorizontally,
                    animationSpec = tween(200)
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(barSize + 8.dp))
                    .wrapContentWidth(unbounded = true, align = Alignment.CenterHorizontally)
            ) {
                Surface(
                    shape = RoundedCornerShape(100f),
                    color = defaultBgColor,
                    contentColor = defaultContentColor,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .clip(RoundedCornerShape(100f))
                        .emberrBlur(hazeState, EmberrBlur.Regular)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                ) {
                    Text(
                        text = partialText.ifBlank { "Listening..." },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontFamily = fontFamilyFor(LocalEmberrFontStyle.current),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Runs once when the field enters the composition, which is exactly when search mode opens -
    // asking for focus here is what raises the keyboard, and the keyboard inset then lifts the bar.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search all notes",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(remember { MutableInteractionSource() }, null) {
                    if (query.isEmpty()) onClose() else onQueryChange("")
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.x),
                contentDescription = if (query.isEmpty()) "Close search" else "Clear search",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: Painter,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }
    val iconColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(0.6f)

    Surface(
        shape = CircleShape,
        color = bgColor,
        contentColor = iconColor,
        border = BorderStroke(1.dp, Color.Transparent),
        modifier = modifier
            .clip(CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp).then(iconModifier))
        }
    }
}
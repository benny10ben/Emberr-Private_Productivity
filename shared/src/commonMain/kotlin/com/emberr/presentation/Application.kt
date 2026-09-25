package com.emberr.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.emberr.domain.util.eventbus.AiEventBus
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.mobile.daily.DailyScreen
import com.emberr.presentation.navigation.Screen
import com.emberr.presentation.onboarding.OnboardingScreen
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.components.LocalEmberrBlurSource
import com.emberr.presentation.trash.TrashScreen
import dev.chrisbanes.haze.HazeState
import com.emberr.presentation.splash.LoadingScreen
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.repository.EmojiRepository
import com.emberr.domain.util.system.AppPermission
import com.emberr.domain.util.system.rememberAppPermissionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.emberr.presentation.mobile.home.HomeScreen
import com.emberr.presentation.mobile.voice.VoiceTaskDialog
import com.emberr.presentation.mobile.voice.VoiceTaskViewModel
import com.emberr.presentation.search.SearchResultsList
import com.emberr.presentation.search.SearchViewModel
import com.emberr.presentation.share.ShareReceiverSheet
import com.emberr.presentation.share.ShareViewModel
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res.readBytes
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private val DESKTOP_SIDEBAR_WIDTH = 340.dp
private const val ONBOARDING_OVERLAY_FADE_MILLIS = 650
private const val ONBOARDING_SWAP_FADE_OUT_MILLIS = 300
private const val ONBOARDING_SWAP_FADE_IN_MILLIS = 380

private val SEARCH_BAR_RESERVED_HEIGHT = EXPANDED_BOTTOM_BAR_PILL_HEIGHT + BOTTOM_BAR_BOTTOM_PADDING + 6.dp

val LocalImageOverlay = staticCompositionLocalOf<( (@Composable () -> Unit)? ) -> Unit> { {} }

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun EmberrApp(
    startRoute: String,
    HomeViewModel: com.emberr.presentation.mobile.home.HomeViewModel = koinViewModel(),
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onExportMarkdown: (fileName: String, content: String) -> Unit = { _, _ -> },
    onExportBackup: () -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onRequestBackupFolder: () -> Unit,
    onExportPdf: (fileName: String, title: String, blocks: List<NoteBlock>) -> Unit = { _, _, _ -> },
    onExitApp: () -> Unit = {}
) {

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val bytes = readBytes("files/data-by-group.json")
                EmojiRepository.initialize(bytes.decodeToString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        com.emberr.domain.util.eventbus.WidgetNavigationBus.requestedRoutes.collect { requestedRoute ->
            com.emberr.domain.util.eventbus.WidgetNavigationBus.consumeRequestedRoute()
            try {
                val openEntry = navController.currentBackStackEntry
                val openRoutePattern = openEntry?.destination?.route
                val isAlreadyOpen = if (openRoutePattern == Screen.Note.route) {
                    requestedRoute == Screen.Note.createRoute(
                        openEntry.savedStateHandle.get<String>("noteId").orEmpty()
                    )
                } else {
                    openRoutePattern == requestedRoute
                }

                val isDailyRoute = requestedRoute.startsWith(Screen.Daily.createRoute())

                if (isDailyRoute) {
                    navController.navigate(requestedRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                } else if (!isAlreadyOpen) {
                    navController.navigate(requestedRoute) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            } catch (cause: Exception) {
                cause.printStackTrace()
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val voiceTaskViewModel: VoiceTaskViewModel = koinViewModel()
    val voiceTaskSessionState by voiceTaskViewModel.sessionState.collectAsState()
    var isVoiceTaskDialogOpen by rememberSaveable { mutableStateOf(false) }

    val hazeState = remember { HazeState() }
    val density = LocalDensity.current

    val micPermissionCoordinator = rememberAppPermissionCoordinator()
    val hasMicPermission = micPermissionCoordinator.isGranted(AppPermission.Microphone)
    var isMicPermissionPending by remember { mutableStateOf(false) }

    LaunchedEffect(hasMicPermission) {
        if (isMicPermissionPending && hasMicPermission) {
            isMicPermissionPending = false
            voiceTaskViewModel.startListening()
        }
    }

    val requestMicPermission: () -> Unit = {
        if (hasMicPermission) {
            voiceTaskViewModel.startListening()
        } else {
            isMicPermissionPending = true
            micPermissionCoordinator.request(AppPermission.Microphone)
        }
    }

    var activeTab by remember { mutableStateOf(Screen.Daily.route) }
    var isBottomBarCompact by remember { mutableStateOf(false) }

    // Mobile search: the bottom bar turns into the search field, and the moment there is a query
    // the results take over the screen above it.
    val searchViewModel: SearchViewModel = koinViewModel()
    val searchQuery by searchViewModel.query.collectAsState()
    val searchResults by searchViewModel.results.collectAsState()
    var isSearchBarOpen by remember { mutableStateOf(false) }
    val areSearchResultsVisible = isSearchBarOpen && searchQuery.isNotBlank()
    val closeSearchBar: () -> Unit = {
        isSearchBarOpen = false
        isBottomBarCompact = false
        searchViewModel.onQueryChange("")
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Daily.route || currentRoute == Screen.Home.route) {
            activeTab = currentRoute
        }
        isBottomBarCompact = false
    }

    // AI chat ViewModel
    val ragViewModel: com.emberr.presentation.ai.RagViewModel = koinViewModel()

    val settingsManager = koinInject<com.emberr.data.local.prefs.SettingsManager>()
    val isAiDisabled by settingsManager.aiFeaturesDisabledFlow.collectAsState(
        initial = settingsManager.isAiFeaturesDisabled()
    )

    // Controls the AI chat overlay
    var showAiChatOverlay by remember { mutableStateOf(false) }

    var isSelectionActive by remember { mutableStateOf(false) }

    val isTopLevelScreen = currentRoute == Screen.Daily.route ||
            currentRoute == Screen.Home.route ||
            currentRoute == Screen.Note.route
    val isBottomBarVisible = isTopLevelScreen && !isSelectionActive
    val navigationBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val expectedBottomBarHeight = 58.dp + navigationBarBottomInset
    var measuredBottomBarHeight by remember { mutableStateOf<Dp?>(null) }
    val bottomBarHeightDp = measuredBottomBarHeight ?: expectedBottomBarHeight
    var suppressBottomBarEnterAnimation by remember { mutableStateOf(true) }
    LaunchedEffect(isBottomBarVisible) {
        if (isBottomBarVisible) suppressBottomBarEnterAnimation = false
    }

    var isSidebarVisible by remember { mutableStateOf(true) }

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

    val aiChatCoroutineScope = rememberCoroutineScope()
    var pendingAiChatClearJob by remember { mutableStateOf<Job?>(null) }

    val dismissAiChat: () -> Unit = {
        showAiChatOverlay = false
        pendingAiChatClearJob?.cancel()
        pendingAiChatClearJob = aiChatCoroutineScope.launch {
            delay(2000.milliseconds)
            ragViewModel.clearChat()
        }
    }

    val openAiChat: () -> Unit = {
        if (isAiDisabled) {
            if (showAiChatOverlay) dismissAiChat()
        } else if (isDesktopPlatform) {
            if (showAiChatOverlay) {
                dismissAiChat()
            } else {
                pendingAiChatClearJob?.cancel()
                pendingAiChatClearJob = null
                ragViewModel.clearChat()
                AiEventBus.requestImmediateIndex()
                showAiChatOverlay = true
            }
        } else {
            if (currentRoute == Screen.AiChat.route) {
                navController.popBackStack()
            } else {
                pendingAiChatClearJob?.cancel()
                pendingAiChatClearJob = null
                ragViewModel.clearChat()
                AiEventBus.requestImmediateIndex()
                navController.navigate(Screen.AiChat.route)
            }
        }
    }

    LaunchedEffect(isAiDisabled) {
        if (isAiDisabled) {
            showAiChatOverlay = false
            if (!isDesktopPlatform && currentRoute == Screen.AiChat.route) navController.popBackStack()
            ragViewModel.clearChat()
        }
    }

    var fullScreenContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    var isStatusBarInsetReady by remember { mutableStateOf(isDesktopPlatform) }
    if (!isDesktopPlatform) {
        val statusBarInsetTopPx = WindowInsets.statusBars.getTop(density)
        LaunchedEffect(statusBarInsetTopPx) {
            if (statusBarInsetTopPx > 0) isStatusBarInsetReady = true
        }
        LaunchedEffect(Unit) {
            delay(500.milliseconds)
            isStatusBarInsetReady = true
        }
    }

    CompositionLocalProvider(
        LocalImageOverlay provides { content -> fullScreenContent = content },
        LocalEmberrBlurSource provides if (isDesktopPlatform) null else hazeState
    ) {
        if (isDesktopPlatform) {
            var isOnboardingCompleted by remember { mutableStateOf(settingsManager.isOnboardingCompleted()) }
            var hasSplashFinished by remember { mutableStateOf(false) }
            var hasFirstFrameRendered by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                withFrameNanos { }
                hasFirstFrameRendered = true
            }

            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
                    targetState = hasFirstFrameRendered && isOnboardingCompleted,
                    transitionSpec = {
                        fadeIn(
                            tween(
                                ONBOARDING_SWAP_FADE_IN_MILLIS,
                                delayMillis = ONBOARDING_SWAP_FADE_OUT_MILLIS
                            )
                        ) togetherWith fadeOut(tween(ONBOARDING_SWAP_FADE_OUT_MILLIS))
                    },
                    label = "onboarding-handoff"
                ) { showsMainScreen ->
                    if (showsMainScreen) {
                        DesktopMainScreenWrapper(
                            isSidebarVisible = isSidebarVisible,
                            sidebarWidth = DESKTOP_SIDEBAR_WIDTH,
                            onToggleSidebar = { isSidebarVisible = !isSidebarVisible },
                            onSelectionModeChange = { isActive -> isSelectionActive = isActive },
                            onPickImage = onPickImage,
                            onTakePhoto = onTakePhoto,
                            onPickDocument = onPickDocument,
                            onOpenFile = onOpenFile,
                            onExportMarkdown = onExportMarkdown,
                            onExportPdf = onExportPdf,
                            onExportBackup = onExportBackup,
                            onImportBackupClick = onImportBackupClick,
                            onAiIconTap = openAiChat,
                            isAiChatVisible = showAiChatOverlay,
                            ragViewModel = ragViewModel,
                            onDismissAiChat = dismissAiChat
                        )
                    } else if (hasFirstFrameRendered) {
                        OnboardingScreen(onFinished = { isOnboardingCompleted = true })
                    }
                }

                if (!hasSplashFinished) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { change -> change.consume() }
                                    }
                                }
                            }
                    ) {
                        LoadingScreen(onLoadingComplete = { hasSplashFinished = true })
                    }
                }

                fullScreenContent?.invoke()
            }
            return@CompositionLocalProvider
        }

        if (!isStatusBarInsetReady) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            )
            return@CompositionLocalProvider
        }

        val shareViewModel: ShareViewModel = koinViewModel()
        val currentShare by shareViewModel.currentShare.collectAsState()
        val linkableNotes by shareViewModel.linkableNotes.collectAsState()
        val shareNavigateToNoteId by shareViewModel.navigateToNoteId.collectAsState()

        LaunchedEffect(shareNavigateToNoteId) {
            shareNavigateToNoteId?.let { id ->
                navController.navigate(Screen.Note.createRoute(id))
                shareViewModel.clearNavigation()
            }
        }

        var isOnboardingCompleted by remember {
            mutableStateOf(settingsManager.isOnboardingCompleted())
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0)
        ) { innerPadding ->
            SharedTransitionLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val sharedTransitionScope = this@SharedTransitionLayout

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nestedScroll(bottomBarNestedScrollConnection)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startRoute, // Updated to use the parameter
                        modifier = Modifier
                            .padding(top = innerPadding.calculateTopPadding())
                            .consumeWindowInsets(innerPadding)
                            .hazeSource(state = hazeState),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None }
                    ) {

                        // Unconditionally added the Splash route to prevent "destination not found" crashes
                        composable(Screen.Splash.route) {
                            LoadingScreen(
                                onLoadingComplete = {
                                    navController.navigate(Screen.Daily.createRoute()) {
                                        popUpTo(Screen.Splash.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = Screen.Daily.route,
                            arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true })
                        ) { backStackEntry ->
                            DailyScreen(
                                bottomContentPadding = if (isBottomBarVisible) bottomBarHeightDp else 0.dp,
                                onSelectionModeChange = { isActive -> isSelectionActive = isActive },
                                onPickImage = onPickImage,
                                onTakePhoto = onTakePhoto,
                                onPickDocument = onPickDocument,
                                onOpenFile = onOpenFile,
                                onNavigateToEditor = { noteId ->
                                    navController.navigate(Screen.Note.createRoute(noteId))
                                },
                                onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                onNavigateToTrash = { navController.navigate("trash_route") },
                                isSearchActive = isSearchBarOpen,
                                dateArg = backStackEntry.savedStateHandle.get<String>("date")
                            )
                        }

                        composable(Screen.Home.route) {
                            HomeScreen(
                                bottomContentPadding = if (isBottomBarVisible) bottomBarHeightDp else 0.dp,
                                onNavigateToEditor = { noteId ->
                                    navController.navigate(
                                        Screen.Note.createRoute(
                                            noteId
                                        )
                                    )
                                },
                                onSelectionModeChange = { isActive -> isSelectionActive = isActive },
                                onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                onNavigateToReminders = { navController.navigate(Screen.Reminders.route) },
                                onNavigateToBookmarks = { navController.navigate(Screen.Bookmarks.route) },
                                onNavigateToImages = { navController.navigate(Screen.Images.route) },
                                onNavigateToDocuments = { navController.navigate(Screen.Documents.route) },
                                onNavigateToTrash = { navController.navigate("trash_route") },
                                onToggleSidebar = { isSidebarVisible = !isSidebarVisible },
                                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                            )
                        }

                        composable(
                            route = "trash_route",
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            TrashScreen(onNavigateBack = { navController.popBackStack() })
                        }

                        composable(
                            route = Screen.Note.route,
                            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                if (targetState.destination.route == Screen.Note.route) {
                                    ExitTransition.None
                                } else {
                                    slideOutOfContainer(
                                        AnimatedContentTransitionScope.SlideDirection.Left,
                                        tween(300)
                                    )
                                }
                            },
                            popEnterTransition = {
                                if (initialState.destination.route == Screen.Note.route) {
                                    EnterTransition.None
                                } else {
                                    slideIntoContainer(
                                        AnimatedContentTransitionScope.SlideDirection.Right,
                                        tween(300)
                                    )
                                }
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) { backStackEntry ->
                            com.emberr.presentation.mobile.home.note.NoteScreen(
                                noteId = backStackEntry.savedStateHandle.get<String>("noteId") ?: "",
                                onNavigateBack = { if (!navController.popBackStack()) onExitApp() },
                                onNavigateToEditor = { subNoteId ->
                                    navController.navigate(Screen.Note.createRoute(subNoteId))
                                },
                                onSelectionModeChange = { isActive -> isSelectionActive = isActive },
                                onPickImage = onPickImage,
                                onTakePhoto = onTakePhoto,
                                onPickDocument = onPickDocument,
                                onOpenFile = onOpenFile,
                                onExportMarkdown = onExportMarkdown,
                                onExportPdf = onExportPdf,
                                isSearchActive = isSearchBarOpen
                            )
                        }

                        composable(
                            route = Screen.Reminders.route,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            com.emberr.presentation.mobile.home.overview.tasks.TasksScreen(
                                onNavigateBack = { if (!navController.popBackStack()) onExitApp() },
                                onNavigateToEditor = { noteId ->
                                    navController.navigate(
                                        Screen.Note.createRoute(
                                            noteId
                                        )
                                    )
                                }
                            )
                        }

                        composable(
                            route = Screen.Calendar.route,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            com.emberr.presentation.calendar.CalendarScreen(
                                onNavigateBack = { navController.popBackStack() },
                                sharedTransitionScope = sharedTransitionScope,
                                bottomBarAnimatedVisibilityScope = this,
                            )
                        }

                        composable(
                            route = Screen.Bookmarks.route,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            com.emberr.presentation.mobile.home.overview.bookmarks.BookmarksScreen(
                                onNavigateBack = { navController.popBackStack() })
                        }

                        composable(
                            route = Screen.Images.route,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            val imagesViewModel: com.emberr.presentation.mobile.home.overview.images.ImagesViewModel =
                                koinViewModel()
                            com.emberr.presentation.mobile.home.overview.images.ImagesScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onTriggerImagePicker = {
                                    onPickImage { path -> imagesViewModel.createNewImageWithFile(path) }
                                },
                                viewModel = imagesViewModel
                            )
                        }

                        composable(
                            route = Screen.Documents.route,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            val documentsViewModel: com.emberr.presentation.mobile.home.overview.documents.DocumentsViewModel =
                                koinViewModel()
                            com.emberr.presentation.mobile.home.overview.documents.DocumentsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onTriggerDocumentPicker = {
                                    onPickDocument { path ->
                                        documentsViewModel.createNewDocumentWithFile(
                                            path
                                        )
                                    }
                                },
                                onOpenFile = onOpenFile,
                                viewModel = documentsViewModel
                            )
                        }

                        composable(
                            route = Screen.Settings.route,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                if (targetState.destination.route == Screen.SelfHostSetup.route) {
                                    ExitTransition.None
                                } else {
                                    slideOutOfContainer(
                                        AnimatedContentTransitionScope.SlideDirection.Left,
                                        tween(300)
                                    )
                                }
                            },
                            popEnterTransition = {
                                if (initialState.destination.route == Screen.SelfHostSetup.route) {
                                    EnterTransition.None
                                } else {
                                    slideIntoContainer(
                                        AnimatedContentTransitionScope.SlideDirection.Right,
                                        tween(300)
                                    )
                                }
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            com.emberr.presentation.settings.SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onExportReady = onExportBackup,
                                onImportClick = onImportBackupClick,
                                onRequestBackupFolder = onRequestBackupFolder,
                                onNavigateToSelfHostSetup = { navController.navigate(Screen.SelfHostSetup.route) }
                            )
                        }

                        composable(
                            route = Screen.SelfHostSetup.route,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            com.emberr.presentation.settings.selfhost.SelfHostSetupScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.AiChat.route,
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        ) {
                            DisposableEffect(Unit) {
                                onDispose {
                                    pendingAiChatClearJob?.cancel()
                                    pendingAiChatClearJob = aiChatCoroutineScope.launch {
                                        delay(2000.milliseconds)
                                        ragViewModel.clearChat()
                                    }
                                }
                            }

                            com.emberr.presentation.ai.AiChatScreen(
                                onDismiss = { navController.popBackStack() },
                                viewModel = ragViewModel,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = this,
                                onPickDocument = onPickDocument
                            )
                        }
                    }
                    // Search results sit above the screen content but below the bottom bar, so
                    // the bar stays usable while everything behind it is covered. The no-op
                    // clickable is what stops taps falling through to the hidden screen.
                    AnimatedVisibility(
                        visible = areSearchResultsVisible,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { }
                                .statusBarsPadding()
                                .imePadding()
                                .navigationBarsPadding()
                                .padding(bottom = SEARCH_BAR_RESERVED_HEIGHT)
                        ) {
                            SearchResultsList(
                                query = searchQuery,
                                results = searchResults,
                                onNoteClick = { noteId ->
                                    closeSearchBar()
                                    navController.navigate(Screen.Note.createRoute(noteId))
                                },
                                onDailyNoteClick = { dateString ->
                                    closeSearchBar()
                                    navController.navigate(Screen.Daily.createRoute(dateString)) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
                            )
                        }
                    }

                    KmpBackHandler(enabled = isSearchBarOpen) { closeSearchBar() }

                    if (!isDesktopPlatform) {
                        AnimatedVisibility(
                            visible = isBottomBarVisible,
                            enter = if (suppressBottomBarEnterAnimation) {
                                EnterTransition.None
                            } else {
                                slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = tween(
                                        durationMillis = 250,
                                        delayMillis = 100,
                                        easing = FastOutSlowInEasing
                                    )
                                ) + fadeIn(tween(durationMillis = 250, delayMillis = 100))
                            },
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeOut(tween(durationMillis = 200)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .onGloballyPositioned { coords ->
                                    measuredBottomBarHeight = with(density) { coords.size.height.toDp() }
                                }
                        ) {
                            EmberrBottomBar(
                                navController = navController,
                                hazeState = hazeState,
                                sharedTransitionScope = sharedTransitionScope,
                                bottomBarAnimatedVisibilityScope = this,
                                currentRoute = currentRoute,
                                activeTab = activeTab,
                                onAiIconTap = openAiChat,
                                isAiEnabled = !isAiDisabled,
                                onSearchClick = { isSearchBarOpen = true },
                                isSearchMode = isSearchBarOpen,
                                searchQuery = searchQuery,
                                onSearchQueryChange = searchViewModel::onQueryChange,
                                onCloseSearch = closeSearchBar,
                                onMicClick = {
                                    isVoiceTaskDialogOpen = true
                                    voiceTaskViewModel.startListening(
                                        onPermissionNeeded = { requestMicPermission() }
                                    )
                                },
                                isListening = voiceTaskSessionState.isListening,
                                isCompact = isBottomBarCompact
                            )
                        }
                    }


                    if (isVoiceTaskDialogOpen) {
                        VoiceTaskDialog(
                            state = voiceTaskSessionState,
                            onOrbClick = {
                                voiceTaskViewModel.startListening(
                                    onPermissionNeeded = { requestMicPermission() }
                                )
                            },
                            onTaskEditStart = voiceTaskViewModel::stopListening,
                            onTaskTextChange = voiceTaskViewModel::editTask,
                            onTaskReminderChange = voiceTaskViewModel::setTaskReminder,
                            onAdd = {
                                voiceTaskViewModel.addTasks()
                                isVoiceTaskDialogOpen = false
                            },
                            onDiscard = {
                                voiceTaskViewModel.discardTasks()
                                isVoiceTaskDialogOpen = false
                            }
                        )
                    }

                    ShareReceiverSheet(
                        share = currentShare,
                        linkableNotes = linkableNotes,
                        onSaveToInbox = { shareViewModel.saveToInbox() },
                        onNoteSelected = { noteId -> shareViewModel.saveToNote(noteId) },
                        onCreateNote = { title -> shareViewModel.createNoteAndSave(title) },
                        onCreateBlankNote = { shareViewModel.createNoteAndSave("") },
                        onDismiss = { shareViewModel.dismiss() }
                    )

                    fullScreenContent?.invoke()

                    AnimatedVisibility(
                        visible = !isOnboardingCompleted,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(ONBOARDING_OVERLAY_FADE_MILLIS)),
                        modifier = Modifier
                            .zIndex(20f)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent()
                                    }
                                }
                            }
                    ) {
                        OnboardingScreen(onFinished = { isOnboardingCompleted = true })
                    }
                }
            }
        }
    }
}
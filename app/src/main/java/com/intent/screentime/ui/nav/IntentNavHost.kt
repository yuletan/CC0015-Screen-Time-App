package com.intent.screentime.ui.nav

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.intent.screentime.core.di.AppContainer
import com.intent.screentime.data.apps.InstalledAppsProvider
import com.intent.screentime.ui.IntentViewModelFactory
import com.intent.screentime.ui.appdetail.AppDetailScreen
import com.intent.screentime.ui.appdetail.AppDetailViewModel
import com.intent.screentime.ui.apps.AppsScreen
import com.intent.screentime.ui.apps.AppsViewModel
import com.intent.screentime.ui.components.SetCapDialog
import com.intent.screentime.ui.daycard.DayCardScreen
import com.intent.screentime.ui.daycard.DayCardViewModel
import com.intent.screentime.ui.focus.FocusScreen
import com.intent.screentime.ui.focus.FocusViewModel
import com.intent.screentime.ui.goals.GoalsScreen
import com.intent.screentime.ui.goals.GoalsViewModel
import com.intent.screentime.ui.insights.InsightsScreen
import com.intent.screentime.ui.insights.InsightsViewModel
import com.intent.screentime.ui.settings.CategoryEditorScreen
import com.intent.screentime.ui.settings.CategoryEditorViewModel
import com.intent.screentime.ui.settings.IntentPromptScreen
import com.intent.screentime.ui.settings.IntentPromptViewModel
import com.intent.screentime.ui.settings.SettingsScreen
import com.intent.screentime.ui.settings.SettingsViewModel
import com.intent.screentime.ui.status.TrackingStatusScreen
import com.intent.screentime.ui.today.TodayScreen
import com.intent.screentime.ui.today.TodayViewModel
import com.intent.screentime.ui.triage.TriageScreen
import com.intent.screentime.ui.triage.TriageViewModel

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Today("today", "Today", Icons.Filled.Home),
    Apps("apps", "Apps", Icons.Filled.Apps),
    Focus("focus", "Focus", Icons.Filled.Timer),
    Goals("goals", "Goals", Icons.Filled.EmojiEvents),
    Insights("insights", "Insights", Icons.Filled.Insights),
}

private const val DIAGNOSTICS_ROUTE = "diagnostics"
private const val CATEGORY_EDITOR_ROUTE = "categories"
private const val APP_DETAIL_ROUTE = "app"
private const val APP_DETAIL_ARG = "packageName"

/** The triage sheet. A route rather than a dialog so the nightly digest can deep-link it. */
private const val TRIAGE_ROUTE = "triage"

/** One past day's card, reached by tapping its square on the heatmap. */
private const val DAY_CARD_ROUTE = "day"
private const val DAY_CARD_ARG = "epochDay"

/** Settings is pushed from Today rather than living in the bar: five tabs is already the limit. */
private const val SETTINGS_ROUTE = "settings"
private const val INTENT_PROMPT_ROUTE = "intent-prompt"

@Composable
fun IntentNavHost(
    container: AppContainer,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    deepLinkRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // A route from a notification, opened once the graph exists and then consumed, so a
    // recomposition never re-navigates. Only routes that already exist are ever sent.
    LaunchedEffect(deepLinkRoute) {
        if (!deepLinkRoute.isNullOrBlank()) {
            navController.navigate(deepLinkRoute)
            onDeepLinkConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Single-instance tabs that remember their scroll position.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(destination.icon, contentDescription = destination.label)
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Today.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Today.route) {
                val vm: TodayViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        TodayViewModel(container.usageRepository, container.appInfoProvider)
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()
                val refreshing by vm.refreshing.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) { vm.refreshIfStale() }

                TodayScreen(
                    state = state,
                    refreshing = refreshing,
                    appInfo = container.appInfoProvider,
                    onRefresh = vm::refresh,
                    onSetCap = vm::setCap,
                    onOpenApps = { navController.navigate(Destination.Apps.route) },
                    onOpenTriage = { navController.navigate(TRIAGE_ROUTE) },
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }

            composable(Destination.Apps.route) {
                val vm: AppsViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        AppsViewModel(container.usageRepository, container.appInfoProvider)
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                AppsScreen(
                    state = state,
                    appInfo = container.appInfoProvider,
                    onOpenApp = { packageName ->
                        navController.navigate("$APP_DETAIL_ROUTE/$packageName")
                    },
                    onSortCategories = { navController.navigate(TRIAGE_ROUTE) },
                )
            }

            composable(
                route = "$APP_DETAIL_ROUTE/{$APP_DETAIL_ARG}",
                arguments = listOf(navArgument(APP_DETAIL_ARG) { type = NavType.StringType }),
            ) { entry ->
                val packageName = entry.arguments?.getString(APP_DETAIL_ARG).orEmpty()
                val vm: AppDetailViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        AppDetailViewModel(
                            packageName = packageName,
                            repository = container.usageRepository,
                            appInfo = container.appInfoProvider,
                        )
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                AppDetailScreen(
                    state = state,
                    appInfo = container.appInfoProvider,
                    onBack = { navController.popBackStack() },
                    onSetCap = vm::setCap,
                    onSetCategory = vm::setCategory,
                )
            }

            composable(Destination.Focus.route) {
                val vm: FocusViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        FocusViewModel(
                            repository = container.usageRepository,
                            manager = container.focusManager,
                            onStart = container::startFocusSession,
                            onCancel = container::cancelFocusSession,
                        )
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                FocusScreen(
                    state = state,
                    onStart = vm::start,
                    onCancel = vm::cancel,
                    onSetGoal = vm::setGoal,
                )
            }

            composable(Destination.Goals.route) {
                val vm: GoalsViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        GoalsViewModel(container.usageRepository)
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                GoalsScreen(
                    state = state,
                    onSetTarget = vm::setTarget,
                    onOpenDay = { epochDay ->
                        navController.navigate("$DAY_CARD_ROUTE/$epochDay")
                    },
                )
            }

            composable(TRIAGE_ROUTE) {
                val vm: TriageViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        TriageViewModel(container.usageRepository, container.appInfoProvider)
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                TriageScreen(
                    state = state,
                    appInfo = container.appInfoProvider,
                    onAssign = vm::assign,
                    onUndo = vm::undo,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = "$DAY_CARD_ROUTE/{$DAY_CARD_ARG}",
                arguments = listOf(navArgument(DAY_CARD_ARG) { type = NavType.LongType }),
            ) { entry ->
                val epochDay = entry.arguments?.getLong(DAY_CARD_ARG) ?: 0L
                val vm: DayCardViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        DayCardViewModel(
                            epochDay = epochDay,
                            repository = container.usageRepository,
                            appInfo = container.appInfoProvider,
                        )
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                DayCardScreen(
                    state = state,
                    appInfo = container.appInfoProvider,
                    onBack = { navController.popBackStack() },
                    onSetNote = vm::setNote,
                    onSetReflection = vm::setReflection,
                )
            }

            composable(Destination.Insights.route) {
                val vm: InsightsViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        InsightsViewModel(container.usageRepository, container.appInfoProvider)
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                InsightsScreen(state = state, onSelectRange = vm::setRange)
            }

            composable(SETTINGS_ROUTE) {
                val vm: SettingsViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        SettingsViewModel(
                            repository = container.usageRepository,
                            preferences = container.preferences,
                            csvExporter = container.csvExporter,
                            onDigestTimeChanged = container::rescheduleDigest,
                        )
                    },
                )
                val capMinutes by vm.capMinutes.collectAsStateWithLifecycle()
                val busy by vm.busy.collectAsStateWithLifecycle()
                val digestMinutes by vm.digestMinutes.collectAsStateWithLifecycle()
                val exportUri by vm.exportUri.collectAsStateWithLifecycle()

                var showCapDialog by remember { mutableStateOf(false) }

                SettingsScreen(
                    dynamicColor = dynamicColor,
                    capMinutes = capMinutes,
                    busy = busy,
                    digestMinutes = digestMinutes,
                    exportUri = exportUri,
                    onDynamicColorChange = onDynamicColorChange,
                    onSetCap = { showCapDialog = true },
                    onSetDigestTime = vm::setDigestMinutes,
                    onExportCsv = vm::exportCsv,
                    onExportConsumed = vm::clearExport,
                    onOpenCategories = { navController.navigate(CATEGORY_EDITOR_ROUTE) },
                    onOpenIntentPrompt = { navController.navigate(INTENT_PROMPT_ROUTE) },
                    onRefreshNow = vm::refreshNow,
                    onRecomputeHistory = vm::recomputeHistory,
                    onOpenDiagnostics = { navController.navigate(DIAGNOSTICS_ROUTE) },
                )

                if (showCapDialog) {
                    SetCapDialog(
                        currentMinutes = capMinutes,
                        onDismiss = { showCapDialog = false },
                        onConfirm = {
                            vm.setCap(it)
                            showCapDialog = false
                        },
                    )
                }
            }

            composable(CATEGORY_EDITOR_ROUTE) {
                val vm: CategoryEditorViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        CategoryEditorViewModel(container.usageRepository)
                    },
                )
                val state by vm.state.collectAsStateWithLifecycle()

                CategoryEditorScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSave = vm::save,
                    onAdd = vm::add,
                    onDelete = vm::delete,
                )
            }

            composable(INTENT_PROMPT_ROUTE) {
                val context = LocalContext.current
                var overlayGranted by remember {
                    mutableStateOf(Settings.canDrawOverlays(context))
                }
                val overlayLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    overlayGranted = Settings.canDrawOverlays(context)
                }

                val vm: IntentPromptViewModel = viewModel(
                    factory = IntentViewModelFactory {
                        IntentPromptViewModel(
                            preferences = container.preferences,
                            repository = container.usageRepository,
                            installedApps = InstalledAppsProvider(
                                context = context.applicationContext,
                                labelOf = container.appInfoProvider::label,
                            ),
                            onEnabledChanged = { enabled ->
                                if (enabled) container.startIntentWatch() else container.stopIntentWatch()
                            },
                        )
                    },
                )
                val enabled by vm.enabled.collectAsStateWithLifecycle()
                val watched by vm.watched.collectAsStateWithLifecycle()
                val apps by vm.apps.collectAsStateWithLifecycle()
                val promptCount by vm.promptCount.collectAsStateWithLifecycle()

                IntentPromptScreen(
                    enabled = enabled,
                    watched = watched,
                    apps = apps,
                    promptCount = promptCount,
                    overlayGranted = overlayGranted,
                    onBack = { navController.popBackStack() },
                    onToggle = vm::setEnabled,
                    onToggleApp = vm::setWatched,
                    onGrantOverlay = {
                        overlayLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                )
            }

            composable(DIAGNOSTICS_ROUTE) {
                TrackingStatusScreen(
                    database = container.database,
                    onHarvestNow = { container.usageIngestor.ingest() },
                    onRecomputeHistory = { container.usageIngestor.recomputeFrom(0L) },
                    onOpenSettings = {
                        navController.popBackStack(SETTINGS_ROUTE, false)
                    },
                )
            }
        }
    }
}

package com.anirust.app.ui.navigation

import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anirust.app.di.AppContainer
import com.anirust.app.ui.account.*
import com.anirust.app.ui.details.DetailsScreen
import com.anirust.app.ui.details.DetailsViewModel
import com.anirust.app.ui.favorites.FavoritesViewModel
import com.anirust.app.ui.favorites.PrimaryListsScreen
import com.anirust.app.ui.history.HistoryScreen
import com.anirust.app.ui.history.HistoryViewModel
import com.anirust.app.ui.home.HomeScreen
import com.anirust.app.ui.home.HomeViewModel
import com.anirust.app.ui.player.PlayerScreen
import com.anirust.app.ui.player.PlayerViewModel
import com.anirust.app.ui.search.SearchScreen
import com.anirust.app.ui.search.SearchViewModel
import com.anirust.app.ui.settings.SettingsScreen
import com.anirust.app.ui.settings.SettingsViewModel

@Composable
fun AnirustAppRoot(container: AppContainer) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val accountViewModel: ShikimoriViewModel? =
        container.shikimoriAccountRepository?.let {
            viewModel(key = "shikimori-account", factory = ShikimoriViewModel.Factory(it))
        }
    LaunchedEffect(container.messages, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.messages.events.collect {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(accountViewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            accountViewModel?.refreshIfStale()
            kotlinx.coroutines.awaitCancellation()
        }
    }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    AdaptiveAppScaffold(
        currentRoute,
        onNavigate = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = contentModifier,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel =
                    viewModel(
                        factory =
                            HomeViewModel.Factory(
                                container.watchHistoryUseCase,
                                container.favoritesUseCase,
                                container.resolveStreamUseCase,
                                container.settingsRepository,
                            )
                    )
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToDetails = { animeId ->
                        navController.navigate(Screen.Details.createRoute(animeId))
                    },
                    onNavigateToPlayer = { animeId, episodeNum, dubbing ->
                        navController.navigate(
                            Screen.Player.createRoute(animeId, episodeNum, dubbing)
                        )
                    },
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                )
            }

            composable(Screen.Search.route) {
                val searchViewModel: SearchViewModel =
                    viewModel(factory = SearchViewModel.Factory(container.searchAnimeUseCase))
                SearchScreen(
                    viewModel = searchViewModel,
                    onNavigateToDetails = { animeId ->
                        navController.navigate(Screen.Details.createRoute(animeId))
                    },
                )
            }

            composable(Screen.Favorites.route) {
                val favoritesViewModel: FavoritesViewModel =
                    viewModel(factory = FavoritesViewModel.Factory(container.favoritesUseCase))
                PrimaryListsScreen(
                    localViewModel = favoritesViewModel,
                    accountViewModel = accountViewModel,
                    favorites = container.favoritesUseCase,
                    onAccount = { navController.navigate(Screen.ShikimoriAccount.route) },
                    onOpenAnime = { animeId ->
                        navController.navigate(Screen.Details.createRoute(animeId))
                    },
                )
            }

            composable(Screen.History.route) {
                val historyViewModel: HistoryViewModel =
                    viewModel(
                        factory =
                            HistoryViewModel.Factory(
                                container.watchHistoryUseCase,
                                container.resolveStreamUseCase,
                                container.settingsRepository,
                            )
                    )
                HistoryScreen(
                    viewModel = historyViewModel,
                    onNavigateToPlayer = { animeId, episodeNum, dubbing ->
                        navController.navigate(
                            Screen.Player.createRoute(animeId, episodeNum, dubbing)
                        )
                    },
                    onNavigateToDetails = { animeId ->
                        navController.navigate(Screen.Details.createRoute(animeId))
                    },
                )
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel =
                    viewModel(
                        factory =
                            SettingsViewModel.Factory(
                                container.settingsRepository,
                                container.watchHistoryUseCase,
                                container.favoritesUseCase,
                            )
                    )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenAccount = { navController.navigate(Screen.ShikimoriAccount.route) },
                )
            }

            composable(Screen.ShikimoriAccount.route) {
                accountViewModel?.let {
                    AccountScreen(
                        it,
                        onBack = { navController.popBackStack() },
                        onOpenLists = {
                            navController.navigate(Screen.Favorites.route) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
            composable(Screen.ShikimoriLists.route) {
                accountViewModel?.let {
                    ShikimoriListsScreen(
                        it,
                        onBack = { navController.popBackStack() },
                        onAccount = { navController.navigate(Screen.ShikimoriAccount.route) },
                        onOpenAnime = { id ->
                            navController.navigate(Screen.Details.createRoute(id))
                        },
                    )
                }
            }

            composable(
                route = Screen.Details.route,
                arguments = listOf(navArgument("animeId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val animeId = backStackEntry.arguments?.getLong("animeId") ?: 0L
                val detailsViewModel: DetailsViewModel =
                    viewModel(
                        key = "details_$animeId",
                        factory =
                            DetailsViewModel.Factory(
                                animeId,
                                container.getAnimeDetailsUseCase,
                                container.getEpisodesUseCase,
                                container.resolveStreamUseCase,
                                container.favoritesUseCase,
                                container.watchHistoryUseCase,
                                container.settingsRepository,
                            ),
                    )
                DetailsScreen(
                    viewModel = detailsViewModel,
                    accountViewModel = accountViewModel,
                    onOpenAccount = { navController.navigate(Screen.ShikimoriAccount.route) },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPlayer = { id, epNum, dub ->
                        navController.navigate(Screen.Player.createRoute(id, epNum, dub))
                    },
                )
            }

            composable(
                route = Screen.Player.route,
                arguments =
                    listOf(
                        navArgument("animeId") { type = NavType.LongType },
                        navArgument("episodeNumber") { type = NavType.IntType },
                        navArgument("dubbing") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = ""
                        },
                    ),
            ) { backStackEntry ->
                val animeId = backStackEntry.arguments?.getLong("animeId") ?: 0L
                val episodeNum = backStackEntry.arguments?.getInt("episodeNumber") ?: 1
                val rawDubbing = backStackEntry.arguments?.getString("dubbing")
                // Navigation has already URI-decoded this argument once.
                val dubbing = rawDubbing?.takeIf { it.isNotBlank() }

                val playerViewModel: PlayerViewModel =
                    viewModel(
                        key = "player_${animeId}_${episodeNum}_$dubbing",
                        factory =
                            PlayerViewModel.Factory(
                                animeId,
                                episodeNum,
                                dubbing,
                                container.getAnimeDetailsUseCase,
                                container.resolveStreamUseCase,
                                container.watchHistoryUseCase,
                                container.settingsRepository,
                            ),
                    )
                PlayerScreen(
                    viewModel = playerViewModel,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun AdaptiveAppScaffold(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val showNavigation = BottomNavItems.any { it.route == currentRoute }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val useRail = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (showNavigation && useRail)
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().testTag("navigation_rail"),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        BottomNavItems.forEach { screen ->
                            NavigationRailItem(
                                selected = currentRoute == screen.route,
                                onClick = { onNavigate(screen.route) },
                                icon = { screen.icon?.let { Icon(it, null) } },
                                label = { Text(screen.title, maxLines = 1) },
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            Scaffold(
                modifier = Modifier.weight(1f),
                bottomBar = {
                    if (showNavigation && !useRail)
                        NavigationBar(
                            modifier = Modifier.testTag("navigation_bar"),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            BottomNavItems.forEach { screen ->
                                NavigationBarItem(
                                    selected = currentRoute == screen.route,
                                    onClick = { onNavigate(screen.route) },
                                    icon = { screen.icon?.let { Icon(it, null) } },
                                    label = {
                                        Text(
                                            screen.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                },
            ) { padding ->
                Box(
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .imePadding(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val maxContentWidth =
                        when (currentRoute) {
                            Screen.Home.route,
                            Screen.Settings.route,
                            Screen.History.route -> 840.dp
                            Screen.Player.route -> androidx.compose.ui.unit.Dp.Infinity
                            else -> 1200.dp
                        }
                    content(Modifier.widthIn(max = maxContentWidth).fillMaxSize())
                }
            }
        }
    }
}

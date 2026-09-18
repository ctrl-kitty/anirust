package com.anirust.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.domain.model.Anime
import com.anirust.app.ui.components.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToPlayer: (Long, Int, String?) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferExternal by viewModel.preferExternalPlayer.collectAsStateWithLifecycle()
    val resolving by viewModel.isResolvingStream.collectAsStateWithLifecycle()
    val error by viewModel.streamResolveError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            val result =
                snackbar.showSnackbar(it, actionLabel = "Повторить", withDismissAction = true)
            viewModel.clearError()
            if (result == SnackbarResult.ActionPerformed) viewModel.retryExternalPlayback(context)
        }
    }
    Scaffold(modifier.fillMaxSize(), snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        if (state.isLoading) LoadingView(modifier = Modifier.padding(padding).fillMaxSize())
        else
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    ScreenHeading("AniRust", "Твоя следующая любимая история") {
                        FilledTonalIconButton(
                            onClick = onNavigateToSearch,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Outlined.Search, "Поиск")
                        }
                    }
                }
                item {
                    val last = state.lastWatched
                    if (last != null)
                        LastWatchCard(
                            last,
                            onResume = {
                                val next = state.nextEpisodeNumber
                                if (last.isCompleted && next == null)
                                    onNavigateToDetails(last.animeId)
                                else if (preferExternal)
                                    viewModel.openInExternalPlayer(
                                        context,
                                        if (last.isCompleted && next != null)
                                            last.copy(
                                                episodeNumber = next,
                                                episodeId = "${last.animeId}_$next",
                                                playbackPositionMs = 0,
                                                durationMs = 0,
                                                completionOverride = null,
                                            )
                                        else last,
                                    )
                                else
                                    onNavigateToPlayer(
                                        last.animeId,
                                        if (last.isCompleted) next ?: last.episodeNumber
                                        else last.episodeNumber,
                                        last.dubbing,
                                    )
                            },
                            onOpenExternal = { viewModel.openInExternalPlayer(context, last) },
                            onClickAnime = { onNavigateToDetails(last.animeId) },
                            nextEpisodeNumber = state.nextEpisodeNumber,
                        )
                    else
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(28.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        null,
                                        Modifier.padding(16.dp).size(32.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                                Text(
                                    "Хороший день\nдля нового аниме",
                                    style = MaterialTheme.typography.displaySmall,
                                )
                                Text(
                                    "Выбирай историю по настроению. Любимая озвучка, списки и прогресс — всё под рукой.",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Button(
                                    onClick = onNavigateToSearch,
                                    modifier = Modifier.heightIn(min = 52.dp),
                                ) {
                                    Icon(Icons.Outlined.Search, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Найти аниме")
                                }
                            }
                        }
                }
                if (state.recentHistory.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Недавно смотрели", style = MaterialTheme.typography.titleLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.recentHistory, key = { it.historyId }) { item ->
                                    Card(
                                        onClick = {
                                            if (preferExternal)
                                                viewModel.openInExternalPlayer(context, item)
                                            else
                                                onNavigateToPlayer(
                                                    item.animeId,
                                                    item.episodeNumber,
                                                    item.dubbing,
                                                )
                                        },
                                        modifier = Modifier.width(228.dp),
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AnimePoster(
                                                item.animePoster,
                                                Modifier.width(56.dp).height(80.dp),
                                            )
                                            Column(
                                                Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    item.animeTitle,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    "Серия " + item.episodeNumber,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (item.durationMs > 0)
                                            LinearProgressIndicator(
                                                progress = { item.progressFraction },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.favorites.isNotEmpty())
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("В твоих списках", style = MaterialTheme.typography.titleLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.favorites, key = { it.animeId }) { item ->
                                    AnimeCard(
                                        Anime(
                                            id = item.animeId,
                                            title = item.title,
                                            posterUrl = item.posterUrl,
                                            score = item.score,
                                            episodesCount = item.episodesCount,
                                        ),
                                        onClick = { onNavigateToDetails(item.animeId) },
                                        modifier = Modifier.width(156.dp),
                                    )
                                }
                            }
                        }
                    }
                if (state.lastWatched == null)
                    item {
                        ListItem(
                            headlineContent = { Text("Смотри в своём ритме") },
                            supportingContent = {
                                Text(
                                    "Добавляй аниме в списки, а к начатым сериям возвращайся с того же момента."
                                )
                            },
                            leadingContent = { Icon(Icons.Outlined.Bookmarks, null) },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                        )
                    }
            }
    }
    if (resolving) ResolvingDialog(viewModel::cancelExternalPlayback)
}

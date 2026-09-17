package com.anirust.app.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.FavoriteStatus
import com.anirust.app.ui.components.*

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToDetails: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenShikimori: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(modifier.fillMaxSize()) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            ScreenHeading("Моя коллекция", "Истории, которые хочется сохранить")
            if (onOpenShikimori != null) {
                FilledTonalButton(onClick = onOpenShikimori, modifier = Modifier.fillMaxWidth()) {
                    Text("Списки Shikimori")
                }
                Text(
                    "На устройстве",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterTabChip(
                        "Все",
                        state.selectedStatus == null,
                        { viewModel.selectStatus(null) },
                    )
                }
                items(FavoriteStatus.entries) { status ->
                    FilterTabChip(
                        status.title,
                        state.selectedStatus == status,
                        { viewModel.selectStatus(status) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                state.isLoading -> LoadingView()
                state.items.isEmpty() ->
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        EmptyView(
                            if (state.selectedStatus == null) "В избранном пока пусто"
                            else "В этом списке пока пусто",
                            "Открой аниме и нажми на закладку — оно появится здесь.",
                            icon = Icons.Outlined.Bookmarks,
                        )
                    }
                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(148.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.items, key = { it.animeId }) { item ->
                            AnimeCard(
                                Anime(
                                    id = item.animeId,
                                    title = item.title,
                                    originalTitle = item.originalTitle,
                                    posterUrl = item.posterUrl,
                                    score = item.score,
                                    episodesCount = item.episodesCount,
                                ),
                                onClick = { onNavigateToDetails(item.animeId) },
                            )
                        }
                    }
            }
        }
    }
}

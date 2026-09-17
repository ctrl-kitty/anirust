package com.anirust.app.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.ui.components.*

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateToDetails: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(modifier.fillMaxSize()) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            ScreenHeading("Открывай новое", "Поиск по каталогам Yummy и Shikimori")
            AnirustSearchBar(state.query, viewModel::onQueryChange, viewModel::searchImmediately)
            Spacer(Modifier.height(16.dp))
            if (state.results.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    if (state.isLoading) "Обновляем результаты…"
                    else "Найдено: " + state.results.size,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (state.error != null) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { viewModel.searchImmediately(state.query) }) {
                        Text("Повторить поиск")
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(148.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.results, key = { it.id }) { anime ->
                        AnimeCard(anime, onClick = { onNavigateToDetails(anime.id) })
                    }
                }
            } else
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when {
                        state.isLoading -> LoadingView("Ищем твою следующую историю…")
                        state.error != null ->
                            ErrorView(state.error!!, { viewModel.searchImmediately(state.query) })
                        state.hasSearched ->
                            EmptyView(
                                "Ничего не найдено",
                                "Попробуй другое название или его оригинальное написание.",
                                icon = Icons.Outlined.Search,
                            )
                        else ->
                            EmptyView(
                                "Введите название аниме",
                                "От знакомой классики до новой любимой истории.",
                                icon = Icons.Outlined.Search,
                            ) {
                                Text("С чего начнём?", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Naruto", "Bleach").forEach { query ->
                                        SuggestionChip(
                                            onClick = {
                                                viewModel.onQueryChange(query)
                                                viewModel.searchImmediately(query)
                                            },
                                            label = { Text(query) },
                                        )
                                    }
                                }
                            }
                    }
                }
        }
    }
}

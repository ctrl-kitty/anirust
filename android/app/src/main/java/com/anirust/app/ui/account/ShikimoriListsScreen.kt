package com.anirust.app.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.data.remote.shikimori.*
import com.anirust.app.ui.components.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShikimoriListsScreen(
    viewModel: ShikimoriViewModel,
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    showBack: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable(state.user?.id) { mutableStateOf<String?>("watching") }
    var edit by remember(state.user?.id) { mutableStateOf<ShikimoriRate?>(null) }
    val filtered = state.rates.filter { filter == null || it.status == filter }
    val online = rememberOnline()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Списки Shikimori") },
                navigationIcon = {
                    if (showBack)
                        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::sync,
                        enabled = state.user != null && !state.busy && !state.needsLogin,
                    ) {
                        Icon(Icons.Outlined.Sync, "Обновить списки")
                    }
                    IconButton(onAccount) {
                        Icon(Icons.Outlined.AccountCircle, "Аккаунт Shikimori")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.initializing) item { LoadingView() }
            else if (state.user == null)
                item {
                    EmptyView(
                        "Твои списки Shikimori",
                        "Подключи аккаунт, чтобы смотреть аниме из списков и менять их прямо здесь.",
                        icon = Icons.Outlined.CloudSync,
                    ) {
                        Button(onAccount) { Text("Подключить аккаунт") }
                    }
                }
            else {
                item {
                    Text(state.user!!.nickname, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (state.lastSync == 0L) "Списки ещё не загружены"
                        else
                            "Синхронизация: " +
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(state.lastSync)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterTabChip(
                                "Все · " + state.rates.size,
                                filter == null,
                                { filter = null },
                            )
                        }
                        items(ShikimoriListStatus.entries) { status ->
                            FilterTabChip(
                                status.title +
                                    " · " +
                                    state.rates.count { it.status == status.apiValue },
                                filter == status.apiValue,
                                { filter = status.apiValue },
                            )
                        }
                    }
                }
                if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (!online && state.rates.isNotEmpty())
                    item {
                        Text(
                            "Офлайн · сохранённая копия списков",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                if (state.busy && state.rates.isEmpty())
                    item { LoadingView("Загружаем твои списки…") }
                if (state.error != null || state.needsLogin)
                    item {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(state.error ?: "Войди заново, чтобы продолжить синхронизацию")
                                if (state.rates.isNotEmpty())
                                    Text(
                                        "Показана последняя сохранённая копия.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                TextButton(
                                    onClick = {
                                        if (state.needsLogin) onAccount() else viewModel.sync()
                                    },
                                    enabled = !state.busy,
                                ) {
                                    Text(if (state.needsLogin) "Подключить заново" else "Повторить")
                                }
                            }
                        }
                    }
                if (filtered.isEmpty() && !state.busy && state.lastSync != 0L)
                    item {
                        EmptyView(
                            "В этом списке пока пусто",
                            "Найди аниме и добавь его в Shikimori на экране с сериями.",
                        )
                    }
                items(filtered, key = { it.id }) { rate ->
                    val anime = rate.anime.toAnime()
                    Card(
                        onClick = { onOpenAnime(anime.id) },
                        modifier = Modifier.animateItem(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnimePoster(anime.posterUrl, Modifier.width(72.dp).height(100.dp))
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        anime.displayTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        ShikimoriListStatus.titleOf(rate.status),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "Серий: " +
                                            rate.episodes +
                                            " / " +
                                            (anime.episodesCount ?: "?") +
                                            if (rate.score > 0) " · Оценка: " + rate.score else "",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { onOpenAnime(anime.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Выбрать серию")
                                }
                                IconButton(
                                    onClick = { edit = rate },
                                    enabled = !state.busy && !state.needsLogin,
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        "Изменить список: " + anime.displayTitle,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { rate ->
        RateEditorDialog(
            rate.anime.toAnime(),
            rate,
            state.busy,
            onDismiss = { edit = null },
            onSave = { status, score, episodes ->
                viewModel.save(rate.anime.toAnime(), status, score, episodes) { edit = null }
            },
            onDelete = { viewModel.delete(rate) { edit = null } },
        )
    }
}

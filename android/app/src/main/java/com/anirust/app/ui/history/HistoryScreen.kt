package com.anirust.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.domain.model.WatchHistoryItem
import com.anirust.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToPlayer: (Long, Int, String?) -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferExternal by viewModel.preferExternalPlayer.collectAsStateWithLifecycle()
    val resolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WatchHistoryItem?>(null) }
    LaunchedEffect(error) {
        error?.let {
            val result =
                snackbar.showSnackbar(it, actionLabel = "Повторить", withDismissAction = true)
            viewModel.clearError()
            if (result == SnackbarResult.ActionPerformed) viewModel.retryExternalPlayback(context)
        }
    }
    Scaffold(modifier.fillMaxSize(), snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeading("История", "Возвращайся к любимым моментам") {
                    if (state.items.isNotEmpty())
                        IconButton(onClick = { showClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, "Очистить всё")
                        }
                }
            }
            if (state.isLoading) item { LoadingView() }
            else if (state.items.isEmpty())
                item {
                    EmptyView(
                        "История просмотров пуста",
                        "Начни просмотр — мы сохраним серию, озвучку и твоё место.",
                        icon = Icons.Outlined.History,
                    )
                }
            else
                items(state.items, key = { it.historyId }) { item ->
                    HistoryRow(
                        item,
                        onResume = {
                            if (preferExternal) viewModel.openInExternalPlayer(context, item)
                            else onNavigateToPlayer(item.animeId, item.episodeNumber, item.dubbing)
                        },
                        onOpenExternal = { viewModel.openInExternalPlayer(context, item) },
                        onClickDetails = { onNavigateToDetails(item.animeId) },
                        onDelete = { pendingDelete = item },
                    )
                }
        }
    }
    if (showClear || pendingDelete != null)
        AlertDialog(
            onDismissRequest = {
                showClear = false
                pendingDelete = null
            },
            icon = { Icon(Icons.Outlined.DeleteOutline, null) },
            title = { Text(if (showClear) "Очистить историю?" else "Удалить запись?") },
            text = {
                Text(
                    if (showClear) "Прогресс всех серий будет удалён с этого устройства."
                    else "Серия и её прогресс исчезнут из истории. Остальные записи сохранятся."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (showClear) viewModel.clearAll()
                        else pendingDelete?.let { viewModel.deleteItem(it.historyId) }
                        showClear = false
                        pendingDelete = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClear = false
                        pendingDelete = null
                    }
                ) {
                    Text("Отмена")
                }
            },
        )
    if (resolving) ResolvingDialog(viewModel::cancelExternalPlayback)
}

@Composable
private fun HistoryRow(
    item: WatchHistoryItem,
    onResume: () -> Unit,
    onOpenExternal: () -> Unit,
    onClickDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onResume,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimePoster(item.animePoster, Modifier.width(64.dp).height(88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.animeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Серия " + item.episodeNumber, style = MaterialTheme.typography.bodyMedium)
                    item.dubbing?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, "Действия с записью")
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Об аниме") },
                            onClick = {
                                menu = false
                                onClickDetails()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Внешний плеер") },
                            onClick = {
                                menu = false
                                onOpenExternal()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить из истории") },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            if (item.durationMs > 0)
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTimestamp(item.lastWatchedTimestamp),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onResume) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (item.resumePositionMs == 0L) "Смотреть" else "Продолжить")
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    return when {
        diff < 60_000 -> "Только что"
        diff < 3600_000 -> (diff / 60_000).toString() + " мин назад"
        diff < 86400_000 -> (diff / 3600_000).toString() + " ч назад"
        else ->
            SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("ru")).format(Date(timestamp))
    }
}

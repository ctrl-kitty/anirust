package com.anirust.app.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.data.repository.ShikimoriAccountState
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.Episode
import com.anirust.app.domain.model.FavoriteStatus
import com.anirust.app.ui.account.*
import com.anirust.app.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Long, Int, String?) -> Unit,
    modifier: Modifier = Modifier,
    accountViewModel: ShikimoriViewModel? = null,
    onOpenAccount: (() -> Unit)? = null,
) {
    val accountState by
        (accountViewModel?.state
                ?: remember { MutableStateFlow(ShikimoriAccountState(initializing = false)) })
            .collectAsStateWithLifecycle()
    var editShikimori by remember(accountState.user?.id) { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shikimoriId = state.anime?.shikimoriId ?: state.anime?.id?.takeIf { it < 0 }?.let { -it }
    val remoteRate =
        remember(accountState.rates, shikimoriId) {
            accountState.rates.firstOrNull { it.anime.id == shikimoriId }
        }
    val remoteWatchedCount = remoteRate?.episodes?.coerceAtLeast(0) ?: 0
    var showWatched by rememberSaveable(state.anime?.id) { mutableStateOf(false) }
    val locallyUnwatched =
        state.history
            .filter { it.completionOverride == false && !it.isCompleted }
            .map { it.episodeNumber }
            .toSet()
    fun isWatched(number: Int) =
        number in state.watchedEpisodeNumbers ||
            (number <= remoteWatchedCount && number !in locallyUnwatched)
    val unwatchedEpisodes = state.filteredEpisodes.filterNot { isWatched(it.number) }
    val resume =
        state.history.firstOrNull { item ->
            !item.isCompleted &&
                item.playbackPositionMs > 0 &&
                (state.selectedDubbing == null || item.dubbing == state.selectedDubbing) &&
                unwatchedEpisodes.any { it.number == item.episodeNumber }
        }
    val primaryEpisode =
        unwatchedEpisodes.firstOrNull { it.number == resume?.episodeNumber }
            ?: state.history
                .firstOrNull { it.isCompleted }
                ?.let { last ->
                    unwatchedEpisodes
                        .filter { it.number > last.episodeNumber }
                        .minByOrNull { it.number }
                }
            ?: unwatchedEpisodes.minByOrNull { it.number }
    val primaryVoice = state.selectedDubbing ?: resume?.dubbing
    val visibleEpisodes = if (showWatched) state.filteredEpisodes else unwatchedEpisodes
    val watchedCount = state.filteredEpisodes.size - unwatchedEpisodes.size
    val preferExternal by viewModel.preferExternalPlayer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var favoriteDialog by rememberSaveable { mutableStateOf(false) }
    var sheetExternal by rememberSaveable { mutableStateOf(false) }
    val openPicker: (Episode, Boolean) -> Unit = { episode, external ->
        sheetExternal = external
        viewModel.openDubbingPicker(episode)
    }
    LaunchedEffect(state.externalError) {
        state.externalError?.let {
            val result =
                snackbar.showSnackbar(it, actionLabel = "Повторить", withDismissAction = true)
            viewModel.clearExternalError()
            if (result == SnackbarResult.ActionPerformed) viewModel.retryExternalPlayback(context)
        }
    }
    val play: (Episode) -> Unit = { episode ->
        if (state.selectedDubbing == null) openPicker(episode, preferExternal)
        else if (preferExternal) viewModel.openEpisodeInExternal(context, episode)
        else state.anime?.let { onNavigateToPlayer(it.id, episode.number, state.selectedDubbing) }
    }
    Scaffold(
        modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbar) },
        bottomBar = {
            if (!state.isLoading && primaryEpisode != null)
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = {
                                if (primaryVoice == null) play(primaryEpisode)
                                else if (preferExternal)
                                    viewModel.openEpisodeInExternal(
                                        context,
                                        primaryEpisode,
                                        primaryVoice,
                                    )
                                else
                                    state.anime?.let {
                                        onNavigateToPlayer(
                                            it.id,
                                            primaryEpisode.number,
                                            primaryVoice,
                                        )
                                    }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (resume != null)
                                    "Продолжить · серия ${primaryEpisode.number} · ${resume.playbackPositionMs / 60000}:${((resume.playbackPositionMs / 1000) % 60).toString().padStart(2, '0')}"
                                else "Смотреть серию ${primaryEpisode.number}"
                            )
                        }
                        Text(
                            primaryVoice ?: "Выбери озвучку перед просмотром",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.anime?.displayTitle ?: "Об аниме",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (accountState.user != null) {
                                if (accountState.needsLogin) onOpenAccount?.invoke()
                                else editShikimori = true
                            } else favoriteDialog = true
                        },
                        enabled = state.anime != null && !state.isLoading && !accountState.busy,
                    ) {
                        Icon(
                            if (
                                if (accountState.user != null) remoteRate != null
                                else state.isFavorite
                            )
                                Icons.Outlined.BookmarkAdded
                            else Icons.Outlined.BookmarkAdd,
                            if (accountState.user != null) "Изменить список Shikimori"
                            else
                                state.favoriteStatus?.let { "В списке: " + it.title + ". Изменить" }
                                    ?: "Добавить в список",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading ->
                LoadingView("Загружаем историю и серии…", Modifier.padding(padding).fillMaxSize())
            state.error != null && state.anime == null ->
                Box(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                    ErrorView(state.error!!, { viewModel.loadData() })
                }
            else ->
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                    val wide = maxWidth >= 840.dp
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 0.dp),
                    ) {
                        if (wide)
                            Column(
                                Modifier.width(320.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(24.dp)
                            ) {
                                state.anime?.let { AnimeOverview(it) }
                            }
                        LazyColumn(
                            Modifier.weight(1f).fillMaxHeight().testTag("episode_list"),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            if (!wide) item { state.anime?.let { AnimeOverview(it) } }
                            if (onOpenAccount != null)
                                item {
                                    val shikiId =
                                        state.anime?.shikimoriId
                                            ?: state.anime?.id?.takeIf { it < 0 }?.let { -it }
                                    val rate =
                                        accountState.rates.firstOrNull { it.anime.id == shikiId }
                                    FilledTonalButton(
                                        onClick = {
                                            if (
                                                accountState.user == null || accountState.needsLogin
                                            )
                                                onOpenAccount()
                                            else editShikimori = true
                                        },
                                        enabled =
                                            !accountState.busy &&
                                                !accountState.initializing &&
                                                (shikiId != null || accountState.user == null),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Outlined.CloudSync, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            rate?.let {
                                                "Shikimori · " +
                                                    com.anirust.app.data.remote.shikimori
                                                        .ShikimoriListStatus
                                                        .titleOf(it.status)
                                            }
                                                ?: if (accountState.user == null)
                                                    "Подключить Shikimori"
                                                else "Добавить в Shikimori"
                                        )
                                    }
                                    if (shikiId == null && accountState.user != null)
                                        Text(
                                            "У этого аниме нет подтверждённого ID Shikimori",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                }
                            if (state.series.size > 1)
                                item {
                                    SeriesPicker(
                                        state.series,
                                        state.selectedSeriesId,
                                        viewModel::selectSeries,
                                    )
                                }
                            if (state.preferredDubbingNotFound)
                                item {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Row(
                                            Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Icon(Icons.Outlined.Info, null)
                                            Text(
                                                "Озвучки «" +
                                                    state.preferredDubbingName +
                                                    "» здесь нет. Выбери другую.",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                            if (state.availableDubbings.isNotEmpty())
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Озвучка",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            item {
                                                FilterTabChip(
                                                    "Все",
                                                    state.selectedDubbing == null,
                                                    { viewModel.selectDubbing(null) },
                                                )
                                            }
                                            items(state.availableDubbings, key = { it }) { dubbing
                                                ->
                                                FilterTabChip(
                                                    dubbing,
                                                    state.selectedDubbing == dubbing,
                                                    { viewModel.selectDubbing(dubbing) },
                                                )
                                            }
                                        }
                                    }
                                }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Серии",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        visibleEpisodes.size.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (watchedCount > 0)
                                item {
                                    OutlinedButton(onClick = { showWatched = !showWatched }) {
                                        Icon(
                                            if (showWatched) Icons.Outlined.ExpandLess
                                            else Icons.Outlined.ExpandMore,
                                            null,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (showWatched) "Скрыть просмотренные · $watchedCount"
                                            else "Показать просмотренные · $watchedCount"
                                        )
                                    }
                                }
                            if (state.completionThreshold != 95)
                                item {
                                    TextButton(onClick = viewModel::resetCompletionThreshold) {
                                        Text(
                                            "Просмотрено с ${state.completionThreshold}% · сбросить правило"
                                        )
                                    }
                                }
                            if (state.filteredEpisodes.isEmpty())
                                item {
                                    if (state.error != null)
                                        ErrorView(state.error!!, { viewModel.loadData() })
                                    else
                                        EmptyView(
                                            "Нет доступных серий",
                                            if (state.selectedDubbing != null)
                                                "Попробуй другую озвучку."
                                            else "Поддерживаемые видео ещё не добавлены в каталог.",
                                        )
                                }
                            else if (visibleEpisodes.isEmpty())
                                item {
                                    EmptyView(
                                        "Нет непросмотренных серий",
                                        if (state.selectedDubbing != null)
                                            "Все доступные серии в выбранной озвучке уже просмотрены."
                                        else "Все доступные серии уже просмотрены.",
                                        icon = Icons.Outlined.TaskAlt,
                                    )
                                }
                            else
                                items(visibleEpisodes, key = { it.id }) { episode ->
                                    EpisodeItem(
                                        episode,
                                        isWatched(episode.number),
                                        state.selectedDubbing,
                                        onPlay = { play(episode) },
                                        onPlayExternal = {
                                            if (state.selectedDubbing != null)
                                                viewModel.openEpisodeInExternal(context, episode)
                                            else openPicker(episode, true)
                                        },
                                        onSelectDubbing = { openPicker(episode, preferExternal) },
                                        modifier = Modifier.animateItem(),
                                        progress =
                                            state.history
                                                .firstOrNull {
                                                    it.episodeNumber == episode.number &&
                                                        (state.selectedDubbing == null ||
                                                            it.dubbing == state.selectedDubbing)
                                                }
                                                ?.progressFraction ?: 0f,
                                        onMarkWatched = {
                                            viewModel.markEpisode(
                                                episode,
                                                !isWatched(episode.number),
                                            )
                                        },
                                    )
                                }
                        }
                    }
                }
        }
    }
    if (favoriteDialog)
        AlertDialog(
            onDismissRequest = { favoriteDialog = false },
            icon = { Icon(Icons.Outlined.Bookmarks, null) },
            title = { Text("На этом устройстве") },
            text = {
                Column(Modifier.selectableGroup()) {
                    FavoriteStatus.entries.forEach { status ->
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(
                                    state.favoriteStatus == status,
                                    role = Role.RadioButton,
                                    onClick = {
                                        viewModel.setFavoriteStatus(status)
                                        favoriteDialog = false
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(state.favoriteStatus == status, onClick = null)
                            Text(status.title)
                        }
                    }
                    if (state.isFavorite)
                        TextButton(
                            onClick = {
                                viewModel.setFavoriteStatus(null)
                                favoriteDialog = false
                            }
                        ) {
                            Text("Удалить из списка", color = MaterialTheme.colorScheme.error)
                        }
                }
            },
            confirmButton = { TextButton(onClick = { favoriteDialog = false }) { Text("Закрыть") } },
        )
    state.selectedEpisodeForDubbing?.let { episode ->
        ModalBottomSheet(onDismissRequest = viewModel::closeDubbingPicker) {
            Text(
                "Как будем смотреть?",
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Серия " + episode.number + " · выбери озвучку",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(episode.voiceVariants) { variant ->
                    Card(
                        onClick = {
                            viewModel.selectDubbing(variant.label)
                            viewModel.closeDubbingPicker()
                            if (sheetExternal)
                                viewModel.openEpisodeInExternal(context, episode, variant.label)
                            else
                                state.anime?.let {
                                    onNavigateToPlayer(it.id, episode.number, variant.label)
                                }
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Outlined.PlayCircle,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(variant.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (sheetExternal) "Внешний плеер" else "Встроенный плеер",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.closeDubbingPicker()
                                    if (sheetExternal)
                                        state.anime?.let {
                                            onNavigateToPlayer(it.id, episode.number, variant.label)
                                        }
                                    else
                                        viewModel.openEpisodeInExternal(
                                            context,
                                            episode,
                                            variant.label,
                                        )
                                }
                            ) {
                                Icon(
                                    if (sheetExternal) Icons.Outlined.SmartDisplay
                                    else Icons.AutoMirrored.Outlined.OpenInNew,
                                    if (sheetExternal) "Во встроенном плеере: " + variant.label
                                    else "Во внешнем плеере: " + variant.label,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.isResolvingExternal) ResolvingDialog(viewModel::cancelExternalPlayback)
    if (editShikimori)
        state.anime?.let { anime ->
            val shikiId = anime.shikimoriId ?: anime.id.takeIf { it < 0 }?.let { -it }
            val existing = accountState.rates.firstOrNull { it.anime.id == shikiId }
            RateEditorDialog(
                anime,
                existing,
                accountState.busy,
                onDismiss = { editShikimori = false },
                onSave = { status, score, episodes ->
                    accountViewModel?.save(anime, status, score, episodes) { editShikimori = false }
                },
                onDelete =
                    existing?.let { rate ->
                        {
                            accountViewModel?.delete(rate) { editShikimori = false }
                            Unit
                        }
                    },
            )
        }
}

@Composable
private fun AnimeOverview(anime: Anime) {
    var expanded by rememberSaveable(anime.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimePoster(anime.posterUrl, Modifier.width(104.dp).height(152.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(anime.displayTitle, style = MaterialTheme.typography.headlineSmall)
                anime.originalTitle
                    ?.takeIf { it != anime.displayTitle }
                    ?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                if (anime.score != null && anime.score > 0)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Outlined.Star, null, Modifier.size(18.dp))
                            Text(
                                anime.score.toString() + " / 10",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
            }
        }
        if (anime.genres.isNotEmpty())
            Text(
                anime.genres.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        anime.synopsis
            ?.takeIf { it.isNotBlank() }
            ?.let { synopsis ->
                Column(Modifier.animateContentSize()) {
                    Text(
                        synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(if (expanded) "Свернуть" else "Об истории")
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                        )
                    }
                }
            }
    }
}

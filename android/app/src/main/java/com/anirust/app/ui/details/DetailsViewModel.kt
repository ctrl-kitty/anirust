package com.anirust.app.ui.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.Episode
import com.anirust.app.domain.model.FavoriteStatus
import com.anirust.app.domain.model.SeriesEntry
import com.anirust.app.domain.model.WatchHistoryItem
import com.anirust.app.domain.usecase.FavoritesUseCase
import com.anirust.app.domain.usecase.GetAnimeDetailsUseCase
import com.anirust.app.domain.usecase.GetEpisodesUseCase
import com.anirust.app.domain.usecase.ResolveStreamUseCase
import com.anirust.app.domain.usecase.WatchHistoryUseCase
import com.anirust.app.ui.player.ExternalPlayerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsUiState(
    val anime: Anime? = null,
    val series: List<SeriesEntry> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val filteredEpisodes: List<Episode> = emptyList(),
    val watchedEpisodeNumbers: Set<Int> = emptySet(),
    val history: List<WatchHistoryItem> = emptyList(),
    val completionThreshold: Int = 95,
    val selectedSeriesId: String? = null,
    val selectedDubbing: String? = null,
    val availableDubbings: List<String> = emptyList(),
    val preferredDubbingNotFound: Boolean = false,
    val preferredDubbingName: String = "",
    val selectedEpisodeForDubbing: Episode? = null,
    val isFavorite: Boolean = false,
    val favoriteStatus: FavoriteStatus? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isResolvingExternal: Boolean = false,
    val externalError: String? = null,
)

class DetailsViewModel(
    private val animeId: Long,
    private val getAnimeDetailsUseCase: GetAnimeDetailsUseCase,
    private val getEpisodesUseCase: GetEpisodesUseCase,
    private val resolveStreamUseCase: ResolveStreamUseCase,
    private val favoritesUseCase: FavoritesUseCase,
    private val watchHistoryUseCase: WatchHistoryUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState(isLoading = true))
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var currentActiveAnimeId: Long = animeId
    private var loadJob: Job? = null
    private var favoriteJob: Job? = null
    private var historyJob: Job? = null
    private var externalJob: Job? = null
    private var lastExternalRequest: Pair<Episode, String?>? = null

    fun retryExternalPlayback(context: Context) {
        lastExternalRequest?.let { openEpisodeInExternal(context, it.first, it.second) }
    }

    val preferExternalPlayer = settingsRepository.useExternalPlayer

    init {
        loadData(animeId)
    }

    private fun observeFavoritesAndHistory(targetId: Long, anime: Anime) {
        favoriteJob?.cancel()
        historyJob?.cancel()
        favoriteJob =
            viewModelScope.launch {
                favoritesUseCase.getFavorite(targetId).collect { favorite ->
                    _uiState.update {
                        it.copy(isFavorite = favorite != null, favoriteStatus = favorite?.status)
                    }
                }
            }

        historyJob =
            viewModelScope.launch {
                watchHistoryUseCase.getHistoryForAnimeIds(anime.localIds).collect { history ->
                    val watchedNums =
                        history.filter { it.isCompleted }.map { it.episodeNumber }.toSet()
                    val choice = restoredDubbing(anime, history, _uiState.value.availableDubbings)
                    _uiState.update {
                        it.copy(
                            watchedEpisodeNumbers = watchedNums,
                            history = history,
                            completionThreshold =
                                settingsRepository.completionThresholds.value[anime.id] ?: 95,
                            selectedDubbing = choice.first,
                            preferredDubbingName = choice.second.orEmpty(),
                            preferredDubbingNotFound = choice.second != null,
                            filteredEpisodes = filterEpisodesByDubbing(it.episodes, choice.first),
                        )
                    }
                }
            }
    }

    fun loadData(targetAnimeId: Long = currentActiveAnimeId) {
        loadJob?.cancel()
        cancelExternalPlayback()
        currentActiveAnimeId = targetAnimeId
        _uiState.update { DetailsUiState(isLoading = true) }
        favoriteJob?.cancel()
        historyJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val animeTask = async { getAnimeDetailsUseCase.getDetails(targetAnimeId) }
                val seriesTask = async { getAnimeDetailsUseCase.getSeries(targetAnimeId) }
                val episodesTask = async { getEpisodesUseCase(targetAnimeId) }
                val animeResult = animeTask.await()
                val seriesResult = seriesTask.await()
                val episodesResult = episodesTask.await()

                if (animeResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error =
                                animeResult.exceptionOrNull()?.message
                                    ?: "Ошибка загрузки информации об аниме",
                        )
                    }
                    return@launch
                }

                val anime = animeResult.getOrThrow()
                settingsRepository.registerAnime(anime)
                val series = seriesResult.getOrDefault(emptyList())
                val episodes = episodesResult.getOrDefault(emptyList())

                // Collect all unique dubbings across episodes
                val dubbings =
                    episodes.flatMap { ep -> ep.voiceVariants.map { it.label } }.distinct().sorted()

                val history = watchHistoryUseCase.getHistoryForAnimeIds(anime.localIds).first()
                val (initialDubbing, missingDubbing) = restoredDubbing(anime, history, dubbings)

                _uiState.update {
                    it.copy(
                        anime = anime,
                        series = series,
                        episodes = episodes,
                        availableDubbings = dubbings,
                        selectedSeriesId = targetAnimeId.toString(),
                        selectedDubbing = initialDubbing,
                        preferredDubbingNotFound = missingDubbing != null,
                        preferredDubbingName = missingDubbing.orEmpty(),
                        watchedEpisodeNumbers =
                            history.filter { it.isCompleted }.map { it.episodeNumber }.toSet(),
                        history = history,
                        completionThreshold =
                            settingsRepository.completionThresholds.value[anime.id] ?: 95,
                        filteredEpisodes = filterEpisodesByDubbing(episodes, initialDubbing),
                        isLoading = false,
                        error = episodesResult.exceptionOrNull()?.message,
                    )
                }
                observeFavoritesAndHistory(targetAnimeId, anime)
            }
    }

    fun selectSeries(seriesId: String) {
        val nextId = seriesId.toLongOrNull() ?: return
        if (nextId == currentActiveAnimeId) return
        loadData(nextId)
    }

    fun resetCompletionThreshold() {
        settingsRepository.setCompletionThreshold(currentActiveAnimeId, null)
    }

    fun markEpisode(episode: Episode, watched: Boolean) {
        val state = _uiState.value
        val anime = state.anime ?: return
        viewModelScope.launch {
            val previous = state.history.firstOrNull { it.episodeNumber == episode.number }
            watchHistoryUseCase.markEpisode(
                previous
                    ?: WatchHistoryItem(
                        animeId = anime.id,
                        animeTitle = anime.displayTitle,
                        animePoster = anime.posterUrl,
                        episodeId = episode.id,
                        episodeNumber = episode.number,
                        dubbing = state.selectedDubbing,
                    ),
                watched,
            )
        }
    }

    fun selectDubbing(dubbing: String?) {
        _uiState.value.anime?.let { settingsRepository.setAnimeDubbing(it, dubbing) }
        _uiState.update {
            it.copy(
                selectedDubbing = dubbing,
                filteredEpisodes = filterEpisodesByDubbing(it.episodes, dubbing),
                preferredDubbingNotFound = false,
            )
        }
    }

    private fun restoredDubbing(
        anime: Anime,
        history: List<WatchHistoryItem>,
        available: List<String>,
    ): Pair<String?, String?> {
        val saved = settingsRepository.animeDubbing(anime)
        val last =
            history
                .filter { !it.dubbing.isNullOrBlank() }
                .maxByOrNull { it.lastWatchedTimestamp }
                ?.dubbing
        val global = settingsRepository.preferredDubbing.value.trim().takeIf { it.isNotBlank() }
        val wanted = if (saved != null) saved.dubbing else last ?: global
        if (wanted == null) return null to null
        val match =
            available.firstOrNull { it.equals(wanted, ignoreCase = true) }
                ?: if (saved == null && last == null)
                    available.firstOrNull { it.contains(wanted, ignoreCase = true) }
                else null
        return match to wanted.takeIf { match == null }
    }

    private fun filterEpisodesByDubbing(episodes: List<Episode>, dubbing: String?): List<Episode> {
        if (dubbing.isNullOrBlank()) return episodes
        return episodes.filter { ep -> ep.voiceVariants.any { it.label == dubbing } }
    }

    fun openDubbingPicker(episode: Episode) {
        _uiState.update { it.copy(selectedEpisodeForDubbing = episode) }
    }

    fun closeDubbingPicker() {
        _uiState.update { it.copy(selectedEpisodeForDubbing = null) }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val anime = _uiState.value.anime ?: return@launch
            if (_uiState.value.isFavorite) {
                favoritesUseCase.removeFavorite(anime.id)
            } else {
                favoritesUseCase.addFavorite(anime, FavoriteStatus.FAVORITE)
            }
        }
    }

    fun setFavoriteStatus(status: FavoriteStatus?) {
        val state = _uiState.value
        val anime = state.anime ?: return
        viewModelScope.launch {
            when {
                status == null -> favoritesUseCase.removeFavorite(anime.id)
                state.isFavorite -> favoritesUseCase.updateStatus(anime.id, status)
                else -> favoritesUseCase.addFavorite(anime, status)
            }
        }
    }

    fun openEpisodeInExternal(context: Context, episode: Episode, specificDubbing: String? = null) {
        lastExternalRequest = episode to specificDubbing
        if (_uiState.value.isResolvingExternal) return
        val targetId = currentActiveAnimeId
        val selectedAnime = _uiState.value.anime
        val selectedDubbing = specificDubbing ?: _uiState.value.selectedDubbing
        externalJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isResolvingExternal = true, externalError = null) }

                val result = resolveStreamUseCase(targetId, episode.number, selectedDubbing)

                _uiState.update {
                    it.copy(isResolvingExternal = false, selectedEpisodeForDubbing = null)
                }

                result
                    .onSuccess { stream ->
                        val animeTitle = selectedAnime?.displayTitle ?: "Аниме"
                        val targetPkg = settingsRepository.externalPlayerPackage.value
                        val dubbing = stream.dubbing ?: selectedDubbing
                        val previous =
                            watchHistoryUseCase.getHistoryForAnime(targetId).first().firstOrNull {
                                it.episodeNumber == episode.number && it.dubbing == dubbing
                            }

                        val launched =
                            ExternalPlayerHelper.openInExternalPlayer(
                                context = context,
                                stream = stream,
                                title = "$animeTitle — Серия ${episode.number}",
                                targetPackage = targetPkg,
                                positionMs = previous?.resumePositionMs ?: 0L,
                                historyId = "${targetId}_${episode.number}_${dubbing ?: "default"}",
                                onError = { message ->
                                    _uiState.update { it.copy(externalError = message) }
                                },
                            )
                        if (!launched) return@onSuccess

                        selectDubbing(dubbing)

                        // Record history
                        watchHistoryUseCase.recordWatch(
                            WatchHistoryItem(
                                animeId = targetId,
                                animeTitle = animeTitle,
                                animePoster = selectedAnime?.posterUrl,
                                episodeId = episode.id,
                                episodeNumber = episode.number,
                                episodeTitle = episode.title,
                                dubbing = dubbing,
                                streamUrl = stream.streamUrl,
                                iframeUrl = stream.iframeUrl,
                                playbackPositionMs = previous?.resumePositionMs ?: 0L,
                                durationMs = previous?.durationMs ?: 0L,
                                lastWatchedTimestamp = System.currentTimeMillis(),
                            )
                        )
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                externalError = err.message ?: "Не удалось разрешить поток видео"
                            )
                        }
                    }
            }
    }

    fun clearExternalError() {
        _uiState.update { it.copy(externalError = null) }
    }

    fun cancelExternalPlayback() {
        externalJob?.cancel()
        _uiState.update { it.copy(isResolvingExternal = false) }
    }

    class Factory(
        private val animeId: Long,
        private val getAnimeDetailsUseCase: GetAnimeDetailsUseCase,
        private val getEpisodesUseCase: GetEpisodesUseCase,
        private val resolveStreamUseCase: ResolveStreamUseCase,
        private val favoritesUseCase: FavoritesUseCase,
        private val watchHistoryUseCase: WatchHistoryUseCase,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetailsViewModel(
                animeId,
                getAnimeDetailsUseCase,
                getEpisodesUseCase,
                resolveStreamUseCase,
                favoritesUseCase,
                watchHistoryUseCase,
                settingsRepository,
            )
                as T
        }
    }
}

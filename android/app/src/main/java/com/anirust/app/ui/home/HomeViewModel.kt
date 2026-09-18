package com.anirust.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.domain.model.FavoriteItem
import com.anirust.app.domain.model.WatchHistoryItem
import com.anirust.app.domain.usecase.FavoritesUseCase
import com.anirust.app.domain.usecase.GetEpisodesUseCase
import com.anirust.app.domain.usecase.ResolveStreamUseCase
import com.anirust.app.domain.usecase.WatchHistoryUseCase
import com.anirust.app.ui.player.ExternalPlayerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val lastWatched: WatchHistoryItem? = null,
    val recentHistory: List<WatchHistoryItem> = emptyList(),
    val favorites: List<FavoriteItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val nextEpisodeNumber: Int? = null,
)

class HomeViewModel(
    private val watchHistoryUseCase: WatchHistoryUseCase,
    private val favoritesUseCase: FavoritesUseCase,
    private val resolveStreamUseCase: ResolveStreamUseCase,
    private val settingsRepository: SettingsRepository,
    private val getEpisodesUseCase: GetEpisodesUseCase? = null,
) : ViewModel() {

    private var externalJob: Job? = null
    private var lastExternalItem: WatchHistoryItem? = null

    fun retryExternalPlayback(context: Context) {
        lastExternalItem?.let { openInExternalPlayer(context, it) }
    }

    fun cancelExternalPlayback() {
        externalJob?.cancel()
        _isResolvingStream.value = false
    }

    val preferExternalPlayer = settingsRepository.useExternalPlayer

    private val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream: StateFlow<Boolean> = _isResolvingStream.asStateFlow()

    private val _streamResolveError = MutableStateFlow<String?>(null)
    val streamResolveError: StateFlow<String?> = _streamResolveError.asStateFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(
                watchHistoryUseCase.getLastWatched(),
                watchHistoryUseCase.getAllHistory(),
                favoritesUseCase.getAllFavorites(),
            ) { lastWatched, allHistory, favorites ->
                val next =
                    if (lastWatched?.isCompleted == true)
                        getEpisodesUseCase
                            ?.invoke(lastWatched.animeId)
                            ?.getOrNull()
                            ?.filter {
                                it.number > lastWatched.episodeNumber &&
                                    it.voiceVariants.any { voice ->
                                        voice.label == lastWatched.dubbing
                                    }
                            }
                            ?.minByOrNull { it.number }
                            ?.number
                    else null
                HomeUiState(
                    lastWatched = lastWatched,
                    recentHistory = allHistory.take(10),
                    favorites = favorites.take(10),
                    isLoading = false,
                    nextEpisodeNumber = next,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = HomeUiState(isLoading = true),
            )

    fun openInExternalPlayer(context: Context, item: WatchHistoryItem) {
        lastExternalItem = item
        if (_isResolvingStream.value) return
        externalJob =
            viewModelScope.launch {
                _isResolvingStream.value = true
                _streamResolveError.value = null

                val result =
                    resolveStreamUseCase(
                        animeId = item.animeId,
                        episodeNumber = item.episodeNumber,
                        dubbing = item.dubbing,
                    )

                _isResolvingStream.value = false

                result
                    .onSuccess { stream ->
                        val targetPkg = settingsRepository.externalPlayerPackage.value
                        val launched =
                            ExternalPlayerHelper.openInExternalPlayer(
                                context = context,
                                stream = stream,
                                title = "${item.animeTitle} - Серия ${item.episodeNumber}",
                                targetPackage = targetPkg,
                                positionMs = item.resumePositionMs,
                                historyId = item.historyId,
                                onError = { _streamResolveError.value = it },
                            )
                        if (launched)
                            watchHistoryUseCase.recordWatch(
                                item.copy(
                                    streamUrl = stream.streamUrl,
                                    iframeUrl = stream.iframeUrl,
                                    lastWatchedTimestamp = System.currentTimeMillis(),
                                )
                            )
                    }
                    .onFailure { error ->
                        _streamResolveError.value =
                            error.message ?: "Не удалось разрешить видеопоток"
                    }
            }
    }

    fun clearError() {
        _streamResolveError.value = null
    }

    class Factory(
        private val watchHistoryUseCase: WatchHistoryUseCase,
        private val favoritesUseCase: FavoritesUseCase,
        private val resolveStreamUseCase: ResolveStreamUseCase,
        private val settingsRepository: SettingsRepository,
        private val getEpisodesUseCase: GetEpisodesUseCase? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                watchHistoryUseCase,
                favoritesUseCase,
                resolveStreamUseCase,
                settingsRepository,
                getEpisodesUseCase,
            )
                as T
        }
    }
}

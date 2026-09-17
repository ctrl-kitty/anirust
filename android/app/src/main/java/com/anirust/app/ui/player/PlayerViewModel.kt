package com.anirust.app.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.StreamMedia
import com.anirust.app.domain.model.WatchHistoryItem
import com.anirust.app.domain.usecase.GetAnimeDetailsUseCase
import com.anirust.app.domain.usecase.ResolveStreamUseCase
import com.anirust.app.domain.usecase.WatchHistoryUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val anime: Anime? = null,
    val episodeNumber: Int = 1,
    val dubbing: String? = null,
    val stream: StreamMedia? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = true,
)

class PlayerViewModel(
    private val animeId: Long,
    initialEpisodeNumber: Int,
    initialDubbing: String?,
    private val getAnimeDetailsUseCase: GetAnimeDetailsUseCase,
    private val resolveStreamUseCase: ResolveStreamUseCase,
    private val watchHistoryUseCase: WatchHistoryUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            PlayerUiState(episodeNumber = initialEpisodeNumber, dubbing = initialDubbing)
        )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private val saveMutex = Mutex()

    init {
        loadStream()
    }

    fun loadStream() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null, stream = null) }
                val anime = getAnimeDetailsUseCase.getDetails(animeId).getOrNull()
                val current = _uiState.value
                resolveStreamUseCase(animeId, current.episodeNumber, current.dubbing)
                    .onSuccess { media ->
                        val dubbing = media.dubbing ?: current.dubbing
                        val previous =
                            watchHistoryUseCase.getHistoryForAnime(animeId).first().firstOrNull {
                                it.episodeNumber == current.episodeNumber && it.dubbing == dubbing
                            }
                        _uiState.update {
                            it.copy(
                                anime = anime,
                                stream = media,
                                dubbing = dubbing,
                                isLoading = false,
                                currentPositionMs =
                                    previous?.resumePositionMs ?: current.currentPositionMs,
                                durationMs = previous?.durationMs ?: current.durationMs,
                            )
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                anime = anime,
                                isLoading = false,
                                error = error.message ?: "Не удалось загрузить видео",
                            )
                        }
                    }
            }
    }

    fun saveHistoryProgress(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        // Unknown duration/position before preparation must not erase existing progress.
        if (durationMs <= 0 || positionMs < 0) return
        val state = _uiState.value
        if (state.stream == null) return
        state.anime?.let { settingsRepository.setAnimeDubbing(it, state.dubbing) }
        val position = positionMs.coerceAtMost(durationMs)
        _uiState.update {
            it.copy(currentPositionMs = position, durationMs = durationMs, isPlaying = isPlaying)
        }
        val item =
            WatchHistoryItem(
                animeId = animeId,
                animeTitle = state.anime?.displayTitle ?: "Аниме",
                animePoster = state.anime?.posterUrl,
                episodeId = animeId.toString() + "_" + state.episodeNumber,
                episodeNumber = state.episodeNumber,
                dubbing = state.dubbing,
                streamUrl = state.stream.streamUrl,
                iframeUrl = state.stream.iframeUrl,
                playbackPositionMs = position,
                durationMs = durationMs,
            )
        // Start the final short database write before navigation clears this ViewModel.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                saveMutex.withLock { watchHistoryUseCase.recordWatch(item) }
            }
        }
    }

    fun onPlaybackError() {
        _uiState.update {
            it.copy(
                error =
                    "Не удалось воспроизвести видео. Повторите попытку или откройте внешний плеер."
            )
        }
    }

    fun openInExternalPlayer(context: Context) {
        val state = _uiState.value
        val stream = state.stream ?: return
        ExternalPlayerHelper.openInExternalPlayer(
            context = context,
            stream = stream,
            title = (state.anime?.displayTitle ?: "Аниме") + " — Серия " + state.episodeNumber,
            targetPackage = settingsRepository.externalPlayerPackage.value,
            positionMs = state.currentPositionMs,
        )
    }

    class Factory(
        private val animeId: Long,
        private val episodeNumber: Int,
        private val dubbing: String?,
        private val getAnimeDetailsUseCase: GetAnimeDetailsUseCase,
        private val resolveStreamUseCase: ResolveStreamUseCase,
        private val watchHistoryUseCase: WatchHistoryUseCase,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(
                animeId,
                episodeNumber,
                dubbing,
                getAnimeDetailsUseCase,
                resolveStreamUseCase,
                watchHistoryUseCase,
                settingsRepository,
            )
                as T
    }
}

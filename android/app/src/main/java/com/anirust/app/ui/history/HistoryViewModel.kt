package com.anirust.app.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.domain.model.WatchHistoryItem
import com.anirust.app.domain.usecase.ResolveStreamUseCase
import com.anirust.app.domain.usecase.WatchHistoryUseCase
import com.anirust.app.ui.player.ExternalPlayerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val items: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isResolvingExternal: Boolean = false,
    val error: String? = null,
)

class HistoryViewModel(
    private val watchHistoryUseCase: WatchHistoryUseCase,
    private val resolveStreamUseCase: ResolveStreamUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private var externalJob: Job? = null
    private var lastExternalItem: WatchHistoryItem? = null

    fun retryExternalPlayback(context: Context) {
        lastExternalItem?.let { openInExternalPlayer(context, it) }
    }

    fun cancelExternalPlayback() {
        externalJob?.cancel()
        _isResolving.value = false
    }

    val preferExternalPlayer = settingsRepository.useExternalPlayer

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val uiState: StateFlow<HistoryUiState> =
        watchHistoryUseCase
            .getAllHistory()
            .map { list -> HistoryUiState(items = list, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = HistoryUiState(isLoading = true),
            )

    fun deleteItem(id: String) {
        viewModelScope.launch { watchHistoryUseCase.deleteItem(id) }
    }

    fun clearAll() {
        viewModelScope.launch { watchHistoryUseCase.clearHistory() }
    }

    fun openInExternalPlayer(context: Context, item: WatchHistoryItem) {
        lastExternalItem = item
        if (_isResolving.value) return
        externalJob =
            viewModelScope.launch {
                _isResolving.value = true
                _error.value = null

                val result =
                    resolveStreamUseCase(
                        animeId = item.animeId,
                        episodeNumber = item.episodeNumber,
                        dubbing = item.dubbing,
                    )

                _isResolving.value = false

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
                                onError = { _error.value = it },
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
                    .onFailure { err ->
                        _error.value = err.message ?: "Не удалось разрешить видеопоток"
                    }
            }
    }

    fun clearError() {
        _error.value = null
    }

    class Factory(
        private val watchHistoryUseCase: WatchHistoryUseCase,
        private val resolveStreamUseCase: ResolveStreamUseCase,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(watchHistoryUseCase, resolveStreamUseCase, settingsRepository)
                as T
        }
    }
}

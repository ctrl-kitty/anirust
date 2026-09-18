package com.anirust.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.domain.usecase.FavoritesUseCase
import com.anirust.app.domain.usecase.WatchHistoryUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferredDubbing: String = "",
    val useExternalPlayer: Boolean = false,
    val externalPlayerPackage: String = SettingsRepository.PACKAGE_MPVEX,
    val syncIntervalMinutes: Int = SettingsRepository.DEFAULT_SYNC_INTERVAL,
    val syncWatchedProgress: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val watchHistoryUseCase: WatchHistoryUseCase,
    private val favoritesUseCase: FavoritesUseCase,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
                settingsRepository.preferredDubbing,
                settingsRepository.useExternalPlayer,
                settingsRepository.externalPlayerPackage,
                settingsRepository.syncIntervalMinutes,
                settingsRepository.syncWatchedProgress,
            ) { dub, useExt, pkg, interval, syncProgress ->
                SettingsUiState(
                    preferredDubbing = dub,
                    useExternalPlayer = useExt,
                    externalPlayerPackage = pkg,
                    syncIntervalMinutes = interval,
                    syncWatchedProgress = syncProgress,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsUiState(),
            )

    fun setPreferredDubbing(dubbing: String) {
        settingsRepository.setPreferredDubbing(dubbing)
    }

    fun setUseExternalPlayer(useExternal: Boolean) {
        settingsRepository.setUseExternalPlayer(useExternal)
    }

    fun setExternalPlayerPackage(pkg: String) {
        settingsRepository.setExternalPlayerPackage(pkg)
    }

    fun setSyncIntervalMinutes(minutes: Int) {
        settingsRepository.setSyncIntervalMinutes(minutes)
    }

    fun setSyncWatchedProgress(enabled: Boolean) =
        settingsRepository.setSyncWatchedProgress(enabled)

    fun clearAllHistory() {
        viewModelScope.launch { watchHistoryUseCase.clearHistory() }
    }

    fun clearAllFavorites() {
        viewModelScope.launch { favoritesUseCase.clearFavorites() }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val watchHistoryUseCase: WatchHistoryUseCase,
        private val favoritesUseCase: FavoritesUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, watchHistoryUseCase, favoritesUseCase) as T
        }
    }
}

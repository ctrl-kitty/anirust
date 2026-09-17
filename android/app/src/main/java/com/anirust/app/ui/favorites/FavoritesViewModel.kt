package com.anirust.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anirust.app.domain.model.FavoriteItem
import com.anirust.app.domain.model.FavoriteStatus
import com.anirust.app.domain.usecase.FavoritesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val items: List<FavoriteItem> = emptyList(),
    val selectedStatus: FavoriteStatus? = null,
    val isLoading: Boolean = false,
)

class FavoritesViewModel(private val favoritesUseCase: FavoritesUseCase) : ViewModel() {

    private val _selectedStatus = MutableStateFlow<FavoriteStatus?>(null)
    val selectedStatus: StateFlow<FavoriteStatus?> = _selectedStatus.asStateFlow()

    val uiState: StateFlow<FavoritesUiState> =
        combine(favoritesUseCase.getAllFavorites(), _selectedStatus) { allFavorites, status ->
                val filtered =
                    if (status == null) {
                        allFavorites
                    } else {
                        allFavorites.filter { it.status == status }
                    }
                FavoritesUiState(items = filtered, selectedStatus = status, isLoading = false)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = FavoritesUiState(isLoading = true),
            )

    fun selectStatus(status: FavoriteStatus?) {
        _selectedStatus.value = status
    }

    fun removeFavorite(animeId: Long) {
        viewModelScope.launch { favoritesUseCase.removeFavorite(animeId) }
    }

    class Factory(private val favoritesUseCase: FavoritesUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FavoritesViewModel(favoritesUseCase) as T
        }
    }
}

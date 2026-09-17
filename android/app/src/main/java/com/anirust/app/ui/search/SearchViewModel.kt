package com.anirust.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.usecase.SearchAnimeUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<Anime> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false,
)

class SearchViewModel
private constructor(
    private val search: suspend (String, (List<Anime>) -> Unit) -> Result<List<Anime>>
) : ViewModel() {

    constructor(
        search: suspend (String) -> Result<List<Anime>>
    ) : this({ query, _ -> search(query) })

    constructor(
        useCase: SearchAnimeUseCase
    ) : this({ query, onCatalogReady -> useCase(query, onCatalogReady) })

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var generation = 0L

    fun onQueryChange(newQuery: String) {
        _uiState.update {
            it.copy(query = newQuery, error = null, isLoading = newQuery.isNotBlank())
        }

        searchJob?.cancel()
        val request = ++generation

        if (newQuery.isBlank()) {
            _uiState.update {
                it.copy(results = emptyList(), isLoading = false, hasSearched = false, error = null)
            }
            return
        }

        searchJob =
            viewModelScope.launch {
                delay(400) // Debounce
                performSearch(newQuery, request)
            }
    }

    fun searchImmediately(query: String) {
        searchJob?.cancel()
        val request = ++generation
        _uiState.update { it.copy(query = query) }
        if (query.isBlank()) {
            _uiState.value = SearchUiState(query = query)
            return
        }
        searchJob = viewModelScope.launch { performSearch(query, request) }
    }

    private suspend fun performSearch(query: String, request: Long) {
        if (query.isBlank()) return

        _uiState.update { it.copy(isLoading = true, error = null, hasSearched = true) }

        var finished = false
        val result =
            search(query.trim()) { preview ->
                // Callbacks originate on IO; check the current generation on Main before
                // publishing.
                viewModelScope.launch {
                    if (request == generation && !finished)
                        _uiState.update { it.copy(results = preview, error = null) }
                }
            }
        finished = true
        if (request != generation) return
        result
            .onSuccess { animes ->
                _uiState.update { it.copy(results = animes, isLoading = false, error = null) }
            }
            .onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, error = err.message ?: "Ошибка при поиске аниме")
                }
            }
    }

    class Factory(private val searchAnimeUseCase: SearchAnimeUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(searchAnimeUseCase) as T
        }
    }
}

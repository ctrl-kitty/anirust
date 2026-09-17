package com.anirust.app.domain.usecase

import com.anirust.app.data.repository.AnimeRepository
import com.anirust.app.domain.model.Anime

class SearchAnimeUseCase(private val repository: AnimeRepository) {
    suspend operator fun invoke(
        query: String,
        onCatalogReady: ((List<Anime>) -> Unit)? = null,
    ): Result<List<Anime>> {
        return repository.search(query.trim(), onCatalogReady)
    }
}

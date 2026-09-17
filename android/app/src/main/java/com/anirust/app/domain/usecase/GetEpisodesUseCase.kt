package com.anirust.app.domain.usecase

import com.anirust.app.data.repository.AnimeRepository
import com.anirust.app.domain.model.Episode

class GetEpisodesUseCase(
    private val repository: AnimeRepository
) {
    suspend operator fun invoke(animeId: Long): Result<List<Episode>> {
        return repository.getEpisodes(animeId)
    }
}

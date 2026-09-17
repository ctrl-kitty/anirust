package com.anirust.app.domain.usecase

import com.anirust.app.data.repository.AnimeRepository
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.SeriesEntry

class GetAnimeDetailsUseCase(
    private val repository: AnimeRepository
) {
    suspend fun getDetails(animeId: Long): Result<Anime> {
        return repository.getAnimeDetails(animeId)
    }

    suspend fun getSeries(animeId: Long): Result<List<SeriesEntry>> {
        return repository.getSeries(animeId)
    }
}

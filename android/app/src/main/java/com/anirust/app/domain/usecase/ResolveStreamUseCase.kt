package com.anirust.app.domain.usecase

import com.anirust.app.data.repository.AnimeRepository
import com.anirust.app.domain.model.StreamMedia

class ResolveStreamUseCase(
    private val repository: AnimeRepository
) {
    suspend operator fun invoke(
        animeId: Long,
        episodeNumber: Int,
        dubbing: String? = null
    ): Result<StreamMedia> {
        return repository.resolveStream(animeId, episodeNumber, dubbing)
    }
}

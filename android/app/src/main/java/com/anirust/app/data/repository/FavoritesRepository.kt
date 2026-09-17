package com.anirust.app.data.repository

import com.anirust.app.data.local.dao.FavoritesDao
import com.anirust.app.data.local.entity.FavoriteEntity
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.FavoriteItem
import com.anirust.app.domain.model.FavoriteStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoritesRepository(private val dao: FavoritesDao) {
    fun getAllFavorites(): Flow<List<FavoriteItem>> {
        return dao.getAllFavorites().map { list -> list.map { it.toDomain() } }
    }

    fun getFavoritesByStatus(status: FavoriteStatus): Flow<List<FavoriteItem>> {
        return dao.getFavoritesByStatus(status.name).map { list -> list.map { it.toDomain() } }
    }

    fun isFavorite(animeId: Long): Flow<Boolean> {
        return dao.isFavorite(animeId)
    }

    fun getFavorite(animeId: Long): Flow<FavoriteItem?> {
        return dao.getFavorite(animeId).map { it?.toDomain() }
    }

    suspend fun addFavorite(anime: Anime, status: FavoriteStatus = FavoriteStatus.FAVORITE) {
        val entity =
            FavoriteEntity(
                animeId = anime.id,
                title = anime.title,
                originalTitle = anime.originalTitle,
                posterUrl = anime.posterUrl,
                score = anime.score,
                episodesCount = anime.episodesCount,
                status = status.name,
                addedAt = System.currentTimeMillis(),
            )
        dao.insertOrUpdate(entity)
    }

    suspend fun removeFavorite(animeId: Long) {
        dao.delete(animeId)
    }

    suspend fun updateStatus(animeId: Long, status: FavoriteStatus) {
        dao.updateStatus(animeId, status.name)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}

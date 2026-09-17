package com.anirust.app.data.repository

import com.anirust.app.data.local.dao.WatchHistoryDao
import com.anirust.app.data.local.entity.WatchHistoryEntity
import com.anirust.app.domain.model.WatchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WatchHistoryRepository(private val dao: WatchHistoryDao) {
    fun getAllHistory(): Flow<List<WatchHistoryItem>> {
        return dao.getAllHistory().map { list -> list.map { it.toDomain() } }
    }

    fun getLastWatched(): Flow<WatchHistoryItem?> {
        return dao.getLastWatched().map { it?.toDomain() }
    }

    fun getLastWatchedForAnime(animeId: Long): Flow<WatchHistoryItem?> {
        return dao.getLastWatchedForAnime(animeId).map { it?.toDomain() }
    }

    fun getHistoryForAnime(animeId: Long): Flow<List<WatchHistoryItem>> {
        return dao.getHistoryForAnime(animeId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun saveWatchProgress(item: WatchHistoryItem) {
        val entity = WatchHistoryEntity.fromDomain(item)
        dao.insertOrUpdate(entity)
    }

    fun getHistoryForAnimeIds(animeIds: List<Long>): Flow<List<WatchHistoryItem>> =
        dao.getHistoryForAnimeIds(animeIds).map { list -> list.map { it.toDomain() } }

    suspend fun deleteHistoryItem(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteForAnime(animeId: Long) {
        dao.deleteForAnime(animeId)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}

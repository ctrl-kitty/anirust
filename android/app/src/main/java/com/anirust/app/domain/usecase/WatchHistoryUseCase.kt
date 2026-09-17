package com.anirust.app.domain.usecase

import com.anirust.app.data.repository.WatchHistoryRepository
import com.anirust.app.domain.model.WatchHistoryItem
import kotlinx.coroutines.flow.Flow

class WatchHistoryUseCase(private val repository: WatchHistoryRepository) {
    fun getAllHistory(): Flow<List<WatchHistoryItem>> = repository.getAllHistory()

    fun getLastWatched(): Flow<WatchHistoryItem?> = repository.getLastWatched()

    fun getLastWatchedForAnime(animeId: Long): Flow<WatchHistoryItem?> =
        repository.getLastWatchedForAnime(animeId)

    fun getHistoryForAnime(animeId: Long): Flow<List<WatchHistoryItem>> =
        repository.getHistoryForAnime(animeId)

    suspend fun recordWatch(item: WatchHistoryItem) {
        repository.saveWatchProgress(item)
    }

    fun getHistoryForAnimeIds(animeIds: List<Long>): Flow<List<WatchHistoryItem>> =
        repository.getHistoryForAnimeIds(animeIds)

    suspend fun deleteItem(id: String) {
        repository.deleteHistoryItem(id)
    }

    suspend fun deleteForAnime(animeId: Long) {
        repository.deleteForAnime(animeId)
    }

    suspend fun clearHistory() {
        repository.clearAll()
    }
}

package com.anirust.app.domain.usecase

import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.data.repository.WatchHistoryRepository
import com.anirust.app.domain.model.WatchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchHistoryUseCase(
    private val repository: WatchHistoryRepository,
    private val settings: SettingsRepository? = null,
) {
    private val _completionPrompt = MutableStateFlow<WatchHistoryItem?>(null)
    val completionPrompt = _completionPrompt.asStateFlow()

    suspend fun finishPlayback(historyId: String) {
        val item = repository.getById(historyId) ?: return
        if (!item.isCompleted && item.progressPercent >= 90) _completionPrompt.value = item
    }

    fun dismissCompletionPrompt() {
        _completionPrompt.value = null
    }

    suspend fun confirmCompletion(rememberPercent: Boolean) {
        val item = _completionPrompt.value ?: return
        repository.markEpisode(item, true)
        if (rememberPercent) settings?.setCompletionThreshold(item.animeId, item.progressPercent)
        _completionPrompt.value = null
    }

    suspend fun markEpisode(item: WatchHistoryItem, watched: Boolean) =
        repository.markEpisode(item, watched)

    fun getAllHistory(): Flow<List<WatchHistoryItem>> = repository.getAllHistory()

    fun getLastWatched(): Flow<WatchHistoryItem?> = repository.getLastWatched()

    fun getLastWatchedForAnime(animeId: Long): Flow<WatchHistoryItem?> =
        repository.getLastWatchedForAnime(animeId)

    fun getHistoryForAnime(animeId: Long): Flow<List<WatchHistoryItem>> =
        repository.getHistoryForAnime(animeId)

    suspend fun recordWatch(item: WatchHistoryItem) {
        repository.saveWatchProgress(item)
    }

    suspend fun updateProgress(historyId: String, positionMs: Long, durationMs: Long) {
        repository.updateProgress(historyId, positionMs, durationMs)
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

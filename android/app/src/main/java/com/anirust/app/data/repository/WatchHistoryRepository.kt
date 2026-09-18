package com.anirust.app.data.repository

import com.anirust.app.data.local.dao.WatchHistoryDao
import com.anirust.app.data.local.entity.WatchHistoryEntity
import com.anirust.app.domain.model.WatchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WatchHistoryRepository(
    private val dao: WatchHistoryDao,
    private val settings: SettingsRepository? = null,
) {
    private val thresholds =
        settings?.completionThresholds ?: MutableStateFlow(emptyMap<Long, Int>())
    private val writes = Mutex()

    private fun WatchHistoryEntity.domain() =
        toDomain().copy(completionThreshold = thresholds.value[animeId] ?: 95)

    private fun Flow<List<WatchHistoryEntity>>.domainList() =
        combine(thresholds) { list, _ -> list.map { it.domain() } }

    suspend fun getById(id: String): WatchHistoryItem? = dao.getById(id)?.domain()

    fun getAllHistory(): Flow<List<WatchHistoryItem>> {
        return dao.getAllHistory().domainList()
    }

    fun getLastWatched(): Flow<WatchHistoryItem?> {
        return dao.getLastWatched().combine(thresholds) { item, _ -> item?.domain() }
    }

    fun getLastWatchedForAnime(animeId: Long): Flow<WatchHistoryItem?> {
        return dao.getLastWatchedForAnime(animeId).combine(thresholds) { item, _ -> item?.domain() }
    }

    fun getHistoryForAnime(animeId: Long): Flow<List<WatchHistoryItem>> {
        return dao.getHistoryForAnime(animeId).domainList()
    }

    suspend fun saveWatchProgress(item: WatchHistoryItem) {
        writes.withLock {
            val existing = dao.getById(item.historyId)
            val entity =
                WatchHistoryEntity.fromDomain(item)
                    .copy(
                        completionOverride =
                            item.completionOverride
                                ?: if (
                                    item
                                        .copy(
                                            completionThreshold =
                                                thresholds.value[item.animeId] ?: 95
                                        )
                                        .isCompleted || existing?.domain()?.isCompleted == true
                                )
                                    true
                                else existing?.completionOverride
                    )
            dao.insertOrUpdate(entity)
        }
    }

    suspend fun updateProgress(historyId: String, positionMs: Long, durationMs: Long) {
        if (positionMs < 0 || durationMs <= 0) return
        writes.withLock {
            val previous = dao.getById(historyId) ?: return@withLock
            val updated =
                previous.copy(
                    playbackPositionMs = positionMs.coerceAtMost(durationMs),
                    durationMs = durationMs,
                    lastWatchedTimestamp = System.currentTimeMillis(),
                )
            dao.insertOrUpdate(
                updated.copy(
                    completionOverride =
                        if (previous.domain().isCompleted || updated.domain().isCompleted) true
                        else previous.completionOverride
                )
            )
        }
    }

    fun getHistoryForAnimeIds(animeIds: List<Long>): Flow<List<WatchHistoryItem>> =
        dao.getHistoryForAnimeIds(animeIds).domainList()

    suspend fun markEpisode(item: WatchHistoryItem, watched: Boolean) =
        writes.withLock {
            val ids = settings?.animeAliases(item.animeId) ?: listOf(item.animeId)
            if (dao.getById(item.historyId) == null)
                dao.insertOrUpdate(
                    WatchHistoryEntity.fromDomain(item.copy(completionOverride = watched))
                )
            dao.markEpisode(ids, item.episodeNumber, watched)
        }

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

package com.anirust.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anirust.app.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WatchHistoryEntity?

    @Query(
        "UPDATE watch_history SET completionOverride = :watched, playbackPositionMs = CASE WHEN :watched THEN playbackPositionMs ELSE 0 END WHERE animeId IN (:animeIds) AND episodeNumber = :episode"
    )
    suspend fun markEpisode(animeIds: List<Long>, episode: Int, watched: Boolean)

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC LIMIT 1")
    fun getLastWatched(): Flow<WatchHistoryEntity?>

    @Query(
        "SELECT * FROM watch_history WHERE animeId = :animeId ORDER BY lastWatchedTimestamp DESC LIMIT 1"
    )
    fun getLastWatchedForAnime(animeId: Long): Flow<WatchHistoryEntity?>

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId ORDER BY episodeNumber ASC")
    fun getHistoryForAnime(animeId: Long): Flow<List<WatchHistoryEntity>>

    @Query(
        "SELECT * FROM watch_history WHERE animeId IN (:animeIds) ORDER BY lastWatchedTimestamp DESC"
    )
    fun getHistoryForAnimeIds(animeIds: List<Long>): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: WatchHistoryEntity)

    @Query(
        "UPDATE watch_history SET playbackPositionMs = :positionMs, durationMs = :durationMs, " +
            "lastWatchedTimestamp = :timestamp WHERE id = :historyId"
    )
    suspend fun updateProgress(
        historyId: String,
        positionMs: Long,
        durationMs: Long,
        timestamp: Long,
    )

    @Query("DELETE FROM watch_history WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM watch_history WHERE animeId = :animeId")
    suspend fun deleteForAnime(animeId: Long)

    @Query("DELETE FROM watch_history") suspend fun clearAll()
}

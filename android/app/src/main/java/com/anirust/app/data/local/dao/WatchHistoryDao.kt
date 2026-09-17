package com.anirust.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anirust.app.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

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

    @Query("DELETE FROM watch_history WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM watch_history WHERE animeId = :animeId")
    suspend fun deleteForAnime(animeId: Long)

    @Query("DELETE FROM watch_history") suspend fun clearAll()
}

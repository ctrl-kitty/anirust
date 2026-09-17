package com.anirust.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anirust.app.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE status = :status ORDER BY addedAt DESC")
    fun getFavoritesByStatus(status: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE animeId = :animeId LIMIT 1")
    fun getFavorite(animeId: Long): Flow<FavoriteEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE animeId = :animeId)")
    fun isFavorite(animeId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE animeId = :animeId") suspend fun delete(animeId: Long)

    @Query("UPDATE favorites SET status = :status WHERE animeId = :animeId")
    suspend fun updateStatus(animeId: Long, status: String)

    @Query("DELETE FROM favorites") suspend fun clearAll()
}

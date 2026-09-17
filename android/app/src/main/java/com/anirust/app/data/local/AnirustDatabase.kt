package com.anirust.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anirust.app.data.local.dao.FavoritesDao
import com.anirust.app.data.local.dao.WatchHistoryDao
import com.anirust.app.data.local.entity.FavoriteEntity
import com.anirust.app.data.local.entity.WatchHistoryEntity

@Database(
    entities = [WatchHistoryEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AnirustDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun favoritesDao(): FavoritesDao

    companion object {
        @Volatile private var INSTANCE: AnirustDatabase? = null

        fun getInstance(context: Context): AnirustDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                AnirustDatabase::class.java,
                                "anirust_database.db",
                            )
                            .build()
                            .also { INSTANCE = it }
                }
        }
    }
}

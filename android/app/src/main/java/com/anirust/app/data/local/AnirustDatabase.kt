package com.anirust.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anirust.app.data.local.dao.FavoritesDao
import com.anirust.app.data.local.dao.WatchHistoryDao
import com.anirust.app.data.local.entity.FavoriteEntity
import com.anirust.app.data.local.entity.WatchHistoryEntity

@Database(
    entities = [WatchHistoryEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AnirustDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun favoritesDao(): FavoritesDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE watch_history ADD COLUMN completionOverride INTEGER")
                }
            }
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
                            .addMigrations(MIGRATION_1_2)
                            .build()
                            .also { INSTANCE = it }
                }
        }
    }
}

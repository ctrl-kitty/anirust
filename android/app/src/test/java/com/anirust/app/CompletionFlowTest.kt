package com.anirust.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anirust.app.data.local.AnirustDatabase
import com.anirust.app.data.repository.*
import com.anirust.app.domain.model.*
import com.anirust.app.domain.usecase.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CompletionFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AnirustDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var history: WatchHistoryUseCase

    private fun item(position: Long, episode: Int = 1, anime: Long = 111) =
        WatchHistoryItem(
            animeId = anime,
            animeTitle = "Naruto",
            episodeId = "${anime}_$episode",
            episodeNumber = episode,
            playbackPositionMs = position,
            durationMs = 100_000,
        )

    @Before
    fun setup() {
        context
            .getSharedPreferences("anirust_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settings = SettingsRepository(context)
        db =
            Room.inMemoryDatabaseBuilder(context, AnirustDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        history =
            WatchHistoryUseCase(WatchHistoryRepository(db.watchHistoryDao(), settings), settings)
    }

    @After
    fun cleanup() {
        db.close()
    }

    @Test
    fun asksFromNinetyAndAutomaticallyCompletesAtNinetyFive() = runBlocking {
        history.recordWatch(item(89_999))
        history.finishPlayback(item(0).historyId)
        assertNull(history.completionPrompt.value)
        history.recordWatch(item(90_000))
        history.finishPlayback(item(0).historyId)
        assertEquals(90, history.completionPrompt.value!!.progressPercent)
        history.dismissCompletionPrompt()
        assertFalse(history.getAllHistory().first().single().isCompleted)
        history.recordWatch(item(95_000))
        history.finishPlayback(item(0).historyId)
        assertNull(history.completionPrompt.value)
        assertTrue(history.getAllHistory().first().single().isCompleted)
        // Rewatching does not silently remove the completed mark.
        history.recordWatch(item(10_000))
        assertTrue(history.getAllHistory().first().single().isCompleted)
    }

    @Test
    fun rememberedPercentageBelongsToOneTitleIncludingItsConfirmedAliases() = runBlocking {
        settings.registerAnime(Anime(111, shikimoriId = 20, title = "Naruto"))
        history.recordWatch(item(92_500))
        history.finishPlayback(item(0).historyId)
        history.confirmCompletion(true)
        val restored = SettingsRepository(context)
        assertEquals(92, restored.completionThresholds.value[111L])
        assertEquals(92, restored.completionThresholds.value[-20L])
        settings.registerAnime(Anime(111, title = "Metadata temporarily unavailable"))
        assertTrue(-20L in settings.animeAliases(111))
        history.updateProgress(item(0).historyId, 92_500, 100_000)
        history.recordWatch(item(92_000, episode = 2, anime = -20))
        history.recordWatch(item(92_000, anime = 112))
        val rows = history.getAllHistory().first()
        assertTrue(rows.first { it.animeId == -20L }.isCompleted)
        assertFalse(rows.first { it.animeId == 112L }.isCompleted)
        settings.setCompletionThreshold(-20, null)
        assertTrue(history.getAllHistory().first().first { it.animeId == -20L }.isCompleted)
        assertTrue(settings.completionThresholds.value.isEmpty())
    }

    @Test
    fun confirmationWithoutRememberingAndManualUnwatchKeepRealPositionAndCanBeWatchedAgain() =
        runBlocking {
            settings.registerAnime(Anime(111, shikimoriId = 20, title = "Naruto"))
            history.recordWatch(item(91_000))
            history.finishPlayback(item(0).historyId)
            history.confirmCompletion(false)
            val completed = history.getAllHistory().first().single()
            assertEquals(91_000, completed.playbackPositionMs)
            assertTrue(completed.isCompleted)
            assertTrue(settings.completionThresholds.value.isEmpty())
            history.recordWatch(item(20_000, anime = -20))
            history.markEpisode(completed, false)
            assertTrue(
                history.getAllHistory().first().all {
                    !it.isCompleted && it.completionOverride == false && it.resumePositionMs == 0L
                }
            )
            history.updateProgress(completed.historyId, 95_000, 100_000)
            assertTrue(history.getAllHistory().first().first { it.animeId == 111L }.isCompleted)
        }

    @Test
    fun undoRestoresTheOriginalBookmarkWithoutOverwritingANewerChoice() = runBlocking {
        val messages = UiMessages()
        val favorites = FavoritesUseCase(FavoritesRepository(db.favoritesDao()), messages)
        val anime = Anime(111, title = "Naruto")
        favorites.addFavorite(anime, FavoriteStatus.WATCHING)
        messages.events.first()
        val original = favorites.getFavorite(111).first()!!
        favorites.removeFavorite(111)
        val undo = messages.events.first()
        assertEquals("Отменить", undo.actionLabel)
        assertNull(favorites.getFavorite(111).first())
        undo.action!!.invoke()
        assertEquals(original, favorites.getFavorite(111).first())
        favorites.updateStatus(111, FavoriteStatus.COMPLETED)
        undo.action.invoke()
        assertEquals(FavoriteStatus.COMPLETED, favorites.getFavorite(111).first()!!.status)
    }

    @Test
    fun cloudCounterNeverSkipsMissingEpisodesOrMovesBackwards() {
        assertEquals(3, contiguousWatchedCount(3, setOf(5, 6), 12))
        assertEquals(5, contiguousWatchedCount(3, setOf(4, 5, 7), 12))
        assertEquals(4, contiguousWatchedCount(3, setOf(4, 5), 4))
        assertEquals(5, contiguousWatchedCount(5, setOf(1), 12))
    }

    @Test
    fun cloudProgressPreservesScoreAndStatusWithoutReloadingLists() = runBlocking {
        settings.registerAnime(Anime(111, shikimoriId = 20, title = "Naruto"))
        history.recordWatch(item(95_000, episode = 4))
        history.recordWatch(item(95_000, episode = 5))
        val api = FakeShikimoriAccountApi()
        val scope =
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined
            )
        val account =
            ShikimoriAccountRepository(
                api,
                MemoryShikimoriStore(shikimoriTestSession()),
                UiMessages(),
                scope,
                kotlinx.coroutines.Dispatchers.Unconfined,
                pause = {},
            )
        settings.setSyncWatchedProgress(true)
        val job = scope.launch { WatchedProgressSync(history, account, settings).run() }
        try {
            kotlinx.coroutines.withTimeout(5000) {
                account.state.first { it.rates.singleOrNull()?.episodes == 5 && !it.busy }
            }
            assertEquals(7, api.serverRate.score)
            assertEquals("watching", api.serverRate.status)
            assertTrue(api.seenHeaders.isEmpty())
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun versionOneDatabaseMigratesWithoutLosingHistoryOrBookmarks() = runBlocking {
        val name = "completion-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL(
                "CREATE TABLE watch_history (id TEXT NOT NULL PRIMARY KEY, animeId INTEGER NOT NULL, seriesId TEXT, animeTitle TEXT NOT NULL, animePoster TEXT, episodeId TEXT NOT NULL, episodeNumber INTEGER NOT NULL, episodeTitle TEXT, dubbing TEXT, streamUrl TEXT, iframeUrl TEXT, playbackPositionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastWatchedTimestamp INTEGER NOT NULL)"
            )
            old.execSQL(
                "CREATE TABLE favorites (animeId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, originalTitle TEXT, posterUrl TEXT, score REAL, episodesCount INTEGER, status TEXT NOT NULL, addedAt INTEGER NOT NULL)"
            )
            old.execSQL(
                "INSERT INTO watch_history(id, animeId, animeTitle, episodeId, episodeNumber, playbackPositionMs, durationMs, lastWatchedTimestamp) VALUES('111_1_default',111,'Naruto','1',1,92000,100000,1)"
            )
            old.execSQL(
                "INSERT INTO favorites(animeId, title, status, addedAt) VALUES(111,'Naruto','WATCHING',1)"
            )
            old.version = 1
        }
        val migrated =
            Room.databaseBuilder(context, AnirustDatabase::class.java, name)
                .addMigrations(AnirustDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        try {
            val row = migrated.watchHistoryDao().getAllHistory().first().single()
            assertEquals(92_000, row.playbackPositionMs)
            assertNull(row.completionOverride)
            assertEquals(
                "WATCHING",
                migrated.favoritesDao().getAllFavorites().first().single().status,
            )
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}

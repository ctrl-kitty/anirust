package com.anirust.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anirust.app.data.local.AnirustDatabase
import com.anirust.app.data.local.entity.FavoriteEntity
import com.anirust.app.data.remote.resolver.KodikResolver
import com.anirust.app.data.remote.yummy.YummyVideoData
import com.anirust.app.data.remote.yummy.YummyVideoItem
import com.anirust.app.data.remote.yummy.YummyVideosResponse
import com.anirust.app.data.repository.*
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.FavoriteStatus
import com.anirust.app.domain.model.StreamMedia
import com.anirust.app.domain.model.WatchHistoryItem
import com.anirust.app.domain.usecase.*
import com.anirust.app.ui.details.DetailsViewModel
import com.anirust.app.ui.navigation.Screen
import com.anirust.app.ui.player.ExternalPlayerHelper
import com.anirust.app.ui.player.PlayerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidFlowTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())
    private lateinit var database: AnirustDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: AnimeRepository
    private lateinit var favorites: FavoritesUseCase
    private lateinit var history: WatchHistoryUseCase
    private val store = ViewModelStore()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("anirust_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settings = SettingsRepository(context)
        database =
            Room.inMemoryDatabaseBuilder(context, AnirustDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        val yummy =
            FakeYummyApi().apply {
                videos = {
                    YummyVideosResponse(
                        listOf(
                            YummyVideoItem(
                                number = "1",
                                iframeUrl = "https://cdn.test/video.mp4",
                                data = YummyVideoData(dubbing = "Studio A"),
                            )
                        )
                    )
                }
            }
        repository =
            AnimeRepository(
                yummy,
                FakeShikimoriApi(),
                KodikResolver(OkHttpClient()),
                main.dispatcher,
            )
        favorites = FavoritesUseCase(FavoritesRepository(database.favoritesDao()))
        history = WatchHistoryUseCase(WatchHistoryRepository(database.watchHistoryDao()))
    }

    @After
    fun teardown() {
        store.clear()
        database.close()
    }

    @Test
    fun mpvExReceivesVideoTitlePositionAndOrderedHeaders() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        var launched: Intent? = null
        val context =
            object : ContextWrapper(base) {
                override fun startActivity(intent: Intent) {
                    launched = intent
                }
            }
        settings.setExternalPlayerPackage(SettingsRepository.PACKAGE_MPVEX)
        assertEquals(
            SettingsRepository.PACKAGE_MPVEX,
            SettingsRepository(base).externalPlayerPackage.value,
        )
        val stream =
            StreamMedia(
                streamUrl = "https://cdn.test/episode.m3u8",
                headers =
                    linkedMapOf(
                        "Referer" to "https://player.test/",
                        "Origin" to "https://player.test",
                        "user-agent" to "TestAgent",
                    ),
            )
        assertTrue(
            ExternalPlayerHelper.openInExternalPlayer(
                context,
                stream,
                "Серия 2",
                settings.externalPlayerPackage.value,
                123000L,
            )
        )
        val intent = requireNotNull(launched)
        assertEquals(SettingsRepository.PACKAGE_MPVEX, intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(stream.streamUrl, intent.data.toString())
        assertEquals("video/*", intent.type)
        assertEquals("Серия 2", intent.getStringExtra("title"))
        assertEquals(123000, intent.getIntExtra("position", -1))
        assertArrayEquals(
            arrayOf(
                "User-Agent",
                "TestAgent",
                "Referer",
                "https://player.test/",
                "Origin",
                "https://player.test",
            ),
            intent.getStringArrayExtra("headers"),
        )
    }

    @Test
    fun mpvExHeadersKeepRefererWhenProviderDoesNotSupplyUserAgent() {
        assertArrayEquals(
            arrayOf("User-Agent", "AnirustAndroid/1.0", "Referer", "https://player.test/"),
            ExternalPlayerHelper.externalHeaders(mapOf("Referer" to "https://player.test/")),
        )
    }

    @Test
    fun selectedSeasonUsesItsOwnFavoriteAndHistory() = runTest {
        favorites.addFavorite(
            com.anirust.app.domain.model.Anime(2, title = "Second"),
            FavoriteStatus.WATCHING,
        )
        history.recordWatch(
            WatchHistoryItem(
                2,
                animeTitle = "Second",
                episodeId = "2_1",
                episodeNumber = 1,
                playbackPositionMs = 120000,
                durationMs = 120000,
            )
        )
        val vm =
            DetailsViewModel(
                1,
                GetAnimeDetailsUseCase(repository),
                GetEpisodesUseCase(repository),
                ResolveStreamUseCase(repository),
                favorites,
                history,
                settings,
            )
        store.put("details", vm)
        vm.uiState.first { !it.isLoading }
        assertFalse(vm.uiState.value.isFavorite)
        vm.selectSeries("2")
        vm.uiState.first { !it.isLoading && it.isFavorite }
        vm.uiState.first { it.watchedEpisodeNumbers.contains(1) }
        assertEquals(FavoriteStatus.WATCHING, vm.uiState.value.favoriteStatus)
        vm.setFavoriteStatus(FavoriteStatus.COMPLETED)
        assertEquals(FavoriteStatus.COMPLETED, favorites.getFavorite(2).first()!!.status)
        assertNull(favorites.getFavorite(1).first())
    }

    @Test
    fun streamLoadingPreservesResumePositionAndDoesNotCreatePhantomHistory() = runTest {
        history.recordWatch(
            WatchHistoryItem(
                111,
                animeTitle = "Title",
                episodeId = "111_1",
                episodeNumber = 1,
                dubbing = "Studio A",
                playbackPositionMs = 42000,
                durationMs = 120000,
            )
        )
        val vm =
            PlayerViewModel(
                111,
                1,
                null,
                GetAnimeDetailsUseCase(repository),
                ResolveStreamUseCase(repository),
                history,
                settings,
            )
        store.put("player", vm)
        vm.uiState.first { !it.isLoading }
        assertEquals(42000L, vm.uiState.value.currentPositionMs)
        assertEquals("Studio A", vm.uiState.value.dubbing)
        assertEquals(1, history.getAllHistory().first().size)
        vm.saveHistoryProgress(0, -1, true)
        assertEquals(42000L, history.getAllHistory().first().single().playbackPositionMs)
        vm.saveHistoryProgress(55000, 120000, false)
        store.clear()
        history.getAllHistory().first { it.single().playbackPositionMs == 55000L }
        assertEquals(55000L, history.getAllHistory().first().single().playbackPositionMs)
    }

    @Test
    fun favoriteStatusUpdatePreservesAddedDateAndHistoryStaysSeparatedByDubbing() = runBlocking {
        database
            .favoritesDao()
            .insertOrUpdate(FavoriteEntity(1, "One", null, null, null, null, "WATCHING", 123))
        favorites.updateStatus(1, FavoriteStatus.COMPLETED)
        assertEquals(123L, favorites.getFavorite(1).first()!!.addedAt)
        val first =
            WatchHistoryItem(
                1,
                animeTitle = "One",
                episodeId = "1_1",
                episodeNumber = 1,
                dubbing = "A",
            )
        history.recordWatch(first)
        history.recordWatch(first.copy(dubbing = "B"))
        history.deleteItem(first.historyId)
        assertEquals("B", history.getAllHistory().first().single().dubbing)
    }

    @Test
    fun multiWordDubbingCanBeTypedAndPersistsNormalized() {
        settings.setPreferredDubbing("Dream ")
        assertEquals("Dream ", settings.preferredDubbing.value)
        settings.setPreferredDubbing("Dream Cast ")
        val restored = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertEquals("Dream Cast", restored.preferredDubbing.value)
    }

    @Test
    fun dubbingRouteRoundTripsReservedCharactersExactlyOnce() {
        val dubbing = "Studio A+B / 100% & Субтитры"
        val route = Screen.Player.createRoute(111, 1, dubbing)
        assertEquals(dubbing, Uri.parse("https://app/" + route).getQueryParameter("dubbing"))
    }

    private fun details(id: Long = 111): DetailsViewModel =
        DetailsViewModel(
                id,
                GetAnimeDetailsUseCase(repository),
                GetEpisodesUseCase(repository),
                ResolveStreamUseCase(repository),
                favorites,
                history,
                settings,
            )
            .also { store.put("details", it) }

    @Test
    fun historyDubbingOverridesGlobalAndExplicitAllSurvivesRecreation() = runTest {
        settings.setPreferredDubbing("Different studio")
        history.recordWatch(
            WatchHistoryItem(
                111,
                animeTitle = "Title",
                episodeId = "111_1",
                episodeNumber = 1,
                dubbing = "Studio A",
                playbackPositionMs = 30000,
                durationMs = 120000,
            )
        )
        val vm = details()
        vm.uiState.first { !it.isLoading }
        assertEquals("Studio A", vm.uiState.value.selectedDubbing)
        assertTrue(vm.uiState.value.watchedEpisodeNumbers.isEmpty())
        vm.selectDubbing(null)
        val recreated = details()
        recreated.uiState.first { !it.isLoading }
        assertNull(recreated.uiState.value.selectedDubbing)
        assertFalse(recreated.uiState.value.preferredDubbingNotFound)
    }

    @Test
    fun savedDubbingUsesProviderAliasesButDoesNotLeakToOtherSeasons() = runTest {
        settings.setAnimeDubbing(
            Anime(111, yummyId = 111, shikimoriId = 20, title = "Title"),
            "Studio A",
        )
        val restored = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertEquals("Studio A", restored.animeDubbing(Anime(-20, title = "Same"))!!.dubbing)
        assertNull(restored.animeDubbing(Anime(2, title = "Next season")))
        assertNull(restored.animeDubbing(Anime(20, title = "Unrelated catalog ID")))
    }

    @Test
    fun unavailableSavedVoiceShowsWarningInsteadOfSilentlySelectingAnother() = runTest {
        settings.setPreferredDubbing("Studio A")
        settings.setAnimeDubbing(Anime(111, title = "Title"), "Studio B")
        val vm = details()
        vm.uiState.first { !it.isLoading }
        assertNull(vm.uiState.value.selectedDubbing)
        assertTrue(vm.uiState.value.preferredDubbingNotFound)
        assertEquals("Studio B", vm.uiState.value.preferredDubbingName)
    }

    @Test
    fun playingEpisodeUpdatesVoiceSelectionOnExistingDetailsScreen() = runTest {
        val details = details()
        details.uiState.first { !it.isLoading }
        assertNull(details.uiState.value.selectedDubbing)
        val player =
            PlayerViewModel(
                111,
                1,
                "Studio A",
                GetAnimeDetailsUseCase(repository),
                ResolveStreamUseCase(repository),
                history,
                settings,
            )
        store.put("player", player)
        player.uiState.first { !it.isLoading }
        player.saveHistoryProgress(30000, 120000, true)
        details.uiState.first { it.selectedDubbing == "Studio A" }
        assertTrue(details.uiState.value.watchedEpisodeNumbers.isEmpty())
        player.saveHistoryProgress(120000, 120000, false)
        details.uiState.first { it.watchedEpisodeNumbers.contains(1) }
        assertEquals(
            "Studio A",
            SettingsRepository(ApplicationProvider.getApplicationContext())
                .animeDubbing(Anime(111, title = "Title"))!!
                .dubbing,
        )
    }
}

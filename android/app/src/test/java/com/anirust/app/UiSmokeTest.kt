package com.anirust.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anirust.app.data.local.AnirustDatabase
import com.anirust.app.data.remote.resolver.KodikResolver
import com.anirust.app.data.remote.yummy.*
import com.anirust.app.data.remote.yummy.YummyVideoData
import com.anirust.app.data.remote.yummy.YummyVideoItem
import com.anirust.app.data.remote.yummy.YummyVideosResponse
import com.anirust.app.data.repository.AnimeRepository
import com.anirust.app.data.repository.FavoritesRepository
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.data.repository.ShikimoriAccountRepository
import com.anirust.app.data.repository.UiMessages
import com.anirust.app.data.repository.WatchHistoryRepository
import com.anirust.app.di.AppContainer
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.FavoriteStatus
import com.anirust.app.domain.model.WatchHistoryItem
import com.anirust.app.domain.usecase.FavoritesUseCase
import com.anirust.app.domain.usecase.GetAnimeDetailsUseCase
import com.anirust.app.domain.usecase.GetEpisodesUseCase
import com.anirust.app.domain.usecase.ResolveStreamUseCase
import com.anirust.app.domain.usecase.SearchAnimeUseCase
import com.anirust.app.domain.usecase.WatchHistoryUseCase
import com.anirust.app.ui.account.*
import com.anirust.app.ui.components.AppSnackbarHost
import com.anirust.app.ui.details.DetailsScreen
import com.anirust.app.ui.details.DetailsViewModel
import com.anirust.app.ui.navigation.AnirustAppRoot
import com.anirust.app.ui.player.PlayerScreen
import com.anirust.app.ui.player.PlayerViewModel
import com.anirust.app.ui.search.SearchScreen
import com.anirust.app.ui.search.SearchViewModel
import com.anirust.app.ui.theme.AnirustTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private val databases = mutableListOf<AnirustDatabase>()
    private val accountScopes = mutableListOf<CoroutineScope>()

    @After
    fun cleanup() {
        compose.runOnIdle { store.clear() }
        accountScopes.forEach { it.cancel() }
        databases.forEach { it.close() }
    }

    @Test
    fun mainTabsRenderAndSettingsPreferenceIsInteractive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = testContainer(context)
        compose.setContent { AnirustTheme { AnirustAppRoot(container) } }
        waitForText("Хороший день\nдля нового аниме")
        capture("home")
        compose.onNodeWithText("Хороший день\nдля нового аниме").assertIsDisplayed()
        compose.onNodeWithText("Настройки", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Предпочитаемая озвучка").assertIsDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Установить mpvEx"))
        compose.onNodeWithText("Установить mpvEx").performScrollTo().assertIsDisplayed()
        assertEquals(false, container.settingsRepository.useExternalPlayer.value)
        assertEquals(
            SettingsRepository.PACKAGE_MPVEX,
            container.settingsRepository.externalPlayerPackage.value,
        )
        compose.onNodeWithText("Рекомендуем · сохраняет прогресс").assertIsDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("ТО Дубляжная"))
        compose.onNodeWithText("ТО Дубляжная").performScrollTo().performClick()
        assertEquals("ТО Дубляжная", container.settingsRepository.preferredDubbing.value)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("30 мин"))
        compose.onNodeWithText("30 мин").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(30, container.settingsRepository.syncIntervalMinutes.value)
        }
        capture("settings")
        compose.onNodeWithText("Списки", useUnmergedTree = true).performClick()
        waitForText("В избранном пока пусто")
        compose.onNodeWithText("В избранном пока пусто").assertIsDisplayed()
        capture("favorites-empty")
        compose.onNodeWithText("История", useUnmergedTree = true).performClick()
        waitForText("История просмотров пуста")
        compose.onNodeWithText("История просмотров пуста").assertIsDisplayed()
        capture("history-empty")
        compose.onNodeWithText("Поиск", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Введите название аниме").assertIsDisplayed()
        capture("search-empty")
    }

    @Test
    fun searchResultsOpenTheSelectedCatalogId() {
        lateinit var vm: SearchViewModel
        var opened: Long? = null
        compose.runOnUiThread {
            vm = SearchViewModel {
                Result.success(
                    listOf(
                        Anime(111, title = "Наруто", originalTitle = "Naruto", score = 8.7f),
                        Anime(119, title = "Наруто: Ураганные хроники", score = 8.9f),
                    )
                )
            }
            store.put("search", vm)
        }
        compose.setContent { AnirustTheme { SearchScreen(vm, { opened = it }) } }
        compose.onNode(hasSetTextAction()).performTextInput("Naruto")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000) { vm.uiState.value.results.isNotEmpty() }
        compose.onNodeWithText("Наруто", useUnmergedTree = true).assertIsDisplayed()
        capture("search")
        compose.onNodeWithText("Наруто", useUnmergedTree = true).performClick()
        assertEquals(111L, opened)
    }

    @Test
    fun detailsCanBookmarkAndOpenTheChosenEpisode() {
        val vm = detailsViewModel("Studio A")
        var opened: Triple<Long, Int, String?>? = null
        compose.setContent {
            AnirustTheme {
                DetailsScreen(vm, {}, { id, number, dub -> opened = Triple(id, number, dub) })
            }
        }
        compose.waitUntil(5000) { !vm.uiState.value.isLoading }
        compose.onNodeWithContentDescription("Добавить в список").performClick()
        compose.onNodeWithText("В планах").performClick()
        compose.waitUntil(5000) { vm.uiState.value.favoriteStatus == FavoriteStatus.PLAN_TO_WATCH }
        capture("details")
        compose.onNodeWithText("Серия 1").performScrollTo().performClick()
        assertEquals(Triple(111L, 1, "Studio A"), opened)
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-xhdpi")
    fun tabletUsesRailAndDarkTheme() {
        val container = testContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { AnirustTheme(darkTheme = true) { AnirustAppRoot(container) } }
        waitForText("Хороший день\nдля нового аниме")
        compose.onNodeWithTag("navigation_rail").assertIsDisplayed()
        compose.onNodeWithTag("navigation_bar").assertDoesNotExist()
        capture("tablet-home-dark")
        compose.onNodeWithText("Поиск", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Введите название аниме").assertIsDisplayed()
        capture("tablet-search-dark")
    }

    @Test
    fun populatedListsAndHistoryDeletionAreInteractive() {
        val container = testContainer(ApplicationProvider.getApplicationContext())
        runBlocking {
            container.favoritesUseCase.addFavorite(
                Anime(111, title = "Наруто", score = 8.7f, episodesCount = 220),
                FavoriteStatus.WATCHING,
            )
            container.favoritesUseCase.addFavorite(
                Anime(119, title = "Унесённые призраками", score = 9.1f),
                FavoriteStatus.PLAN_TO_WATCH,
            )
            container.watchHistoryUseCase.recordWatch(
                WatchHistoryItem(
                    animeId = 111,
                    animeTitle = "Наруто",
                    episodeId = "111:4",
                    episodeNumber = 4,
                    dubbing = "Studio A",
                    playbackPositionMs = 600_000,
                    durationMs = 1_440_000,
                )
            )
        }
        compose.setContent { AnirustTheme { AnirustAppRoot(container) } }
        waitForText("Продолжить просмотр")
        capture("home-populated")
        compose.onNodeWithTag("navigation_bar").assertIsDisplayed()
        compose.onNodeWithText("Списки", useUnmergedTree = true).performClick()
        waitForText("Наруто")
        capture("favorites")
        compose.onNodeWithText("В планах").performClick()
        waitForText("Унесённые призраками")
        compose.onNodeWithText("Наруто").assertDoesNotExist()
        compose.onNodeWithText("История", useUnmergedTree = true).performClick()
        waitForText("Серия 4")
        capture("history")
        compose.onNodeWithContentDescription("Действия с записью").performClick()
        compose.onNodeWithText("Удалить из истории").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Серия 4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Действия с записью").performClick()
        compose.onNodeWithText("Удалить из истории").performClick()
        compose.onNodeWithText("Удалить", substring = false).performClick()
        waitForText("История просмотров пуста")
    }

    @Test
    fun searchErrorCanBeRetriedAtLargeFontScale() {
        lateinit var vm: SearchViewModel
        var attempts = 0
        compose.runOnUiThread {
            vm = SearchViewModel {
                attempts++
                if (attempts == 1)
                    Result.failure(IllegalStateException("Проверь подключение к интернету"))
                else Result.success(listOf(Anime(111, title = "Наруто")))
            }
            store.put("retry", vm)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                AnirustTheme(darkTheme = true) { SearchScreen(vm, {}) }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Naruto")
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForText("Повторить")
        compose.onNodeWithText("Повторить").performScrollTo().assertIsDisplayed()
        capture("search-error-large-text")
        compose.onNodeWithText("Повторить").performClick()
        waitForText("Наруто")
        compose.onNodeWithText("Наруто").assertIsDisplayed()
    }

    @Test
    fun playerFailureOffersRetryAndBack() {
        val container = testContainer(ApplicationProvider.getApplicationContext())
        lateinit var vm: PlayerViewModel
        var wentBack = false
        compose.runOnUiThread {
            vm =
                PlayerViewModel(
                    111,
                    1,
                    null,
                    container.getAnimeDetailsUseCase,
                    container.resolveStreamUseCase,
                    container.watchHistoryUseCase,
                    container.settingsRepository,
                )
            store.put("player", vm)
        }
        compose.setContent {
            AnirustTheme(darkTheme = true) { PlayerScreen(vm, { wentBack = true }) }
        }
        waitForText("Повторить")
        capture("player-error")
        compose.onNodeWithContentDescription("Назад").performClick()
        assertEquals(true, wentBack)
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-xhdpi")
    fun tabletDetailsAndDubbingSheetOpenChosenVoice() {
        val vm = detailsViewModel()
        var opened: Triple<Long, Int, String?>? = null
        compose.setContent {
            AnirustTheme(darkTheme = true) {
                DetailsScreen(vm, {}, { id, episode, dub -> opened = Triple(id, episode, dub) })
            }
        }
        waitForText("Серия 1")
        compose.onNodeWithText("Об истории").assertIsDisplayed()
        compose.onNodeWithText("Серия 1").assertIsDisplayed()
        capture("tablet-details-dark")
        compose.onNodeWithText("Серия 1").performClick()
        waitForText("Как будем смотреть?")
        capture("tablet-dubbing-sheet")
        compose.onAllNodesWithText("Studio A").onLast().performClick()
        assertEquals(Triple(111L, 1, "Studio A"), opened)
    }

    @Test
    fun phoneDubbingSheetSupportsLongNames() {
        val vm = detailsViewModel()
        compose.setContent { AnirustTheme { DetailsScreen(vm, {}, { _, _, _ -> }) } }
        waitForText("Серия 1")
        compose.onNodeWithText("Серия 1").performScrollTo().performClick()
        waitForText("Как будем смотреть?")
        compose.onNodeWithContentDescription("Во внешнем плеере: Studio A").assertIsDisplayed()
        capture("phone-dubbing-sheet")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-xhdpi")
    fun landscapeNavigationCanReachSettings() {
        val container = testContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { AnirustTheme { AnirustAppRoot(container) } }
        waitForText("Хороший день\nдля нового аниме")
        compose.onNodeWithText("Настройки", useUnmergedTree = true).performScrollTo().performClick()
        waitForText("Предпочитаемая озвучка")
        compose.onNodeWithText("Предпочитаемая озвучка").performScrollTo().assertIsDisplayed()
        capture("landscape-settings")
    }

    private fun detailsViewModel(
        preferredDubbing: String = "",
        initialHistory: List<WatchHistoryItem> = emptyList(),
    ): DetailsViewModel {
        val container = testContainer(ApplicationProvider.getApplicationContext())
        runBlocking { initialHistory.forEach { container.watchHistoryUseCase.recordWatch(it) } }
        container.settingsRepository.setPreferredDubbing(preferredDubbing)
        container.settingsRepository.setUseExternalPlayer(false)
        val yummy =
            FakeYummyApi().apply {
                details = {
                    YummyDetailResponse(
                        YummyAnimeDetail(
                            animeId = it,
                            title = "Наруто: Ураганные хроники",
                            otherTitles = listOf("Naruto: Shippuuden"),
                            rating = YummyRating(8.9),
                            remoteIds = YummyRemoteIds(shikimoriId = if (it == 111L) 20 else it),
                            genres = listOf(YummyGenre("Приключения"), YummyGenre("Фэнтези")),
                            description =
                                "Путь ниндзя продолжается. Наруто возвращается в родную деревню после долгих тренировок. " +
                                    "Впереди — новые встречи, непростые решения и борьба за тех, кто ему дорог.",
                        )
                    )
                }
                videos = {
                    YummyVideosResponse(
                        (1..8).flatMap { number ->
                            listOf("Studio A", "Dream Cast — многоголосая озвучка").map { voice ->
                                YummyVideoItem(
                                    number = number.toString(),
                                    iframeUrl = "https://cdn.test/" + number + ".mp4",
                                    data = YummyVideoData(dubbing = voice),
                                )
                            }
                        }
                    )
                }
            }
        val repo = AnimeRepository(yummy, FakeShikimoriApi(), KodikResolver(OkHttpClient()))
        lateinit var vm: DetailsViewModel
        compose.runOnUiThread {
            vm =
                DetailsViewModel(
                    111,
                    GetAnimeDetailsUseCase(repo),
                    GetEpisodesUseCase(repo),
                    ResolveStreamUseCase(repo),
                    container.favoritesUseCase,
                    container.watchHistoryUseCase,
                    container.settingsRepository,
                )
            store.put("details", vm)
        }
        return vm
    }

    @Test
    fun explicitExternalActionKeepsItsIntentInDubbingSheet() {
        val vm = detailsViewModel()
        compose.setContent { AnirustTheme { DetailsScreen(vm, {}, { _, _, _ -> }) } }
        waitForText("Серия 1")
        compose.onNodeWithContentDescription("Действия с серией 1").performScrollTo().performClick()
        compose.onNodeWithText("Внешний плеер").performClick()
        waitForText("Как будем смотреть?")
        compose.onNodeWithContentDescription("Во встроенном плеере: Studio A").assertIsDisplayed()
        compose.onNodeWithContentDescription("Во внешнем плеере: Studio A").assertDoesNotExist()
    }

    @Test
    fun shikimoriListsCanBeEditedAndOpenedForPlayback() {
        val notices = UiMessages()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val api = FakeShikimoriAccountApi()
        val repo =
            ShikimoriAccountRepository(
                api,
                MemoryShikimoriStore(shikimoriTestSession()),
                notices,
                appScope,
                Dispatchers.Main.immediate,
                pause = {},
            )
        lateinit var vm: ShikimoriViewModel
        var opened: Long? = null
        compose.runOnUiThread {
            vm = ShikimoriViewModel(repo)
            store.put("account", vm)
        }
        compose.setContent {
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                notices.events.collectLatest {
                    snackbar.showSnackbar(it.text, withDismissAction = true)
                }
            }
            AnirustTheme {
                Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
                    Box(Modifier.padding(padding)) {
                        ShikimoriListsScreen(vm, {}, {}, { opened = it })
                    }
                }
            }
        }
        waitForText("Наруто")
        capture("shikimori-lists")
        compose.onNodeWithText("Выбрать серию").performClick()
        assertEquals(-20L, opened)
        compose.onNodeWithContentDescription("Изменить список: Наруто").performClick()
        compose.onNodeWithText("Отложено").performClick()
        capture("shikimori-editor")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(5000) { repo.state.value.rates.single().status == "on_hold" }
        compose.waitForIdle()
        compose.onNodeWithText("Shikimori: «Наруто» — Отложено").assertIsDisplayed()
        compose.onNodeWithText("Наруто").assertDoesNotExist()
        compose.onNodeWithText("Все · 1").performClick()
        compose.onNodeWithContentDescription("Изменить список: Наруто").performClick()
        compose.onNodeWithText("Удалить из Shikimori").performScrollTo().performClick()
        compose.onNodeWithText("Удалить", substring = false).performClick()
        compose.waitUntil(5000) { repo.state.value.rates.isEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("Shikimori: «Наруто» удалено из списка").assertIsDisplayed()
        appScope.cancel()
    }

    @Test
    fun accountLoginOnlyRequestsCodeAndDisablesEmptySubmission() {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val repo =
            ShikimoriAccountRepository(
                FakeShikimoriAccountApi(),
                MemoryShikimoriStore(),
                UiMessages(),
                appScope,
                Dispatchers.Main.immediate,
                pause = {},
            )
        lateinit var vm: ShikimoriViewModel
        compose.runOnUiThread {
            vm = ShikimoriViewModel(repo)
            store.put("account-setup", vm)
        }
        compose.setContent { AnirustTheme { AccountScreen(vm, {}, {}) } }
        waitForText("Подключи свои списки")
        capture("shikimori-setup")
        compose.onNodeWithText("Подключить Shikimori").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Войти через Shikimori").assertIsEnabled()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Client ID").assertDoesNotExist()
        compose.onNodeWithText("Client Secret").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("one-time-test-code")
        compose.onNodeWithText("Подключить Shikimori").assertIsEnabled()
        appScope.cancel()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun shikimoriEditorValidatesInputsOnShortLandscapeScreen() {
        val rate = FakeShikimoriAccountApi().serverRate
        var saved: Triple<String, Int, Int>? = null
        compose.setContent {
            AnirustTheme {
                RateEditorDialog(
                    rate.anime.toAnime(),
                    rate,
                    false,
                    {},
                    { status, score, episodes -> saved = Triple(status, score, episodes) },
                )
            }
        }
        compose.onNodeWithText("Моя оценка · 0–10").performScrollTo().performTextReplacement("11")
        compose.onNodeWithText("Сохранить").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Моя оценка · 0–10").performTextReplacement("10")
        compose.onNodeWithText("Просмотрено серий").performScrollTo().performTextReplacement("221")
        compose.onNodeWithText("Сохранить").assertIsNotEnabled()
        compose.onNodeWithText("Просмотрено серий").performTextReplacement("220")
        compose.onNodeWithText("Сохранить").performClick()
        assertEquals(Triple("watching", 10, 220), saved)
    }

    @Test
    fun localListChangesShowSnackbarAfterSaving() {
        val container = testContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { AnirustTheme { AnirustAppRoot(container) } }
        waitForText("Хороший день\nдля нового аниме")
        runBlocking {
            container.favoritesUseCase.addFavorite(
                Anime(111, title = "Наруто"),
                FavoriteStatus.WATCHING,
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText("«Наруто» добавлено: Смотрю").assertIsDisplayed()
        capture("snackbar-saved")
        runBlocking { container.favoritesUseCase.updateStatus(111, FavoriteStatus.COMPLETED) }
        compose.waitForIdle()
        compose.onNodeWithText("Локальный список изменён: Просмотрено").assertIsDisplayed()
        runBlocking { container.favoritesUseCase.removeFavorite(111) }
        compose.waitForIdle()
        compose.onNodeWithText("Аниме удалено из локального списка").assertIsDisplayed()
        compose.onNodeWithText("Отменить").performClick()
        waitForText("Закладка восстановлена")
        assertEquals(
            FavoriteStatus.COMPLETED,
            runBlocking { container.favoritesUseCase.getFavorite(111).first()!!.status },
        )
    }

    @Test
    fun firstLaunchOnboardingCanBeSkippedAndReopenedFromSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = testContainer(context, onboardingCompleted = false)
        compose.setContent { AnirustTheme { AnirustAppRoot(container) } }
        waitForText("Любимые истории\nвсегда рядом")
        capture("onboarding-welcome")
        compose.onNodeWithText("Пропустить").performClick()
        waitForText("Хороший день\nдля нового аниме")
        assertEquals(true, SettingsRepository(context).onboardingCompleted.value)
        compose.onNodeWithText("Настройки", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Как работает AniRust"))
        compose.onNodeWithText("Как работает AniRust").performClick()
        waitForText("Любимые истории\nвсегда рядом")
        compose.onNodeWithText("Дальше").performClick()
        waitForText("Рекомендуем · сохраняет прогресс")
        capture("onboarding-players")
        compose.onNodeWithText("Дальше").performClick()
        compose.onNodeWithText("Начать без аккаунта").performClick()
        compose.onNodeWithTag("settings_list").performScrollToIndex(0)
        waitForText("Пусть всё будет по-твоему")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-xhdpi")
    fun onboardingOnShortScreenOpensShikimoriAndBackReturnsHome() {
        val local =
            testContainer(ApplicationProvider.getApplicationContext(), onboardingCompleted = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        accountScopes += scope
        val repo =
            ShikimoriAccountRepository(
                FakeShikimoriAccountApi(),
                MemoryShikimoriStore(),
                local.messages,
                scope,
                Dispatchers.Main.immediate,
                pause = {},
            )
        val container =
            object : AppContainer by local {
                override val shikimoriAccountRepository = repo
            }
        compose.setContent { AnirustTheme(darkTheme = true) { AnirustAppRoot(container) } }
        waitForText("Любимые истории\nвсегда рядом")
        compose.onNodeWithText("Дальше").performClick()
        compose.onNodeWithText("Дальше").performClick()
        compose.onNodeWithText("Войти в Shikimori").assertIsDisplayed()
        capture("onboarding-shikimori-landscape")
        compose.onNodeWithText("Войти в Shikimori").performClick()
        waitForText("Подключи свои списки")
        compose.onNodeWithContentDescription("Назад").performClick()
        waitForText("Хороший день\nдля нового аниме")
        assertEquals(true, local.settingsRepository.onboardingCompleted.value)
    }

    @Test
    fun missingAndFailedCoversAreDistinctAndFailedCoversCanBeRetried() {
        val cover = File(compose.activity.cacheDir, "retry-cover.png")
        cover.delete()
        val source = androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.setContent {
            AnirustTheme {
                com.anirust.app.ui.components.AnimePoster(
                    source.value,
                    Modifier.fillMaxSize(),
                    "Тестовая обложка",
                )
            }
        }
        compose.onNodeWithContentDescription("Обложка отсутствует").assertIsDisplayed()
        compose.onNodeWithTag("shimmer").assertDoesNotExist()
        compose.runOnUiThread { source.value = cover.absolutePath }
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithContentDescription("Повторить загрузку обложки")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("shimmer").assertDoesNotExist()
        val bitmap = Bitmap.createBitmap(80, 116, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(98, 83, 154))
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.onNodeWithContentDescription("Повторить загрузку обложки").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("shimmer").fetchSemanticsNodes().isEmpty() &&
                compose
                    .onAllNodesWithContentDescription("Повторить загрузку обложки")
                    .fetchSemanticsNodes()
                    .isEmpty()
        }
        compose.runOnUiThread { source.value = null }
        compose.onNodeWithContentDescription("Обложка отсутствует").assertIsDisplayed()
        cover.delete()
    }

    @Test
    fun completionQuestionRemembersPercentageForThisTitle() {
        val container = testContainer(ApplicationProvider.getApplicationContext())
        container.settingsRepository.registerAnime(Anime(111, shikimoriId = 20, title = "Наруто"))
        val watched =
            WatchHistoryItem(
                animeId = 111,
                animeTitle = "Наруто",
                episodeId = "111_1",
                episodeNumber = 1,
                playbackPositionMs = 92_000,
                durationMs = 100_000,
            )
        runBlocking { container.watchHistoryUseCase.recordWatch(watched) }
        compose.setContent { AnirustTheme { AnirustAppRoot(container) } }
        runBlocking { container.watchHistoryUseCase.finishPlayback(watched.historyId) }
        waitForText("Серия уже просмотрена?")
        capture("completion-confirmation")
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Да, просмотрена").performClick()
        compose.waitUntil(5000) { container.watchHistoryUseCase.completionPrompt.value == null }
        assertEquals(92, container.settingsRepository.completionThresholds.value[-20L])
        assertEquals(
            true,
            runBlocking {
                container.watchHistoryUseCase.getAllHistory().first().single().isCompleted
            },
        )
    }

    @Test
    fun primaryPlaybackResumesWithoutScrollingAndManualCompletionOffersNextEpisode() {
        val history =
            WatchHistoryItem(
                animeId = 111,
                animeTitle = "Наруто",
                episodeId = "111_4",
                episodeNumber = 4,
                dubbing = "Studio A",
                playbackPositionMs = 754_000,
                durationMs = 1_440_000,
            )
        val vm = detailsViewModel("Studio A", listOf(history))
        var played: Triple<Long, Int, String?>? = null
        compose.setContent {
            AnirustTheme {
                DetailsScreen(vm, {}, { id, episode, voice -> played = Triple(id, episode, voice) })
            }
        }
        waitForText("Продолжить · серия 4 · 12:34")
        compose.onNodeWithText("Продолжить · серия 4 · 12:34").assertIsDisplayed().performClick()
        assertEquals(Triple(111L, 4, "Studio A"), played)
        compose.onNodeWithTag("episode_list").performScrollToNode(hasText("Серия 4"))
        compose.onNodeWithContentDescription("Действия с серией 4").performClick()
        compose.onNodeWithText("Отметить просмотренной").performClick()
        waitForText("Смотреть серию 5")
        compose.onNodeWithText("Смотреть серию 5").assertIsDisplayed()
        capture("details-next-episode")
    }

    @Test
    fun searchKeepsResultCardsVisibleWhileTypingAndAwaitingNetwork() {
        lateinit var vm: SearchViewModel
        val pending = kotlinx.coroutines.CompletableDeferred<Result<List<Anime>>>()
        compose.runOnUiThread {
            vm = SearchViewModel { query ->
                if (query == "old") Result.success(listOf(Anime(1, title = "Старый результат")))
                else pending.await()
            }
            store.put("search-stable", vm)
            vm.searchImmediately("old")
        }
        compose.setContent { AnirustTheme { SearchScreen(vm, {}) } }
        waitForText("Старый результат")
        compose.onNode(hasSetTextAction()).performTextReplacement("new")
        compose.onNodeWithText("Старый результат").assertIsDisplayed()
        compose.onNodeWithText("Обновляем результаты…").assertIsDisplayed()
        capture("search-updating")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnUiThread {
            pending.complete(Result.success(listOf(Anime(2, title = "Новый результат"))))
        }
        waitForText("Новый результат")
        compose.onNodeWithText("Старый результат").assertDoesNotExist()
    }

    @Test
    fun longSeasonTitlesUseScrollablePickerAndSelectedPartIsVisible() {
        val second =
            "Очень длинное название аниме: продолжение приключений главного героя во втором сезоне"
        var selected = "1"
        val series =
            listOf(
                com.anirust.app.domain.model.SeriesEntry("1", "Текущая история", 0),
                com.anirust.app.domain.model.SeriesEntry("2", second, 1, year = 2007),
                com.anirust.app.domain.model.SeriesEntry("3", "Полнометражное завершение", 2),
            )
        compose.setContent {
            AnirustTheme { com.anirust.app.ui.details.SeriesPicker(series, "1") { selected = it } }
        }
        compose.onNodeWithText("Сезоны и части · 3").performClick()
        compose.onNodeWithText("Текущая часть").assertIsDisplayed()
        compose.onNodeWithText(second).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("series_poster_2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2 в порядке просмотра · 2007").assertIsDisplayed()
        capture("season-picker-long-titles")
        compose.onNodeWithText(second).performClick()
        assertEquals("2", selected)
        compose.onNodeWithText("Порядок просмотра из каталога").assertDoesNotExist()
    }

    private fun accountFixture(
        api: FakeShikimoriAccountApi = FakeShikimoriAccountApi()
    ): Pair<ShikimoriAccountRepository, ShikimoriViewModel> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        accountScopes += scope
        val repo =
            ShikimoriAccountRepository(
                api,
                MemoryShikimoriStore(shikimoriTestSession(listOf(api.serverRate))),
                UiMessages(),
                scope,
                Dispatchers.Main.immediate,
                pause = {},
            )
        lateinit var vm: ShikimoriViewModel
        compose.runOnUiThread {
            vm = ShikimoriViewModel(repo)
            store.put("account-fixture", vm)
        }
        return repo to vm
    }

    @Test
    fun shikimoriEpisodeChecksRefreshWithoutLocalHistory() {
        val api = FakeShikimoriAccountApi()
        val (repo, account) = accountFixture(api)
        val vm = detailsViewModel("Studio A")
        compose.setContent {
            AnirustTheme {
                DetailsScreen(
                    vm,
                    {},
                    { _, _, _ -> },
                    accountViewModel = account,
                    onOpenAccount = {},
                )
            }
        }
        compose.waitUntil(5000) { !vm.uiState.value.isLoading }
        compose
            .onNodeWithTag("episode_list")
            .performScrollToNode(hasText("Показать просмотренные · 3"))
        compose.onNodeWithText("Показать просмотренные · 3").assertIsDisplayed()
        compose.onNodeWithText("Серия 2").assertDoesNotExist()
        compose.onNodeWithText("Показать просмотренные · 3").performClick()
        compose.onNodeWithTag("episode_list").performScrollToNode(hasText("Серия 2"))
        compose
            .onNodeWithText("Серия 2")
            .performScrollTo()
            .assert(hasContentDescription("Просмотрено"))
        compose.onNodeWithTag("episode_list").performScrollToNode(hasText("Серия 4"))
        compose.onNodeWithText("Серия 4").assert(!hasContentDescription("Просмотрено"))
        api.serverRate = api.serverRate.copy(episodes = 1)
        compose.runOnUiThread { account.sync() }
        compose.waitUntil(5000) { repo.state.value.rates.single().episodes == 1 }
        compose.onNodeWithTag("episode_list").performScrollToNode(hasText("Серия 2"))
        compose.onNodeWithText("Серия 2").assert(!hasContentDescription("Просмотрено"))
        compose.onNodeWithTag("episode_list").performScrollToNode(hasText("Серия 1"))
        compose.onNodeWithText("Серия 1").assert(hasContentDescription("Просмотрено"))
        compose
            .onNodeWithTag("episode_list")
            .performScrollToNode(hasText("Скрыть просмотренные · 1"))
        compose.onNodeWithText("Скрыть просмотренные · 1").performClick()
        api.serverRate = api.serverRate.copy(episodes = 8)
        compose.runOnUiThread { account.sync() }
        compose.waitUntil(5000) { repo.state.value.rates.single().episodes == 8 }
        compose
            .onNodeWithTag("episode_list")
            .performScrollToNode(hasText("Нет непросмотренных серий"))
        compose.onNodeWithText("Нет непросмотренных серий").assertIsDisplayed()
        compose
            .onNodeWithTag("episode_list")
            .performScrollToNode(hasText("Показать просмотренные · 8"))
        compose.onNodeWithText("Показать просмотренные · 8").performClick()
        compose.onNodeWithTag("episode_list").performScrollToNode(hasText("Серия 1"))
        compose.onNodeWithText("Серия 1").assert(hasContentDescription("Просмотрено"))
    }

    @Test
    fun completedEpisodesAreCollapsedAcrossDubbingsAndCanBeReopened() {
        val completed =
            WatchHistoryItem(
                animeId = 111,
                animeTitle = "Наруто",
                episodeId = "111_1",
                episodeNumber = 1,
                dubbing = "Dream Cast — многоголосая озвучка",
                playbackPositionMs = 120000,
                durationMs = 120000,
            )
        val vm =
            detailsViewModel(
                "Studio A",
                listOf(
                    completed,
                    completed.copy(
                        episodeId = "111_2",
                        episodeNumber = 2,
                        playbackPositionMs = 60000,
                    ),
                ),
            )
        var played: Int? = null
        compose.setContent {
            AnirustTheme { DetailsScreen(vm, {}, { _, episode, _ -> played = episode }) }
        }
        compose.waitUntil(5000) { !vm.uiState.value.isLoading }
        compose.runOnUiThread { vm.selectDubbing("Studio A") }
        compose.waitForIdle()
        compose
            .onNodeWithTag("episode_list")
            .performScrollToNode(hasText("Показать просмотренные · 1"))
        compose.onNodeWithText("Серия 1").assertDoesNotExist()
        compose.onNodeWithTag("episode_list").performScrollToNode(hasText("Серия 2"))
        compose.onNodeWithText("Серия 2").assertIsDisplayed()
        compose.onNodeWithText("Показать просмотренные · 1").performScrollTo().performClick()
        compose.onNodeWithText("Серия 1").performScrollTo().performClick()
        assertEquals(1, played)
        compose.onNodeWithText("Скрыть просмотренные · 1").performScrollTo().performClick()
        compose.onNodeWithText("Серия 1").assertDoesNotExist()
        capture("episodes-watched-collapsed")
        compose.runOnUiThread { vm.selectSeries("112") }
        compose.waitForIdle()
        compose.waitUntil(5000) {
            !vm.uiState.value.isLoading && vm.uiState.value.anime?.id == 112L
        }
        compose.onNodeWithTag("episode_list").performScrollToNode(hasText("Серия 1"))
        compose.onNodeWithText("Серия 1").assertIsDisplayed()
    }

    @Test
    fun primaryListsWarnBeforeReplacingLocalBookmarksAndPreserveHistory() {
        val local = testContainer(ApplicationProvider.getApplicationContext())
        val (repo, _) = accountFixture()
        val container =
            object : AppContainer by local {
                override val shikimoriAccountRepository = repo
            }
        runBlocking {
            local.favoritesUseCase.addFavorite(Anime(999, title = "Локальная закладка"))
            local.watchHistoryUseCase.recordWatch(
                WatchHistoryItem(
                    999,
                    animeTitle = "История",
                    episodeId = "999_1",
                    episodeNumber = 1,
                )
            )
        }
        compose.setContent { AnirustTheme { AnirustAppRoot(container) } }
        compose.onNodeWithText("Списки", useUnmergedTree = true).performClick()
        waitForText("Заменить локальные списки?")
        assertEquals(1, runBlocking { local.favoritesUseCase.getAllFavorites().first() }.size)
        capture("shikimori-replace-warning")
        compose.onNodeWithText("Заменить локальные", substring = false).performClick()
        compose.waitUntil(5000) {
            runBlocking { local.favoritesUseCase.getAllFavorites().first() }.isEmpty()
        }
        compose.onNodeWithText("Наруто").assertIsDisplayed()
        compose.onNodeWithText("Смотрю · 1").assertIsSelected()
        assertEquals(1, runBlocking { local.watchHistoryUseCase.getAllHistory().first() }.size)
        assertEquals(1, repo.state.value.rates.size)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun decliningListReplacementKeepsLocalBookmarks() {
        val local = testContainer(ApplicationProvider.getApplicationContext())
        val (repo, _) = accountFixture()
        val container =
            object : AppContainer by local {
                override val shikimoriAccountRepository = repo
            }
        runBlocking { local.favoritesUseCase.addFavorite(Anime(999, title = "Локальная закладка")) }
        compose.setContent { AnirustTheme { AnirustAppRoot(container) } }
        compose.onNodeWithText("Списки", useUnmergedTree = true).performClick()
        waitForText("Заменить локальные списки?")
        compose.onNodeWithText("Пока сохранить").performClick()
        compose.onNodeWithText("Наруто").assertIsDisplayed()
        assertEquals(1, runBlocking { local.favoritesUseCase.getAllFavorites().first() }.size)
    }

    private fun capture(name: String) {
        val target = File("build/reports/ui/" + name + ".png")
        target.parentFile!!.mkdirs()
        compose.runOnIdle {
            val activityView = compose.activity.window.decorView
            // Sheets/dialogs own a separate window; capture that window when it is visible.
            val view =
                WindowInspector.getGlobalWindowViews().lastOrNull {
                    it !== activityView && it.isShown && it.width > 0 && it.height > 0
                } ?: activityView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun testContainer(context: Context, onboardingCompleted: Boolean = true): AppContainer {
        val database =
            Room.inMemoryDatabaseBuilder(context, AnirustDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        databases += database
        return object : AppContainer {
            override val messages = com.anirust.app.data.repository.UiMessages()
            override val animeRepository =
                AnimeRepository(FakeYummyApi(), FakeShikimoriApi(), KodikResolver(OkHttpClient()))
            override val favoritesRepository = FavoritesRepository(database.favoritesDao())
            override val settingsRepository =
                SettingsRepository(context).apply { if (onboardingCompleted) completeOnboarding() }
            override val watchHistoryRepository =
                WatchHistoryRepository(database.watchHistoryDao(), settingsRepository)
            override val favoritesUseCase = FavoritesUseCase(favoritesRepository, messages)
            override val watchHistoryUseCase =
                WatchHistoryUseCase(watchHistoryRepository, settingsRepository)
            override val searchAnimeUseCase = SearchAnimeUseCase(animeRepository)
            override val getAnimeDetailsUseCase = GetAnimeDetailsUseCase(animeRepository)
            override val getEpisodesUseCase = GetEpisodesUseCase(animeRepository)
            override val resolveStreamUseCase = ResolveStreamUseCase(animeRepository)
        }
    }
}

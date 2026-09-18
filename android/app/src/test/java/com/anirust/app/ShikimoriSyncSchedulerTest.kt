package com.anirust.app

import com.anirust.app.data.local.ShikimoriOAuthConfig
import com.anirust.app.data.repository.*
import com.anirust.app.domain.model.Anime
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShikimoriSyncSchedulerTest {
    private fun TestScope.repository(api: FakeShikimoriAccountApi, signedIn: Boolean = true) =
        ShikimoriAccountRepository(
            api,
            MemoryShikimoriStore(
                if (signedIn) shikimoriTestSession().copy(lastSync = 1_000_000) else null
            ),
            UiMessages(),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            now = { 1_000_000 + testScheduler.currentTime },
            pause = {},
        )

    @Test
    fun syncsAtStartupAndEveryFifteenMinutesWithoutRefreshingOnActionsOrResume() = runTest {
        val api = FakeShikimoriAccountApi()
        val repo = repository(api)
        val scheduler =
            ShikimoriSyncScheduler(repo, MutableStateFlow(15)) {
                1_000_000 + testScheduler.currentTime
            }
        var job = backgroundScope.launch { scheduler.run() }
        runCurrent()
        assertEquals(1, api.seenHeaders.size)
        repo.saveRate(Anime(-20, title = "Naruto"), "watching", 8, 3)
        runCurrent()
        assertEquals(1, api.seenHeaders.size)
        advanceTimeBy(10 * 60_000)
        job.cancelAndJoin()
        job = backgroundScope.launch { scheduler.run() }
        runCurrent()
        assertEquals(1, api.seenHeaders.size)
        advanceTimeBy(5 * 60_000)
        runCurrent()
        assertEquals(2, api.seenHeaders.size)
        job.cancelAndJoin()
        advanceTimeBy(16 * 60_000)
        assertEquals(2, api.seenHeaders.size)
        backgroundScope.launch { scheduler.run() }
        runCurrent()
        assertEquals(3, api.seenHeaders.size)
    }

    @Test
    fun changedIntervalAppliesImmediatelyAndManualSyncResetsDeadline() = runTest {
        val api = FakeShikimoriAccountApi()
        val repo = repository(api)
        val interval = MutableStateFlow(15)
        val scheduler =
            ShikimoriSyncScheduler(repo, interval) { 1_000_000 + testScheduler.currentTime }
        backgroundScope.launch { scheduler.run() }
        runCurrent()
        advanceTimeBy(6 * 60_000)
        interval.value = 5
        runCurrent()
        assertEquals(2, api.seenHeaders.size)
        advanceTimeBy(4 * 60_000)
        repo.sync()
        assertEquals(3, api.seenHeaders.size)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(3, api.seenHeaders.size)
        advanceTimeBy(4 * 60_000)
        runCurrent()
        assertEquals(4, api.seenHeaders.size)
    }

    @Test
    fun failedSyncKeepsCacheAndWaitsBeforeRetrying() = runTest {
        val api = FakeShikimoriAccountApi().apply { readFailure = IOException("offline") }
        val repo = repository(api)
        val scheduler =
            ShikimoriSyncScheduler(repo, MutableStateFlow(15)) {
                1_000_000 + testScheduler.currentTime
            }
        backgroundScope.launch { scheduler.run() }
        runCurrent()
        assertEquals(1, api.seenHeaders.size)
        assertEquals("Наруто", repo.state.value.rates.single().anime.russian)
        advanceTimeBy(14 * 60_000)
        runCurrent()
        assertEquals(1, api.seenHeaders.size)
        api.readFailure = null
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, api.seenHeaders.size)
        assertNull(repo.state.value.error)
    }

    @Test
    fun loginDownloadsOnceAndLogoutStopsAutomaticRequests() = runTest {
        val api = FakeShikimoriAccountApi()
        val repo = repository(api, signedIn = false)
        val scheduler =
            ShikimoriSyncScheduler(repo, MutableStateFlow(15)) {
                1_000_000 + testScheduler.currentTime
            }
        backgroundScope.launch { scheduler.run() }
        runCurrent()
        assertTrue(api.seenHeaders.isEmpty())
        repo.signIn(ShikimoriOAuthConfig("client", "secret", "AniRust"), "code")
        runCurrent()
        assertEquals(1, api.seenHeaders.size)
        repo.signOut()
        advanceTimeBy(30 * 60_000)
        runCurrent()
        assertEquals(1, api.seenHeaders.size)
    }
}

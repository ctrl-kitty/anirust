package com.anirust.app

import com.anirust.app.data.local.*
import com.anirust.app.data.remote.shikimori.*
import com.anirust.app.data.repository.*
import com.anirust.app.domain.model.Anime
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class MemoryShikimoriStore(var value: ShikimoriSession? = null) : ShikimoriSessionStore {
    var failSave = false

    override fun read() = value

    override fun save(session: ShikimoriSession) {
        if (failSave) throw IOException("disk")
        value = session
    }

    override fun clear() {
        value = null
    }
}

class FakeShikimoriAccountApi : ShikimoriAccountApi {
    var tokenCalls = 0
    var tokenFailure: Exception? = null
    var requestedFields: Map<String, String> = emptyMap()
    var seenHeaders = mutableListOf<String>()
    var serverRate =
        ShikimoriRate(
            101,
            "watching",
            7,
            3,
            ShikimoriListAnime(20, "Naruto", "Наруто", episodes = 220),
        )
    var pages: suspend (Int) -> List<ShikimoriRate> = {
        if (it == 1) listOf(serverRate) else emptyList()
    }
    var writeFailure: Exception? = null
    var readFailure: Exception? = null
    var graphErrors: List<ShikimoriError>? = null
    var deleted: Long? = null

    override suspend fun token(appName: String, fields: Map<String, String>): OAuthTokens {
        tokenCalls++
        tokenFailure?.let { throw it }
        return OAuthTokens("new-access", "new-refresh", 3600)
    }

    override suspend fun whoami(authorization: String, appName: String) = ShikimoriUser(7, "Tester")

    override suspend fun rates(
        authorization: String,
        appName: String,
        request: ShikimoriGraphQlRequest,
    ): AccountGraphResponse {
        seenHeaders += authorization
        readFailure?.let { throw it }
        return AccountGraphResponse(
            AccountGraphData(pages(request.variables["page"] as Int)),
            graphErrors,
        )
    }

    override suspend fun createRate(
        authorization: String,
        appName: String,
        request: RateRequest,
    ): RateResponse {
        requestedFields = request.userRate
        return updateRate(authorization, appName, 101, request)
    }

    override suspend fun updateRate(
        authorization: String,
        appName: String,
        rateId: Long,
        request: RateRequest,
    ): RateResponse {
        writeFailure?.let { throw it }
        requestedFields = request.userRate
        serverRate =
            serverRate.copy(
                status = request.userRate["status"] ?: serverRate.status,
                score = request.userRate["score"]?.toInt() ?: serverRate.score,
                episodes = request.userRate["episodes"]?.toInt() ?: serverRate.episodes,
            )
        return RateResponse(rateId, serverRate.status, serverRate.score, serverRate.episodes)
    }

    override suspend fun deleteRate(authorization: String, appName: String, rateId: Long) {
        writeFailure?.let { throw it }
        deleted = rateId
    }
}

fun shikimoriTestSession(
    rates: List<ShikimoriRate> = listOf(FakeShikimoriAccountApi().serverRate)
) =
    ShikimoriSession(
        ShikimoriOAuthConfig("test-client", "test-secret", "AniRust"),
        "old-access",
        "old-refresh",
        Long.MAX_VALUE,
        ShikimoriUser(7, "Tester"),
        rates,
        System.currentTimeMillis(),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class ShikimoriAccountRepositoryTest {
    private fun TestScope.repository(
        api: FakeShikimoriAccountApi,
        store: MemoryShikimoriStore,
        messages: UiMessages = UiMessages(),
    ) =
        ShikimoriAccountRepository(
            api,
            store,
            messages,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            now = { 1_000_000L },
            pause = {},
        )

    @Test
    fun readsEveryPageAndReplacesRemovedEntries() = runTest {
        val api = FakeShikimoriAccountApi()
        api.pages = { page ->
            if (page == 1)
                (1L..50L).map {
                    api.serverRate.copy(id = it, anime = api.serverRate.anime.copy(id = it))
                }
            else if (page == 2)
                listOf(api.serverRate.copy(id = 51, anime = api.serverRate.anime.copy(id = 51)))
            else emptyList()
        }
        val store = MemoryShikimoriStore(shikimoriTestSession())
        val repo = repository(api, store)
        assertTrue(repo.sync())
        assertEquals(51, repo.state.value.rates.size)
        assertEquals(51, store.value!!.rates.size)
        assertTrue(api.seenHeaders.all { it == "Bearer old-access" })
    }

    @Test
    fun failedSecondPageKeepsLastCompleteCache() = runTest {
        val api = FakeShikimoriAccountApi()
        api.pages = { page ->
            if (page == 1) (1L..50L).map { api.serverRate.copy(id = it) }
            else throw IOException("offline")
        }
        val original = shikimoriTestSession()
        val store = MemoryShikimoriStore(original)
        val repo = repository(api, store)
        assertFalse(repo.sync())
        assertEquals(original.rates, repo.state.value.rates)
        assertEquals(original.rates, store.value!!.rates)
    }

    @Test
    fun refreshesExpiredTokenAndPersistsRotatedRefreshToken() = runTest {
        val api = FakeShikimoriAccountApi()
        val store = MemoryShikimoriStore(shikimoriTestSession().copy(expiresAt = 1))
        val repo = repository(api, store)
        assertTrue(repo.sync())
        assertEquals(1, api.tokenCalls)
        assertEquals("new-refresh", store.value!!.refreshToken)
        assertEquals("Bearer new-access", api.seenHeaders.last())
    }

    @Test
    fun retries401OnceWithRenewedToken() = runTest {
        val api = FakeShikimoriAccountApi()
        api.pages = {
            if (api.seenHeaders.last() == "Bearer old-access")
                throw HttpException(Response.error<Unit>(401, "{}".toResponseBody()))
            else listOf(api.serverRate)
        }
        val repo = repository(api, MemoryShikimoriStore(shikimoriTestSession()))
        assertTrue(repo.sync())
        assertEquals(1, api.tokenCalls)
        assertEquals(listOf("Bearer old-access", "Bearer new-access"), api.seenHeaders)
    }

    @Test
    fun sendsOnlyChangedFieldsAndPreservesServerScore() = runTest {
        val api = FakeShikimoriAccountApi()
        val store = MemoryShikimoriStore(shikimoriTestSession())
        val messages = UiMessages()
        val repo = repository(api, store, messages)
        api.serverRate = api.serverRate.copy(score = 9) // Another client changed the score.
        assertTrue(repo.saveRate(Anime(-20, title = "Наруто", shikimoriId = 20), "on_hold", 7, 3))
        assertEquals(mapOf("status" to "on_hold"), api.requestedFields)
        assertEquals(9, repo.state.value.rates.single().score)
        assertTrue(messages.events.first().contains("Отложено"))
    }

    @Test
    fun failureShowsNoticeButDoesNotChangeList() = runTest {
        val api = FakeShikimoriAccountApi().apply { writeFailure = IOException("offline") }
        val store = MemoryShikimoriStore(shikimoriTestSession())
        val messages = UiMessages()
        val repo = repository(api, store, messages)
        assertFalse(repo.saveRate(Anime(-20, title = "Наруто"), "dropped", 7, 3))
        assertEquals("watching", repo.state.value.rates.single().status)
        assertTrue(messages.events.first().contains("не синхронизирован"))
    }

    @Test
    fun deletionIsAppliedOnlyAfterServerConfirmation() = runTest {
        val api = FakeShikimoriAccountApi()
        val repo = repository(api, MemoryShikimoriStore(shikimoriTestSession()))
        assertTrue(repo.deleteRate(api.serverRate))
        assertEquals(101L, api.deleted)
        assertTrue(repo.state.value.rates.isEmpty())
    }

    @Test
    fun missingShikimoriIdNeverUsesYummyIdForWrite() = runTest {
        val api = FakeShikimoriAccountApi()
        val repo = repository(api, MemoryShikimoriStore(shikimoriTestSession()))
        assertFalse(repo.saveRate(Anime(20, title = "Другое аниме"), "planned", 0, 0))
        assertTrue(api.requestedFields.isEmpty())
    }

    @Test
    fun graphqlErrorsDoNotEraseCache() = runTest {
        val api =
            FakeShikimoriAccountApi().apply { graphErrors = listOf(ShikimoriError("Forbidden")) }
        val repo = repository(api, MemoryShikimoriStore(shikimoriTestSession()))
        assertFalse(repo.sync())
        assertEquals(1, repo.state.value.rates.size)
    }

    @Test
    fun logoutClearsTokensAndAccountCache() = runTest {
        val store = MemoryShikimoriStore(shikimoriTestSession())
        val repo = repository(FakeShikimoriAccountApi(), store)
        assertTrue(repo.signOut())
        assertNull(store.value)
        assertNull(repo.state.value.user)
        assertTrue(repo.state.value.rates.isEmpty())
    }

    @Test
    fun parseGraphqlStringIdsAndNewPosters() {
        val json =
            """{"data":{"userRates":[{"id":"101","status":"watching","score":8,"episodes":3,
            "anime":{"id":"20","name":"Naruto","russian":"Наруто","episodes":220,
            "image":{"original":"https://shikimori.io/poster.jpg"}}}]}}"""
        val response =
            Moshi.Builder().build().adapter(AccountGraphResponse::class.java).fromJson(json)!!
        assertEquals(-20L, response.data!!.userRates!!.single().anime.toAnime().id)
        assertEquals(
            "https://shikimori.io/poster.jpg",
            response.data!!.userRates!!.single().anime.toAnime().posterUrl,
        )
    }

    @Test
    fun authorizationUrlHasOnlyRequiredScopeAndNoSecret() {
        val url = ShikimoriAccountRepository.authorizationUrl("my-client")
        assertTrue(url.contains("scope=user_rates"))
        assertFalse(url.contains("client_secret"))
        assertTrue(url.contains("response_type=code"))
    }

    @Test
    fun externalExceptionCannotLeakCredentialsToUi() = runTest {
        val api =
            FakeShikimoriAccountApi().apply {
                tokenFailure = IllegalArgumentException("sensitive-test-payload")
            }
        val messages = UiMessages()
        val repo = repository(api, MemoryShikimoriStore(), messages)
        assertFalse(repo.signIn(ShikimoriOAuthConfig("client", "secret", "AniRust"), "code"))
        assertFalse(messages.events.first().contains("sensitive-test-payload"))
        assertNull(repo.state.value.user)
    }

    @Test
    fun newLoginNeverKeepsPreviousAccountsListsWhenDownloadFails() = runTest {
        val api = FakeShikimoriAccountApi().apply { readFailure = IOException("offline") }
        val store =
            MemoryShikimoriStore(shikimoriTestSession().copy(user = ShikimoriUser(99, "Previous")))
        val repo = repository(api, store)
        assertFalse(repo.signIn(ShikimoriOAuthConfig("client", "secret", "AniRust"), "code"))
        assertEquals(7L, repo.state.value.user!!.id)
        assertTrue(repo.state.value.rates.isEmpty())
        assertEquals("new-refresh", store.value!!.refreshToken)
    }

    @Test
    fun creatingRateUsesConfirmedShikimoriIdAndShowsToast() = runTest {
        val api = FakeShikimoriAccountApi()
        val messages = UiMessages()
        val repo =
            repository(api, MemoryShikimoriStore(shikimoriTestSession(emptyList())), messages)
        assertTrue(repo.saveRate(Anime(111, title = "Наруто", shikimoriId = 20), "planned", 0, 0))
        assertEquals("20", api.requestedFields["target_id"])
        assertEquals("7", api.requestedFields["user_id"])
        assertEquals("Anime", api.requestedFields["target_type"])
        assertEquals("planned", repo.state.value.rates.single().status)
        assertTrue(messages.events.first().contains("Наруто"))
    }

    @Test
    fun confirmedRemoteWriteSurvivesLocalCacheFailure() = runTest {
        val store = MemoryShikimoriStore(shikimoriTestSession()).apply { failSave = true }
        val repo = repository(FakeShikimoriAccountApi(), store)
        assertTrue(repo.saveRate(Anime(-20, title = "Наруто"), "completed", 10, 220))
        assertEquals("completed", repo.state.value.rates.single().status)
        assertEquals("watching", store.value!!.rates.single().status)
    }
}

package com.anirust.app

import com.anirust.app.data.remote.shikimori.*
import com.anirust.app.data.remote.yummy.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FakeYummyApi : YummyApi {
    var search: suspend (String) -> YummyListResponse = { YummyListResponse(emptyList()) }
    var details: suspend (Long) -> YummyDetailResponse = {
        YummyDetailResponse(YummyAnimeDetail(animeId = it, title = "Title $it"))
    }
    var videos: suspend (Long) -> YummyVideosResponse = { YummyVideosResponse(emptyList()) }
    val requestedVideoIds = mutableListOf<Long>()

    override suspend fun searchAnime(query: String, lang: String) = search(query)

    override suspend fun getAnimeDetail(id: Long, lang: String) = details(id)

    override suspend fun getAnimeVideos(id: Long, lang: String): YummyVideosResponse {
        requestedVideoIds.add(id)
        return videos(id)
    }
}

class FakeShikimoriApi : ShikimoriApi {
    var response: suspend (ShikimoriGraphQlRequest) -> ShikimoriGraphQlResponse = {
        ShikimoriGraphQlResponse(ShikimoriData(emptyList()))
    }

    override suspend fun search(request: ShikimoriGraphQlRequest) = response(request)
}

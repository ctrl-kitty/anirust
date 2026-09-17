package com.anirust.app

import com.anirust.app.domain.model.Anime
import com.anirust.app.ui.search.SearchViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun debounceOnlySearchesTheLatestQuery() = runTest {
        val queries = mutableListOf<String>()
        val vm = SearchViewModel { query ->
            queries += query
            Result.success(emptyList())
        }
        vm.onQueryChange("N")
        advanceTimeBy(200)
        vm.onQueryChange("Naruto")
        advanceTimeBy(399)
        assertTrue(queries.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("Naruto"), queries)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun immediateSearchCannotOverwriteAReplacementEvenIfBackendIgnoresCancellation() = runTest {
        val vm = SearchViewModel { query ->
            withContext(NonCancellable) {
                delay(if (query == "old") 1000 else 10)
                Result.success(listOf(Anime(1, title = query)))
            }
        }
        vm.searchImmediately("old")
        runCurrent()
        vm.onQueryChange("new")
        advanceUntilIdle()
        assertEquals("new", vm.uiState.value.results.single().title)
        vm.onQueryChange("")
        assertFalse(vm.uiState.value.hasSearched)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    @Test
    fun clearingInputCancelsPendingSearch() = runTest {
        var count = 0
        val vm = SearchViewModel {
            count++
            Result.success(emptyList())
        }
        vm.onQueryChange("Naruto")
        vm.searchImmediately(" ")
        advanceUntilIdle()
        assertEquals(0, count)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun progressiveSearchPublishesCatalogThenMetadataWithoutStaleOverwrite() = runTest {
        val yummy =
            FakeYummyApi().apply {
                search = {
                    delay(100)
                    com.anirust.app.data.remote.yummy.YummyListResponse(
                        listOf(
                            com.anirust.app.data.remote.yummy.YummyAnimeItem(
                                animeId = 111,
                                title = "Naruto",
                                remoteIds =
                                    com.anirust.app.data.remote.yummy.YummyRemoteIds(
                                        shikimoriId = 20
                                    ),
                            )
                        )
                    )
                }
            }
        val shiki =
            FakeShikimoriApi().apply {
                response = {
                    delay(5000)
                    com.anirust.app.data.remote.shikimori.ShikimoriGraphQlResponse(
                        com.anirust.app.data.remote.shikimori.ShikimoriData(
                            listOf(
                                com.anirust.app.data.remote.shikimori.ShikimoriAnime(
                                    "20",
                                    russian = "Наруто",
                                )
                            )
                        )
                    )
                }
            }
        val repo =
            com.anirust.app.data.repository.AnimeRepository(
                yummy,
                shiki,
                com.anirust.app.data.remote.resolver.KodikResolver(okhttp3.OkHttpClient()),
                main.dispatcher,
            )
        val vm = SearchViewModel(com.anirust.app.domain.usecase.SearchAnimeUseCase(repo))
        vm.searchImmediately("Naruto")
        advanceTimeBy(101)
        runCurrent()
        assertEquals("Naruto", vm.uiState.value.results.single().title)
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals("Наруто", vm.uiState.value.results.single().title)
        assertFalse(vm.uiState.value.isLoading)
    }
}

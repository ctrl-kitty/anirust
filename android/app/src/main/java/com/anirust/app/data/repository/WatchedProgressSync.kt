package com.anirust.app.data.repository

import com.anirust.app.domain.usecase.WatchHistoryUseCase
import kotlinx.coroutines.flow.combine

/** Only advances a contiguous watched sequence in an existing Shikimori entry. */
class WatchedProgressSync(
    private val history: WatchHistoryUseCase,
    private val account: ShikimoriAccountRepository,
    private val settings: SettingsRepository,
) {
    private val attempts = mutableMapOf<Pair<Long, Long>, Pair<Int, Long>>()

    suspend fun run() {
        combine(history.getAllHistory(), account.state, settings.syncWatchedProgress) {
                rows,
                state,
                enabled ->
                Triple(rows, state, enabled)
            }
            .collect { (rows, state, enabled) ->
                if (!enabled || state.initializing || state.busy || state.needsLogin) return@collect
                val user = state.user ?: return@collect
                for (rate in state.rates) {
                    if (
                        account.state.value.user?.id != user.id ||
                            !settings.syncWatchedProgress.value
                    )
                        break
                    val aliases = settings.animeAliases(-rate.anime.id)
                    val watched =
                        rows
                            .filter { it.animeId in aliases && it.isCompleted }
                            .map { it.episodeNumber }
                            .toSet()
                    val anime = rate.anime.toAnime()
                    val episodes =
                        contiguousWatchedCount(rate.episodes, watched, anime.episodesCount)
                    val key = user.id to rate.anime.id
                    val target = episodes to state.lastSync
                    if (episodes <= rate.episodes || attempts[key] == target) continue
                    attempts[key] = target
                    val latest =
                        account.state.value.rates.firstOrNull { it.id == rate.id } ?: continue
                    if (episodes > latest.episodes)
                        account.saveRate(
                            anime,
                            latest.status,
                            latest.score,
                            episodes,
                            expectedUserId = user.id,
                        )
                }
            }
    }
}

internal fun contiguousWatchedCount(current: Int, watched: Set<Int>, total: Int?): Int {
    var next = current
    while (next + 1 in watched && (total == null || next < total)) next++
    return next
}

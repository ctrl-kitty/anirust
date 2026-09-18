package com.anirust.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anirust.app.domain.model.WatchHistoryItem

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String, // format: "animeId_episodeNumber_dubbing"
    val animeId: Long,
    val seriesId: String?,
    val animeTitle: String,
    val animePoster: String?,
    val episodeId: String,
    val episodeNumber: Int,
    val episodeTitle: String?,
    val dubbing: String?,
    val streamUrl: String?,
    val iframeUrl: String?,
    val playbackPositionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long,
    val completionOverride: Boolean? = null,
) {
    fun toDomain(): WatchHistoryItem =
        WatchHistoryItem(
            animeId = animeId,
            seriesId = seriesId,
            animeTitle = animeTitle,
            animePoster = animePoster,
            episodeId = episodeId,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            dubbing = dubbing,
            streamUrl = streamUrl,
            iframeUrl = iframeUrl,
            playbackPositionMs = playbackPositionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = lastWatchedTimestamp,
            completionOverride = completionOverride,
        )

    companion object {
        fun fromDomain(item: WatchHistoryItem): WatchHistoryEntity =
            WatchHistoryEntity(
                id = item.historyId,
                animeId = item.animeId,
                seriesId = item.seriesId,
                animeTitle = item.animeTitle,
                animePoster = item.animePoster,
                episodeId = item.episodeId,
                episodeNumber = item.episodeNumber,
                episodeTitle = item.episodeTitle,
                dubbing = item.dubbing,
                streamUrl = item.streamUrl,
                iframeUrl = item.iframeUrl,
                playbackPositionMs = item.playbackPositionMs,
                durationMs = item.durationMs,
                lastWatchedTimestamp = item.lastWatchedTimestamp,
                completionOverride = item.completionOverride,
            )
    }
}

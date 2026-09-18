package com.anirust.app.domain.model

data class WatchHistoryItem(
    val animeId: Long,
    val seriesId: String? = null,
    val animeTitle: String,
    val animePoster: String? = null,
    val episodeId: String,
    val episodeNumber: Int,
    val episodeTitle: String? = null,
    val dubbing: String? = null,
    val streamUrl: String? = null,
    val iframeUrl: String? = null,
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val completionOverride: Boolean? = null,
    val completionThreshold: Int = 95,
) {
    val isCompleted: Boolean
        get() =
            completionOverride == true ||
                (durationMs > 0 &&
                    playbackPositionMs.toDouble() / durationMs >= completionThreshold / 100.0)

    val progressPercent: Int
        get() =
            if (durationMs > 0)
                (playbackPositionMs.toDouble() * 100 / durationMs).toInt().coerceIn(0, 100)
            else 0

    val historyId: String
        get() = "${animeId}_${episodeNumber}_${dubbing ?: "default"}"

    val resumePositionMs: Long
        get() = if (isCompleted) 0L else playbackPositionMs.coerceAtLeast(0L)

    val progressFraction: Float
        get() =
            if (durationMs > 0)
                (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            else 0f
}

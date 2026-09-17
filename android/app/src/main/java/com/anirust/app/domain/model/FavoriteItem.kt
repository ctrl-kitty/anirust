package com.anirust.app.domain.model

enum class FavoriteStatus(val title: String) {
    WATCHING("Смотрю"),
    PLAN_TO_WATCH("В планах"),
    COMPLETED("Просмотрено"),
    FAVORITE("Любимое")
}

data class FavoriteItem(
    val animeId: Long,
    val title: String,
    val originalTitle: String? = null,
    val posterUrl: String? = null,
    val score: Float? = null,
    val episodesCount: Int? = null,
    val status: FavoriteStatus = FavoriteStatus.FAVORITE,
    val addedAt: Long = System.currentTimeMillis()
)

package com.anirust.app.domain.model

data class Anime(
    val id: Long,
    val yummyId: Long? = null,
    val shikimoriId: Long? = null,
    val myAnimeListId: Long? = null,
    val title: String,
    val originalTitle: String? = null,
    val altTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val score: Float? = null,
    val episodesCount: Int? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val source: String = "yummy",
) {
    /** Confirmed aliases only: positive catalog IDs and negative Shikimori IDs. */
    val localIds: List<Long>
        get() =
            listOfNotNull(
                    shikimoriId?.takeIf { it > 0 }?.let { -it },
                    id,
                    yummyId?.takeIf { it > 0 },
                )
                .distinct()

    val displayTitle: String
        get() = title.ifBlank { originalTitle ?: "Без названия" }

    val subtitle: String
        get() = originalTitle?.takeIf { it != title } ?: genres.take(3).joinToString(", ")
}

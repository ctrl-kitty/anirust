package com.anirust.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anirust.app.domain.model.FavoriteItem
import com.anirust.app.domain.model.FavoriteStatus

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val animeId: Long,
    val title: String,
    val originalTitle: String?,
    val posterUrl: String?,
    val score: Float?,
    val episodesCount: Int?,
    val status: String,
    val addedAt: Long
) {
    fun toDomain(): FavoriteItem = FavoriteItem(
        animeId = animeId,
        title = title,
        originalTitle = originalTitle,
        posterUrl = posterUrl,
        score = score,
        episodesCount = episodesCount,
        status = try {
            FavoriteStatus.valueOf(status)
        } catch (_: Exception) {
            FavoriteStatus.FAVORITE
        },
        addedAt = addedAt
    )

    companion object {
        fun fromDomain(item: FavoriteItem): FavoriteEntity = FavoriteEntity(
            animeId = item.animeId,
            title = item.title,
            originalTitle = item.originalTitle,
            posterUrl = item.posterUrl,
            score = item.score,
            episodesCount = item.episodesCount,
            status = item.status.name,
            addedAt = item.addedAt
        )
    }
}

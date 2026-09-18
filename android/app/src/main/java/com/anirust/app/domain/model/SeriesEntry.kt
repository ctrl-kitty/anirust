package com.anirust.app.domain.model

data class SeriesEntry(
    val id: String,
    val title: String,
    val order: Int? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
)

package com.anirust.app.domain.model

data class VoiceVariant(
    val id: String,
    val label: String,
    val lang: String? = "ru"
)

package com.anirust.app.domain.model

data class Episode(
    val id: String,
    val number: Int,
    val title: String? = null,
    val iframeUrl: String? = null,
    val voiceVariants: List<VoiceVariant> = emptyList(),
    val playerKind: PlayerKind = PlayerKind.Unknown
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "Серия $number"
}

package com.anirust.app.domain.model

import java.net.URI

enum class PlayerKind(val displayName: String) {
    Kodik("Kodik"),
    Direct("Direct HLS"),
    Alloha("Alloha"),
    Sibnet("Sibnet"),
    Unknown("Неизвестный");

    val isPlayable: Boolean
        get() = this == Kodik || this == Direct

    companion object {
        fun fromUrl(url: String): PlayerKind {
            val normalized = url.trim().let { if (it.startsWith("//")) "https:$it" else it }
            val uri =
                try {
                    URI(normalized)
                } catch (_: Exception) {
                    return Unknown
                }
            if (uri.scheme != "http" && uri.scheme != "https") return Unknown
            val host = uri.host?.lowercase() ?: return Unknown
            val path = uri.path.orEmpty().lowercase()
            return when {
                listOf(".m3u8", ".mp4", ".mkv", ".webm").any { path.endsWith(it) } -> Direct
                host.contains("kodik") -> Kodik
                host.contains("alloha") -> Alloha
                host.contains("sibnet") -> Sibnet
                else -> Unknown
            }
        }
    }
}

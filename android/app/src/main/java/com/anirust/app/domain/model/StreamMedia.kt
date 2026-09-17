package com.anirust.app.domain.model

data class StreamMedia(
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val quality: String? = null,
    val dubbing: String? = null,
    val iframeUrl: String? = null,
) {
    fun toHeaderList(): List<String> {
        return headers.map { "${it.key}: ${it.value}" }
    }

    fun toHeaderArgString(): String {
        return headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
    }
}

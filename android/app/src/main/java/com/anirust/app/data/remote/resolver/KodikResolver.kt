package com.anirust.app.data.remote.resolver

import com.anirust.app.data.repository.repositoryResult
import com.anirust.app.domain.model.StreamMedia
import java.io.IOException
import java.net.URI
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject

class KodikResolver(private val okHttpClient: OkHttpClient) {
    private val playerJsRegex = Pattern.compile("""src="(/assets/js/app\.player[^"]+\.js)"""")
    private val playerLinkRegex = Pattern.compile("""playerLink\s*=\s*\["([^"]+)"\]""")
    private val ajaxAtobRegex = Pattern.compile("""\$\.ajax[^\)]*atob\(['"](\w+=)['"]\)""")
    private val urlParamsRegex = Pattern.compile("""var urlParams = '([^']+)'""")
    private val vInfoRegex = Pattern.compile("""vInfo\.(\w+)\s*=\s*'([^']*)'""")
    private val varTypeRegex = Pattern.compile("""var type = "([^"]+)"""")
    private val varVideoIdRegex = Pattern.compile("""var videoId = "([^"]+)"""")
    private val hashUrlRegex = Pattern.compile("""/([a-f0-9]{32})/""")

    suspend fun resolve(rawIframeUrl: String): Result<StreamMedia> =
        withContext(Dispatchers.IO) {
            repositoryResult {
                val iframeUrl = prefixHttps(rawIframeUrl)
                val uri = URI(iframeUrl)
                require(
                    !uri.host.isNullOrBlank() && (uri.scheme == "https" || uri.scheme == "http")
                ) {
                    "Некорректный URL плеера"
                }
                val origin = "${uri.scheme}://${uri.rawAuthority}"

                // 1. Fetch iframe HTML
                val iframeRequest =
                    Request.Builder()
                        .url(iframeUrl)
                        .header("User-Agent", USER_AGENT)
                        .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        )
                        .build()

                val html = requestText(iframeRequest)

                if (html.contains("Видео не найдено") || html.contains("video_not_found")) {
                    throw IOException("Видео не найдено на Kodik")
                }

                // 2. Extract player JS path
                val playerJsPath =
                    extractPlayerJsPath(html)
                        ?: throw IllegalStateException("Не удалось найти путь к JS плееру Kodik")

                val playerJsUrl = uri.resolve(playerJsPath).toString()

                // 3. Fetch player JS and extract API endpoint
                val apiPath =
                    fetchApiPath(playerJsUrl)
                        ?: throw IllegalStateException(
                            "Не удалось извлечь API endpoint из JS плеера Kodik"
                        )

                // 4. Extract parameters
                val payload = mutableMapOf<String, String>()
                extractUrlParams(html)?.let { payload.putAll(it) }
                payload.putAll(extractVInfo(html, iframeUrl))
                payload["bad_user"] = "false"
                payload["cdn_is_working"] = "true"
                payload["info"] = "{}"

                // 5. Send POST request to Kodik API
                val apiUrl = uri.resolve(apiPath).toString()
                val formBuilder = FormBody.Builder()
                for ((k, v) in payload) {
                    formBuilder.add(k, v)
                }

                val apiRequest =
                    Request.Builder()
                        .url(apiUrl)
                        .post(formBuilder.build())
                        .header("User-Agent", USER_AGENT)
                        .header("Origin", origin)
                        .header("Referer", iframeUrl)
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                val apiBody = requestText(apiRequest)

                val json = JSONObject(apiBody)
                val linksObj =
                    json.optJSONObject("links")
                        ?: throw IllegalStateException("Kodik API не вернул ссылки на видео")

                // Try every advertised source, highest quality first.
                val qualities =
                    linksObj.keys().asSequence().toList().sortedByDescending {
                        it.toIntOrNull() ?: 0
                    }
                for (quality in qualities) {
                    val array = linksObj.optJSONArray(quality) ?: continue
                    for (index in 0 until array.length()) {
                        val source = array.optJSONObject(index)?.optString("src").orEmpty()
                        if (source.isBlank()) continue
                        val decoded = decodeKodikUrl(source, quality)
                        if (decoded.isBlank()) continue
                        return@repositoryResult StreamMedia(
                            streamUrl = decoded,
                            headers =
                                mapOf(
                                    "Referer" to iframeUrl,
                                    "Origin" to origin,
                                    "User-Agent" to USER_AGENT,
                                ),
                            quality = "${quality}p",
                        )
                    }
                }

                throw IOException("Не найден воспроизводимый поток в ответе Kodik")
            }
        }

    private fun extractPlayerJsPath(html: String): String? {
        val matcher1 = playerJsRegex.matcher(html)
        if (matcher1.find()) return matcher1.group(1)

        val matcher2 = playerLinkRegex.matcher(html)
        if (matcher2.find()) return matcher2.group(1)

        return null
    }

    private suspend fun fetchApiPath(playerJsUrl: String): String? {
        val request = Request.Builder().url(playerJsUrl).header("User-Agent", USER_AGENT).build()
        val js = requestText(request)

        val matcher = ajaxAtobRegex.matcher(js)
        if (matcher.find()) {
            val encoded = matcher.group(1) ?: return null
            return encoded.decodeBase64()?.utf8()
        }
        return null
    }

    private suspend fun requestText(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val text =
                                response.use {
                                    if (!it.isSuccessful)
                                        throw IOException("Плеер недоступен (HTTP ${it.code})")
                                    it.body?.string()?.takeIf(String::isNotBlank)
                                        ?: throw IOException("Плеер вернул пустой ответ")
                                }
                            if (continuation.isActive) continuation.resume(text)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            )
        }

    private fun extractUrlParams(html: String): Map<String, String>? {
        val matcher = urlParamsRegex.matcher(html)
        if (matcher.find()) {
            val jsonStr = matcher.group(1) ?: return null
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.optString(key)
            }
            return map
        }
        return null
    }

    private fun extractVInfo(html: String, iframeUrl: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val matcher = vInfoRegex.matcher(html)
        while (matcher.find()) {
            val key = matcher.group(1)
            val value = matcher.group(2)
            if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                map[key] = value
            }
        }

        if (!map.containsKey("type")) {
            val typeMatcher = varTypeRegex.matcher(html)
            if (typeMatcher.find()) {
                typeMatcher.group(1)?.let { map["type"] = it }
            }
        }

        if (!map.containsKey("id")) {
            val idMatcher = varVideoIdRegex.matcher(html)
            if (idMatcher.find()) {
                idMatcher.group(1)?.let { map["id"] = it }
            }
        }

        if (!map.containsKey("hash")) {
            val hashMatcher = hashUrlRegex.matcher(iframeUrl)
            if (hashMatcher.find()) {
                hashMatcher.group(1)?.let { map["hash"] = it }
            }
        }

        return map
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        fun decryptRot(value: String): String {
            val sb = StringBuilder(value.length)
            for (ch in value) {
                when (ch) {
                    in 'A'..'Z' -> {
                        val base = ch.code - 'A'.code
                        val rotated = (base + 18) % 26 + 'A'.code
                        sb.append(rotated.toChar())
                    }
                    in 'a'..'z' -> {
                        val base = ch.code - 'a'.code
                        val rotated = (base + 18) % 26 + 'a'.code
                        sb.append(rotated.toChar())
                    }
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun decodeKodikUrl(encoded: String, qualityKey: String): String {
            if (
                encoded.startsWith("//") ||
                    encoded.startsWith("https://") ||
                    encoded.startsWith("http://")
            ) {
                return validatedUrl(prefixHttps(encoded))
            }

            val decryptedRot = decryptRot(encoded)
            val sanitized = decryptedRot.replace('-', '+').replace('_', '/')
            val validChars = StringBuilder()
            for (ch in sanitized) {
                if (ch.isLetterOrDigit() || ch == '+' || ch == '/' || ch == '=') {
                    validChars.append(ch)
                }
            }
            while (validChars.length % 4 != 0) {
                validChars.append('=')
            }

            return try {
                var decoded = validChars.toString().decodeBase64()?.utf8() ?: return ""
                decoded = prefixHttps(decoded)
                if (qualityKey == "720" && decoded.contains("/480.mp4:")) {
                    decoded = decoded.replace("/480.mp4:", "/720.mp4:")
                }
                validatedUrl(decoded)
            } catch (_: Exception) {
                ""
            }
        }

        private fun validatedUrl(value: String): String =
            try {
                val uri = URI(value)
                if ((uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank())
                    value
                else ""
            } catch (_: Exception) {
                ""
            }

        fun prefixHttps(url: String): String {
            return when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                url.startsWith("//") -> "https:$url"
                else -> "https://${url.trim().trimStart('/')}"
            }
        }
    }
}

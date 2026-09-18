package com.anirust.app.ui.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.anirust.app.domain.model.StreamMedia

object ExternalPlayerHelper {

    const val MPV_PACKAGE = "is.xyz.mpv"
    const val VLC_PACKAGE = "org.videolan.vlc"

    fun openInExternalPlayer(
        context: Context,
        stream: StreamMedia,
        title: String? = null,
        targetPackage: String? = MPV_PACKAGE,
        positionMs: Long = 0L,
        historyId: String? = null,
        onError: (String) -> Unit = {},
    ): Boolean {
        val host = if (historyId != null) context.externalPlayerHost() else null
        fun launch(intent: Intent) {
            if (host != null && historyId != null) host.launchExternalPlayer(intent, historyId)
            else context.startActivity(intent)
        }
        val uri = stream.streamUrl.toUri()
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                if (host == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // mpvEx consumes User-Agent first, then the remaining name/value pairs.
                // Keep this order for the chooser too; stock mpv-android ignores headers.
                putExtra("headers", externalHeaders(stream.headers))
                putExtra("http-header-fields", stream.toHeaderArgString())
                putExtra("position", positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())

                putExtra(
                    "android.media.intent.extra.HTTP_HEADERS",
                    Bundle().apply {
                        stream.headers.forEach { (name, value) -> putString(name, value) }
                    },
                )

                if (!title.isNullOrBlank()) {
                    putExtra("title", title)
                }
            }

        // Try launching target package (e.g. mpv-android)
        if (!targetPackage.isNullOrBlank() && targetPackage != "system_chooser") {
            try {
                val packageIntent = Intent(intent).setPackage(targetPackage)
                launch(packageIntent)
                return true
            } catch (_: ActivityNotFoundException) {
                onError(
                    "Выбранный плеер не установлен. Установи его или выбери встроенный в настройках"
                )
                return false
            } catch (_: SecurityException) {
                onError("Выбранный плеер недоступен. Выбери другой в настройках")
                return false
            } catch (_: IllegalStateException) {
                onError("Дождитесь возврата из внешнего плеера")
                return false
            }
        }

        // Fallback: system chooser
        return try {
            if (intent.resolveActivity(context.packageManager) == null) {
                onError("Установите mpvEx, MPV или VLC для просмотра во внешнем плеере")
                return false
            }
            val chooserIntent =
                Intent.createChooser(intent, "Выберите видеоплеер").apply {
                    if (host == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            launch(chooserIntent)
            true
        } catch (_: Exception) {
            onError("Не удалось открыть внешний плеер. Проверьте, что он установлен")
            false
        }
    }

    private fun Context.externalPlayerHost(): ExternalPlayerHost? =
        when (this) {
            is ExternalPlayerHost -> this
            is ContextWrapper -> baseContext.takeIf { it !== this }?.externalPlayerHost()
            else -> null
        }

    internal fun externalHeaders(headers: Map<String, String>): Array<String> {
        val userAgent =
            headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
        return buildList {
                add("User-Agent")
                add(userAgent?.value ?: "AnirustAndroid/1.0")
                headers.forEach { (name, value) ->
                    if (!name.equals("User-Agent", ignoreCase = true)) {
                        add(name)
                        add(value)
                    }
                }
            }
            .toTypedArray()
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}

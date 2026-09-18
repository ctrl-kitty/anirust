package com.anirust.app.ui.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.anirust.app.domain.usecase.WatchHistoryUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ExternalPlayerHost {
    fun launchExternalPlayer(intent: Intent, historyId: String)
}

/** Keeps the episode identity across activity/process recreation while mpvEx is open. */
class ExternalPlaybackTracker(
    activity: ComponentActivity,
    history: WatchHistoryUseCase,
    registry: ActivityResultRegistry = activity.activityResultRegistry,
) : ExternalPlayerHost {
    private var pendingHistoryId =
        activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)?.getString("historyId")

    private val launcher =
        registry.register(STATE_KEY, activity, ActivityResultContracts.StartActivityForResult()) {
            result ->
            val historyId = pendingHistoryId
            pendingHistoryId = null
            if (historyId != null && result.resultCode == Activity.RESULT_OK) {
                val progress = mpvExProgress(result.data)
                if (progress != null) {
                    activity.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        withContext(NonCancellable) {
                            history.updateProgress(historyId, progress.first, progress.second)
                            history.finishPlayback(historyId)
                        }
                    }
                }
            }
        }

    init {
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) {
            Bundle().apply { putString("historyId", pendingHistoryId) }
        }
    }

    override fun launchExternalPlayer(intent: Intent, historyId: String) {
        check(pendingHistoryId == null) { "Внешний плеер уже открыт" }
        pendingHistoryId = historyId
        try {
            launcher.launch(intent)
        } catch (error: RuntimeException) {
            pendingHistoryId = null
            throw error
        }
    }

    companion object {
        private const val STATE_KEY = "external_playback"
        private const val MPVEX_RESULT = "app.marlboroadvance.mpvex.ui.player.PlayerActivity.result"

        @Suppress("DEPRECATION")
        internal fun mpvExProgress(data: Intent?): Pair<Long, Long>? {
            if (data?.action != MPVEX_RESULT) return null
            fun milliseconds(key: String): Long? =
                when (val value = data.extras?.get(key)) {
                    is Int -> value.toLong()
                    is Long -> value
                    else -> null
                }
            val position = milliseconds("position") ?: return null
            val duration = milliseconds("duration") ?: return null
            if (position < 0 || duration <= 0) return null
            return position.coerceAtMost(duration) to duration
        }
    }
}

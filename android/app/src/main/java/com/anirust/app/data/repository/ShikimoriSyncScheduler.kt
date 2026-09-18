package com.anirust.app.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/** Runs only while the app is foregrounded; keeps deadlines across short background trips. */
class ShikimoriSyncScheduler(
    private val repository: ShikimoriAccountRepository,
    private val intervalMinutes: StateFlow<Int>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var startupPending = true
    private var attemptedUserId: Long? = null
    private var lastAttemptAt: Long? = null

    suspend fun run() {
        val initial = repository.state.first { !it.initializing }
        if (initial.user == null) startupPending = false // Signing in downloads the lists itself.
        intervalMinutes.collectLatest { minutes ->
            val interval = minutes.coerceIn(5, 120) * 60_000L
            while (true) {
                val account =
                    repository.state.first {
                        !it.initializing && !it.busy && it.user != null && !it.needsLogin
                    }
                if (attemptedUserId != account.user?.id) {
                    attemptedUserId = account.user?.id
                    lastAttemptAt = null
                }
                val latest = maxOf(account.lastSync, lastAttemptAt ?: 0L).coerceAtMost(now())
                val remaining = if (startupPending) 0L else latest + interval - now()
                if (remaining > 0) {
                    delay(remaining)
                    continue // Recheck login, manual sync and mutations before making a request.
                }
                startupPending = false
                lastAttemptAt = now() // Failed requests also wait one interval before retrying.
                repository.sync(notify = false)
            }
        }
    }
}

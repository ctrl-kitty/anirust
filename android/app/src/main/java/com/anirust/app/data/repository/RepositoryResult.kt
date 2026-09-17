package com.anirust.app.data.repository

import kotlinx.coroutines.CancellationException

/** Cancellation belongs to the calling job, never to a screen's error state. */
internal suspend fun <T> repositoryResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

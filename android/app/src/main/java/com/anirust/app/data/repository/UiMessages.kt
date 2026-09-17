package com.anirust.app.data.repository

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** One foreground consumer displays operation results as Toasts, including after navigation. */
class UiMessages {
    private val channel = Channel<String>(Channel.UNLIMITED)
    val events = channel.receiveAsFlow()

    fun show(message: String) {
        channel.trySend(message)
    }
}

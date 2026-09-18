package com.anirust.app.data.repository

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** One foreground consumer displays operation results as Snackbars, including after navigation. */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (suspend () -> Unit)? = null,
)

class UiMessages {
    private val channel = Channel<UiMessage>(Channel.UNLIMITED)
    val events = channel.receiveAsFlow()

    fun show(message: String, actionLabel: String? = null, action: (suspend () -> Unit)? = null) {
        channel.trySend(UiMessage(message, actionLabel, action))
    }
}

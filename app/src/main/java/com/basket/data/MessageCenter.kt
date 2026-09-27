package com.basket.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A snackbar message that must survive a navigation, e.g. "Milk added" shown on List detail after the item form
 * closes, or "Apple added to Weekly shop" shown on Browse after Product detail. [undo] runs in the app scope, so it
 * still works after the screen that posted the message is gone.
 */
data class UserMessage(
    val text: String,
    val undo: (suspend () -> Unit)? = null,
)

/** Hands [UserMessage]s from the screen that posts them to the screen that is visible next. */
@Singleton
class MessageCenter @Inject constructor() {
    private val channel = Channel<UserMessage>(Channel.BUFFERED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Collect while the screen is at least STARTED; each message is delivered once. */
    val messages: Flow<UserMessage> = channel.receiveAsFlow()

    fun post(message: UserMessage) {
        channel.trySend(message)
    }

    /** Runs the message's Undo action. */
    fun undo(message: UserMessage) {
        val action = message.undo ?: return
        scope.launch { action() }
    }
}

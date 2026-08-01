package com.zhousl.aether.channel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseAetherChannel(
    final override val kind: ChannelKind,
) : AetherChannel {
    protected val mutableStatus = MutableStateFlow(ChannelStatus(kind))
    protected val mutableIncomingMessages = MutableSharedFlow<ChannelIncomingMessage>(extraBufferCapacity = 64)

    final override val status = mutableStatus.asStateFlow()
    final override val incomingMessages = mutableIncomingMessages.asSharedFlow()

    protected fun updateStatus(state: ChannelConnectionState, detail: String = "") {
        mutableStatus.value = ChannelStatus(kind, state, detail)
    }

    protected suspend fun emitIncoming(message: ChannelIncomingMessage) {
        mutableIncomingMessages.emit(message)
    }
}

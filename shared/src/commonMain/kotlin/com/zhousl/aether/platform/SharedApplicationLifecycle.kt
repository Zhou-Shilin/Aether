package com.zhousl.aether.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SharedApplicationLifecycle {
    private val mutableBackgrounded = MutableStateFlow(false)
    val backgrounded: StateFlow<Boolean> = mutableBackgrounded.asStateFlow()

    fun enterBackground() {
        mutableBackgrounded.value = true
    }

    fun enterForeground() {
        mutableBackgrounded.value = false
    }
}

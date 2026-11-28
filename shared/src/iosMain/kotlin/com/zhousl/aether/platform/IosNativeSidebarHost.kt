package com.zhousl.aether.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Presentation-only bridge for the native SwiftUI Liquid Glass sidebar on iPhone and iPad. */
interface IosNativeSidebarListener {
    fun onSnapshotChanged(snapshotJson: String)
    fun onToggleRequested()
    fun onCloseRequested()
}

interface IosNativeSidebarActionHandler {
    fun handle(action: String, payloadJson: String)
}

object IosNativeSidebarHost {
    private var listener: IosNativeSidebarListener? = null
    private var actionHandler: IosNativeSidebarActionHandler? = null

    private val leadingInsetState = MutableStateFlow(0f)
    val leadingInsetDp = leadingInsetState.asStateFlow()

    private val obscuresChatState = MutableStateFlow(false)
    val obscuresChatControls = obscuresChatState.asStateFlow()

    private val openState = MutableStateFlow(false)
    val isOpen = openState.asStateFlow()

    var snapshotJson: String = "{\"visible\":false}"
        private set

    fun setListener(listener: IosNativeSidebarListener?) {
        this.listener = listener
        listener?.onSnapshotChanged(snapshotJson)
    }

    fun setActionHandler(handler: IosNativeSidebarActionHandler?) {
        actionHandler = handler
    }

    fun publishSnapshot(snapshotJson: String) {
        if (snapshotJson == this.snapshotJson) return
        this.snapshotJson = snapshotJson
        listener?.onSnapshotChanged(snapshotJson)
    }

    fun publishHidden() {
        publishSnapshot("{\"visible\":false}")
        updatePresentation(leadingInsetDp = 0f, obscuresChatControls = false, isOpen = false)
    }

    fun requestToggle() {
        listener?.onToggleRequested()
    }

    fun requestClose() {
        listener?.onCloseRequested()
    }

    fun perform(action: String, payloadJson: String = "{}") {
        actionHandler?.handle(action, payloadJson)
    }

    fun updatePresentation(leadingInsetDp: Float, obscuresChatControls: Boolean, isOpen: Boolean) {
        if (leadingInsetState.value != leadingInsetDp) {
            leadingInsetState.value = leadingInsetDp
        }
        if (obscuresChatState.value != obscuresChatControls) {
            obscuresChatState.value = obscuresChatControls
        }
        if (openState.value != isOpen) {
            openState.value = isOpen
        }
    }
}

package com.zhousl.aether.ui

/** Emits at most once per closed-to-open epoch, deferring until listener registration. */
internal class AetherDrawerOpenedEventGate {
    private var drawerOpen = false
    private var dispatchedForCurrentOpen = false

    fun onDrawerStateChanged(
        drawerOpen: Boolean,
        eventRegistered: Boolean,
    ): Boolean {
        if (drawerOpen != this.drawerOpen) {
            this.drawerOpen = drawerOpen
            dispatchedForCurrentOpen = false
        }
        if (!drawerOpen || !eventRegistered || dispatchedForCurrentOpen) return false

        dispatchedForCurrentOpen = true
        return true
    }
}

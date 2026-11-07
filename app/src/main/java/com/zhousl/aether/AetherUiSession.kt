package com.zhousl.aether

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** One controller and capability registration for all windows in this process.
 * No service, extra process or controller per web app is created. */
class AetherUiSession : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var owners = 0
    fun retainController() { owners++ }
    fun releaseController() {
        check(owners > 0)
        owners--
        if (owners == 0) viewModelStore.clear()
    }
    private var clients = 0
    private var release: (() -> Unit)? = null

    @androidx.annotation.MainThread
    fun acquire(bind: () -> (() -> Unit)): () -> Unit {
        if (clients == 0) release = bind()
        clients++
        var closed = false
        return {
            if (!closed) {
                closed = true
                clients--
                if (clients == 0) { release?.invoke(); release = null }
            }
        }
    }
}

/** The activity's normal ViewModelStore retains this lease across rotation and
 * releases it when the task really finishes. The final task clears the controller. */
class AetherWindowLease(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val session = (application as AetherApplication).uiSession
    init { session.retainController() }
    override fun onCleared() { session.releaseController() }
}

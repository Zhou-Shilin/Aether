package com.zhousl.aether.platform

import platform.UIKit.UIViewController

/** Presentation only. All chat actions and persisted state remain in the shared session owner. */
interface IosNativeChatActionHandler {
    fun handle(action: String, value: String, completion: (Boolean) -> Unit)
}

interface IosNativeChatFactory {
    fun create(kind: String, snapshot: String, handler: IosNativeChatActionHandler): UIViewController
    fun update(controller: UIViewController, snapshot: String)
}

object IosNativeChatHost {
    var factory: IosNativeChatFactory? = null
}

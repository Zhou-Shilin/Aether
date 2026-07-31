package com.zhousl.aether.data.pi

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createChromeHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            connectionProxyDictionary = emptyMap<Any?, Any>()
        }
    }
    install(WebSockets)
}

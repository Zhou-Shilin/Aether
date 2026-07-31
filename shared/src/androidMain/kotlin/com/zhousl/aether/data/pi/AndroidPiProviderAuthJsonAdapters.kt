package com.zhousl.aether.data.pi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

fun JSONObject.toPiOAuthPrompt(): PiOAuthPrompt =
    Json.parseToJsonElement(toString()).jsonObject.toPiOAuthPrompt()

fun JSONObject.toPiProviderEnvironmentVariables() =
    Json.parseToJsonElement(toString()).jsonObject.toPiProviderEnvironmentVariables()

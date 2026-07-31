package com.zhousl.aether.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.json.JSONArray
import org.json.JSONObject

fun LlmProviderConfig.toJson(): JSONObject = JSONObject(toJsonObject().toString())

fun List<LlmCustomHeader>.toJsonArray(): JSONArray =
    JSONArray(toKotlinJsonArray().toString())

fun parseCustomHeaders(array: JSONArray?): List<LlmCustomHeader> =
    parseCustomHeaders(array?.toKotlinJsonArray())

fun parseProviderEnvironmentVariables(
    array: JSONArray?,
): List<PiProviderEnvironmentVariable> =
    parseProviderEnvironmentVariables(array?.toKotlinJsonArray())

private fun JSONArray.toKotlinJsonArray(): JsonArray =
    Json.parseToJsonElement(toString()).jsonArray

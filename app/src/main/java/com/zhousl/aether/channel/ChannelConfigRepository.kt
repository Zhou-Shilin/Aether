package com.zhousl.aether.channel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class ChannelConfigRepository(context: Context) {
    private val preferences = context.getSharedPreferences("aether_channels", Context.MODE_PRIVATE)
    private val mutableConfigs = MutableStateFlow(load())
    val configs: StateFlow<List<ChannelConfig>> = mutableConfigs.asStateFlow()

    @Synchronized
    fun upsert(config: ChannelConfig) {
        val updated = mutableConfigs.value.associateBy { it.kind }.toMutableMap().apply {
            put(config.kind, config)
        }.values.sortedBy { it.kind.ordinal }
        persist(updated)
    }

    fun setEnabled(kind: ChannelKind, enabled: Boolean) {
        upsert((mutableConfigs.value.firstOrNull { it.kind == kind } ?: ChannelConfig.default(kind)).copy(enabled = enabled))
    }

    private fun load(): List<ChannelConfig> {
        val saved = preferences.getString("configs", null)?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                buildList { repeat(array.length()) { ChannelConfig.fromJson(array.getJSONObject(it))?.let(::add) } }
            }.getOrNull()
        }.orEmpty().associateBy { it.kind }
        return ChannelKind.entries.map { saved[it] ?: ChannelConfig.default(it) }
    }

    private fun persist(configs: List<ChannelConfig>) {
        mutableConfigs.value = configs
        preferences.edit().putString("configs", JSONArray(configs.map { it.toJson() }).toString()).apply()
    }
}

package com.zhousl.aether.channel

import com.zhousl.aether.channel.dingtalk.DingTalkChannel
import com.zhousl.aether.channel.feishu.FeishuChannel
import com.zhousl.aether.channel.wechat.WeChatChannel
import com.zhousl.aether.channel.wecom.WeComChannel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

class ChannelRegistry(
    private val scope: CoroutineScope,
    private val inboundFileStore: ChannelInboundFileStore = ChannelInboundFileStore(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build(),
) {
    fun create(config: ChannelConfig): AetherChannel = when (config.kind) {
        ChannelKind.Feishu -> FeishuChannel(config, scope, http, inboundFileStore)
        ChannelKind.DingTalk -> DingTalkChannel(config, scope, http, inboundFileStore)
        ChannelKind.WeChat -> WeChatChannel(config, scope, http, inboundFileStore)
        ChannelKind.WeCom -> WeComChannel(config, scope, http, inboundFileStore)
    }
}

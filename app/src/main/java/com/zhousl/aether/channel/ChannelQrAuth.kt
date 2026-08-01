package com.zhousl.aether.channel

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed interface ChannelBindingState {
    data object Idle : ChannelBindingState
    data object Loading : ChannelBindingState
    data class AwaitingScan(
        val scanUrl: String,
        val scanned: Boolean = false,
    ) : ChannelBindingState
    data object Saving : ChannelBindingState
    data class Bound(val completedAtMillis: Long = System.currentTimeMillis()) : ChannelBindingState
    data class Expired(val detail: String = "") : ChannelBindingState
    data class Error(val detail: String) : ChannelBindingState
}

internal data class ChannelQrCode(
    val scanUrl: String,
    val pollToken: String,
)

internal enum class ChannelQrPollStatus { Waiting, Scanned, Success, Expired, Failure }

internal data class ChannelQrPollResult(
    val status: ChannelQrPollStatus,
    val credentials: Map<String, String> = emptyMap(),
    val detail: String = "",
)

internal interface ChannelQrAuthHandler {
    val kind: ChannelKind
    val pollIntervalMillis: Long get() = 2_000L
    suspend fun fetchQrCode(config: ChannelConfig): ChannelQrCode
    suspend fun pollStatus(token: String, config: ChannelConfig): ChannelQrPollResult
}

/**
 * Owns the QR binding lifecycle independently from Compose. This mirrors QwenPaw's
 * fetch-QR/poll-status contract while keeping polling cancellable and lifecycle-safe.
 */
class ChannelQrAuthManager(
    private val scope: CoroutineScope,
    private val configRepository: ChannelConfigRepository,
    http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(65, TimeUnit.SECONDS)
        .build(),
) {
    private val handlers: Map<ChannelKind, ChannelQrAuthHandler> = listOf(
        FeishuQrAuthHandler(http),
        DingTalkQrAuthHandler(http),
        WeChatQrAuthHandler(http),
        WeComQrAuthHandler(http),
    ).associateBy(ChannelQrAuthHandler::kind)
    private val bindingJobs = mutableMapOf<ChannelKind, Job>()
    private val mutableStates = MutableStateFlow(
        ChannelKind.entries.associateWith<ChannelKind, ChannelBindingState> { ChannelBindingState.Idle }
    )
    val states: StateFlow<Map<ChannelKind, ChannelBindingState>> = mutableStates.asStateFlow()

    @Synchronized
    fun start(kind: ChannelKind) {
        bindingJobs.remove(kind)?.cancel()
        update(kind, ChannelBindingState.Loading)
        bindingJobs[kind] = scope.launch {
            val handler = handlers.getValue(kind)
            val config = currentConfig(kind)
            try {
                val qrCode = handler.fetchQrCode(config)
                update(kind, ChannelBindingState.AwaitingScan(qrCode.scanUrl))
                val deadline = System.currentTimeMillis() + BindingTimeoutMillis
                var transientFailures = 0
                while (isActive && System.currentTimeMillis() < deadline) {
                    delay(handler.pollIntervalMillis)
                    val result = try {
                        handler.pollStatus(qrCode.pollToken, currentConfig(kind)).also {
                            transientFailures = 0
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        transientFailures++
                        if (transientFailures >= MaxConsecutivePollFailures) throw error
                        continue
                    }
                    when (result.status) {
                        ChannelQrPollStatus.Waiting -> Unit
                        ChannelQrPollStatus.Scanned -> update(
                            kind,
                            ChannelBindingState.AwaitingScan(qrCode.scanUrl, scanned = true),
                        )
                        ChannelQrPollStatus.Success -> {
                            update(kind, ChannelBindingState.Saving)
                            saveCredentials(kind, result.credentials)
                            update(kind, ChannelBindingState.Bound())
                            return@launch
                        }
                        ChannelQrPollStatus.Expired -> {
                            update(kind, ChannelBindingState.Expired(result.detail))
                            return@launch
                        }
                        ChannelQrPollStatus.Failure -> {
                            update(
                                kind,
                                ChannelBindingState.Error(result.detail.ifBlank { "Authorization failed" }),
                            )
                            return@launch
                        }
                    }
                }
                if (isActive) update(kind, ChannelBindingState.Expired("Authorization timed out"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                update(kind, ChannelBindingState.Error(error.message ?: "Unable to start QR authorization"))
            }
        }
    }

    @Synchronized
    fun cancel(kind: ChannelKind) {
        bindingJobs.remove(kind)?.cancel()
        update(kind, ChannelBindingState.Idle)
    }

    private fun currentConfig(kind: ChannelKind): ChannelConfig =
        configRepository.configs.value.firstOrNull { it.kind == kind } ?: ChannelConfig.default(kind)

    private fun saveCredentials(kind: ChannelKind, credentials: Map<String, String>) {
        val current = currentConfig(kind)
        val updated = when (kind) {
            ChannelKind.Feishu -> current.copy(
                appId = credentials["app_id"].orEmpty().ifBlank { current.appId },
                appSecret = credentials["app_secret"].orEmpty().ifBlank { current.appSecret },
                baseUrl = when (credentials["tenant_brand"]?.lowercase()) {
                    "lark" -> "https://open.larksuite.com"
                    "feishu" -> "https://open.feishu.cn"
                    else -> current.baseUrl
                },
            )
            ChannelKind.DingTalk -> current.copy(
                appId = credentials["client_id"].orEmpty().ifBlank { current.appId },
                appSecret = credentials["client_secret"].orEmpty().ifBlank { current.appSecret },
            )
            ChannelKind.WeChat -> current.copy(
                token = credentials["bot_token"].orEmpty().ifBlank { current.token },
                baseUrl = credentials["base_url"].orEmpty().ifBlank { current.baseUrl },
            )
            ChannelKind.WeCom -> current.copy(
                appId = credentials["bot_id"].orEmpty().ifBlank { current.appId },
                appSecret = credentials["secret"].orEmpty().ifBlank { current.appSecret },
            )
        }
        check(updated.isConfigured) { "Authorization response did not contain complete credentials" }
        configRepository.upsert(updated)
    }

    private fun update(kind: ChannelKind, state: ChannelBindingState) {
        mutableStates.update { states -> states.toMutableMap().apply { put(kind, state) } }
    }

    private companion object {
        const val BindingTimeoutMillis = 5 * 60_000L
        const val MaxConsecutivePollFailures = 4
    }
}

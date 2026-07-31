package com.zhousl.aether.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.data.PiProviderDefinition

@Composable
fun ProviderBrandIconBadge(
    provider: PiProviderDefinition,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 48.dp,
    iconSize: Dp = 30.dp,
    cornerRadius: Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(providerBrandIconRes(provider.id)),
            contentDescription = provider.displayName,
            modifier = Modifier.size(iconSize),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
internal fun ProviderBrandIcon(
    providerId: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(providerBrandIconRes(providerId)),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

internal fun providerBrandIconRes(providerId: String): DrawableResource = when (providerId) {
    "amazon-bedrock" -> Res.drawable.provider_amazon_bedrock
    "ant-ling" -> Res.drawable.provider_ant_ling
    "anthropic" -> Res.drawable.provider_anthropic
    "azure-openai-responses" -> Res.drawable.provider_azure_openai_responses
    "cerebras" -> Res.drawable.provider_cerebras
    "cloudflare-ai-gateway" -> Res.drawable.provider_cloudflare_ai_gateway
    "cloudflare-workers-ai" -> Res.drawable.provider_cloudflare_workers_ai
    "deepseek" -> Res.drawable.provider_deepseek
    "fireworks" -> Res.drawable.provider_fireworks
    "github-copilot" -> Res.drawable.provider_github_copilot
    "google" -> Res.drawable.provider_google
    "google-vertex" -> Res.drawable.provider_google_vertex
    "groq" -> Res.drawable.provider_groq
    "huggingface" -> Res.drawable.provider_huggingface
    "kimi-coding" -> Res.drawable.provider_kimi_coding
    "minimax" -> Res.drawable.provider_minimax
    "minimax-cn" -> Res.drawable.provider_minimax_cn
    "mistral" -> Res.drawable.provider_mistral
    "moonshotai" -> Res.drawable.provider_moonshotai
    "moonshotai-cn" -> Res.drawable.provider_moonshotai_cn
    "nvidia" -> Res.drawable.provider_nvidia
    "openai" -> Res.drawable.provider_openai
    "openai-codex" -> Res.drawable.provider_openai_codex
    "opencode" -> Res.drawable.provider_opencode
    "opencode-go" -> Res.drawable.provider_opencode_go
    "openrouter" -> Res.drawable.provider_openrouter
    "together" -> Res.drawable.provider_together
    "vercel-ai-gateway" -> Res.drawable.provider_vercel_ai_gateway
    "xai" -> Res.drawable.provider_xai
    "xiaomi" -> Res.drawable.provider_xiaomi
    "xiaomi-token-plan-ams" -> Res.drawable.provider_xiaomi_token_plan_ams
    "xiaomi-token-plan-cn" -> Res.drawable.provider_xiaomi_token_plan_cn
    "xiaomi-token-plan-sgp" -> Res.drawable.provider_xiaomi_token_plan_sgp
    "zai" -> Res.drawable.provider_zai
    "zai-coding-cn" -> Res.drawable.provider_zai_coding_cn
    else -> Res.drawable.provider_openai_compatible
}

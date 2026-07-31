package com.zhousl.aether.data

expect fun platformCurrentTimeMillis(): Long

expect fun platformUptimeMillis(): Long

expect fun platformRandomUuid(): String

expect fun platformLanguageTag(): String

expect fun platformDefaultSystemPrompt(): String

expect fun platformDefaultLlmUserAgent(): String

expect fun platformDynamicPromptValues(): Map<String, String>

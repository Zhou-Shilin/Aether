package com.zhousl.aether.ui

import android.content.Context
import androidx.annotation.StringRes

sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Resource(
        @StringRes val resId: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UiText
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> context.getString(resId, *formatArgs.toTypedArray())
}
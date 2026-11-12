package org.happycode.karoo.health_connect.adapters.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    data class StringResource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : UiText {
        constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
    }

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(resId, *args.toTypedArray())
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(resId, *args.toTypedArray())
    }
}

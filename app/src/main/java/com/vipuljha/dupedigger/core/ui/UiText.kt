package com.vipuljha.dupedigger.core.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** Text from resources without a `Context`; an argument that is itself a [UiText] is resolved first. */
sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Resource(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, args.toList())
    }
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.resolved(context))
    is UiText.Plural -> context.resources.getQuantityString(id, count, *args.resolved(context))
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.resolved())
    is UiText.Plural -> pluralStringResource(id, count, *args.resolved())
}

private fun List<Any>.resolved(context: Context): Array<Any> = map { if (it is UiText) it.asString(context) else it }.toTypedArray()

@Composable
private fun List<Any>.resolved(): Array<Any> = map { if (it is UiText) it.asString() else it }.toTypedArray()

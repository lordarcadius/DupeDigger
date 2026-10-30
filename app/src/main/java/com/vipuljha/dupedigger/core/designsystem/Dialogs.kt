package com.vipuljha.dupedigger.core.designsystem

import android.app.Dialog
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Alerts and confirmations: the PrimeOS blur card when the system UI revamp is on, otherwise the stock M3 dialog.
 * Enter runs [onEnter] (a no-op while the dialog is invalid) and Esc dismisses, from the moment it opens.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    onEnter: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) = CompositionLocalProvider(LocalContentBounds provides remember { mutableStateOf(IntRect.Zero) }) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            PrimeOsBlur.DressDialog()
            Box(Modifier.dialogKeys(onConfirm = onEnter, onDismiss = onDismissRequest)) { confirmButton() }
        },
        modifier = modifier.width(dialogWidth(Dimens.dialogWidth)),
        dismissButton = dismissButton,
        icon = icon,
        title = title?.let { t -> { Box(Modifier.padding(end = PrimeOsBlur.closeInset)) { t() } } },
        text = text,
        containerColor = PrimeOsBlur.container(AlertDialogDefaults.containerColor),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

/** Enter confirms and Esc dismisses; the node takes focus as the dialog opens and draws no indicator. */
@Composable
fun Modifier.dialogKeys(onConfirm: () -> Unit, onDismiss: () -> Unit, initialFocus: FocusRequester? = null): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { (initialFocus ?: focus).requestFocus() }
    return this
        .focusRequester(focus)
        .focusable()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> { onConfirm(); true }
                Key.Escape -> { onDismiss(); true }
                else -> false
            }
        }
}

@Composable
fun dialogWidth(cap: Dp): Dp {
    val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return minOf(cap, window - Dimens.dialogWindowMargin * 2)
}

/**
 * The PrimeOS blur card, read from internal framework classes by reflection; any other Android keeps the stock look.
 * Under the blur the card has no dim, shadow or border, and the framework's close button sits at its top end.
 */
object PrimeOsBlur {
    private const val PKG = "com.android.internal.util.primeos."
    private val DialogCorner = 28.dp

    val enabled: Boolean = reflect(false) {
        Class.forName(PKG + "PrimeUtils").getField("IS_PRIMEOS_SYSTEM_UI_REVAMP").getBoolean(null)
    }
    val closeInset: Dp = if (enabled) 24.dp else 0.dp

    fun container(stock: Color): Color = if (enabled) Color.Transparent else stock

    @Composable
    fun DressDialog(closeButton: Boolean = true) {
        if (!enabled) return
        val view = LocalView.current
        val radius = with(LocalDensity.current) { DialogCorner.toPx() }
        val fallback = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                dressCard(view, radius, fallback)
                if (closeButton) attachCloseButton(window, view)
            }
            onDispose {}
        }
    }

    private fun dressCard(card: View, radius: Float, fallback: Int) {
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        card.clipToOutline = true
        val blurred = reflect(false) {
            val config = Class.forName(PKG + "PrimeBlurConfig")
            val fill = config.getMethod("fillColor", Context::class.java).invoke(null, card.context)
            card.background = null
            Class.forName(PKG + "PrimeRealBlur")
                .getMethod("installStyled", View::class.java, Float::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(null, card, radius, fill, config.getField("CONTENT_TEXT").getInt(null))
            true
        }
        if (!blurred) card.background = GradientDrawable().apply { setColor(fallback); cornerRadius = radius }
    }

    private fun attachCloseButton(window: Window, card: View) {
        val dialog = window.callback as? Dialog ?: return
        val host = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        reflect(Unit) {
            Class.forName(PKG + "PrimeDialogStyle")
                .getMethod("attachCloseButton", ViewGroup::class.java, View::class.java, Runnable::class.java)
                .invoke(null, host, card, Runnable { @Suppress("DEPRECATION") dialog.onBackPressed() })
        }
    }

    private inline fun <T> reflect(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: ReflectiveOperationException) {
            fallback
        } catch (e: RuntimeException) {
            fallback
        } catch (e: LinkageError) {
            fallback
        }
}

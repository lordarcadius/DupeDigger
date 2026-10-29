package com.vipuljha.dupedigger.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.PaneMetrics
import com.vipuljha.dupedigger.core.designsystem.WindowBreakpoints

enum class PaneRole { List, Detail }

private const val PANE_ROLE = "paneRole"

fun paneRole(role: PaneRole): Map<String, Any> = mapOf(PANE_ROLE to role)

/** Indices of the list entry and its detail entry (null when none is open), or null when the stack doesn't end in a list. */
internal fun listDetailIndices(roles: List<PaneRole?>): Pair<Int, Int?>? {
    val last = roles.lastIndex
    return when {
        last < 0 -> null
        roles[last] == PaneRole.List -> last to null
        roles[last] == PaneRole.Detail && last > 0 && roles[last - 1] == PaneRole.List -> last - 1 to last
        else -> null
    }
}

/** Shows a list entry beside its detail when [twoPane]; everything else falls back to one pane. */
class ListDetailSceneStrategy<T : Any>(
    private val twoPane: Boolean,
    private val emptyDetail: @Composable () -> Unit,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!twoPane) return null
        val (list, detail) = listDetailIndices(entries.map { it.metadata[PANE_ROLE] as? PaneRole }) ?: return null
        return ListDetailScene(entries[list], detail?.let(entries::get), entries.dropLast(1), emptyDetail)
    }
}

/** Keyed on the list entry, so changing the detail swaps one pane instead of animating the whole scene. */
private class ListDetailScene<T : Any>(
    private val list: NavEntry<T>,
    private val detail: NavEntry<T>?,
    override val previousEntries: List<NavEntry<T>>,
    private val emptyDetail: @Composable () -> Unit,
) : Scene<T> {
    override val key: Any = list.contentKey
    override val entries: List<NavEntry<T>> = listOfNotNull(list, detail)
    override val metadata: Map<String, Any> = list.metadata

    override val content: @Composable () -> Unit = {
        val parent = LocalPaneMetrics.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val listWidth = (this.maxWidth * ListFraction).coerceIn(ListMinWidth, ListMaxWidth)
            Row(Modifier.fillMaxSize()) {
                SubPane(parent, listWidth, Modifier.width(listWidth)) { list.Content() }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.hairline)
                val detailWidth = this@BoxWithConstraints.maxWidth - listWidth - Dimens.hairline
                SubPane(parent, detailWidth, Modifier.width(detailWidth)) { detail?.Content() ?: emptyDetail() }
            }
        }
    }

    override fun equals(other: Any?) =
        other is ListDetailScene<*> && list == other.list && detail == other.detail && previousEntries == other.previousEntries

    override fun hashCode() = 31 * (31 * list.hashCode() + detail.hashCode()) + previousEntries.hashCode()
}

@Composable
private fun SubPane(parent: PaneMetrics, width: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    val metrics = parent.copy(
        width = width,
        gutter = if (width < WindowBreakpoints.Medium) Dimens.gutterCompact else Dimens.gutterMedium,
        actionsCompact = width < WindowBreakpoints.Medium,
    )
    CompositionLocalProvider(LocalPaneMetrics provides metrics) {
        Box(modifier.fillMaxHeight()) { content() }
    }
}

private const val ListFraction = 0.45f
private val ListMinWidth = 300.dp
private val ListMaxWidth = 420.dp

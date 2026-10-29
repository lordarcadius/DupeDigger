package com.vipuljha.dupedigger.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.presentation.group.GroupDetailUi
import com.vipuljha.dupedigger.presentation.group.GroupScreen
import com.vipuljha.dupedigger.presentation.group.GroupState
import com.vipuljha.dupedigger.presentation.group.MemberUi
import com.vipuljha.dupedigger.presentation.results.GroupRowUi
import com.vipuljha.dupedigger.presentation.results.KindChipUi
import com.vipuljha.dupedigger.presentation.results.ResultsScreen
import com.vipuljha.dupedigger.presentation.results.ResultsState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility checks on Results and Group detail, at the default font size and at 200%:
 * - every clickable control has something for TalkBack to read, and a 48dp touch target when fully on screen;
 * - the controls a card or row needs stay on screen at 200%.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule val rule = createComposeRule()

    @Test fun resultsAtDefaultFont() = checkResults(1f)

    @Test fun resultsAtDoubleFont() = checkResults(2f)

    @Test fun groupAtDefaultFont() = checkGroup(1f)

    @Test fun groupAtDoubleFont() = checkGroup(2f)

    private fun checkResults(fontScale: Float) {
        show(fontScale) {
            val groups = remember { flowOf(PagingData.from(rows)) }.collectAsLazyPagingItems()
            ResultsScreen(
                state = ResultsState(
                    kindChips = persistentListOf(
                        KindChipUi(null, UiText.Raw("All · 3")),
                        KindChipUi(MediaKind.IMAGE, UiText.Raw("Photos · 2")),
                    ),
                    selectionSummary = UiText.Raw("1 file selected · 2.1 MB"),
                ),
                groups = groups,
                onIntent = {},
                openGroup = rows.first().fingerprint,
                onOpenGroup = {},
                snackbarHostState = remember { SnackbarHostState() },
                onMenuClick = {},
            )
        }
        assertControlsAccessible()
        rule.onAllNodesWithText("Frees 2.1 MB", useUnmergedTree = true).onFirst().assertIsDisplayed()
    }

    private fun checkGroup(fontScale: Float) {
        show(fontScale) {
            GroupScreen(
                state = GroupState(content = ContentState.Success(detail)),
                onIntent = {},
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
            )
        }
        assertControlsAccessible()
        rule.onAllNodesWithText("Keep this").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("Compare").onFirst().assertIsDisplayed()
    }

    private fun assertControlsAccessible() {
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        nodes.indices.forEach { i ->
            val node = nodes[i]
            val minPx = with(node.layoutInfo.density) { MinTouch.toPx() }
            val clipped = node.boundsInRoot.width < node.size.width - 1 || node.boundsInRoot.height < node.size.height - 1
            val touch = node.touchBoundsInRoot
            assertTrue("Touch target ${touch.size} under 48dp: ${node.config}", clipped || (touch.width >= minPx - 1 && touch.height >= minPx - 1))
            val config = node.config
            val label = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + config.getOrNull(SemanticsProperties.Text).orEmpty()
            assertTrue("Control without a label: $config", label.any { it.isNotBlank() })
        }
    }

    private fun show(fontScale: Float, content: @Composable () -> Unit) = rule.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) { DupeDiggerTheme { content() } }
    }

    private val rows = List(3) { i ->
        GroupRowUi(
            groupId = i.toLong(),
            fingerprint = "fp$i",
            kind = if (i == 2) MediaKind.DOCUMENT else MediaKind.IMAGE,
            title = "IMG_2026102$i.jpg",
            detail = UiText.Raw("2 copies · 2.1 MB · Exact"),
            reclaim = UiText.Raw("Frees 2.1 MB"),
            tierTag = if (i == 1) UiText.Raw("Similar · 96%") else null,
            removable = persistentMapOf(10L + i to 2_100_000L),
            selected = i == 0,
        )
    }

    private val detail = GroupDetailUi(
        title = "IMG_20261020.jpg",
        summary = UiText.Raw("3 files · frees 4.2 MB"),
        similar = true,
        members = persistentListOf(
            member(1, keeper = true),
            member(2, keeper = false),
            member(3, keeper = false),
        ),
    )

    private fun member(id: Long, keeper: Boolean) = MemberUi(
        fileId = id,
        kind = MediaKind.DOCUMENT,
        name = "Quarterly report final version $id.pdf",
        location = UiText.Raw("Internal storage › Documents › Work"),
        details = UiText.Raw("2.1 MB · 29 Oct 2026"),
        isKeeper = keeper,
        thumbnail = null,
        comparable = !keeper,
    )

    private companion object {
        val MinTouch = 48.dp
    }
}

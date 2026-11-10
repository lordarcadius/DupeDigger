package com.vipuljha.dupedigger.presentation.scan

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.data.worker.InMemorySemanticStageSkip
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanPreset
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanPresetRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import com.vipuljha.dupedigger.domain.usecase.AddScanFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.CancelScanUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteScanPresetUseCase
import com.vipuljha.dupedigger.domain.usecase.GetAvailableScopesUseCase
import com.vipuljha.dupedigger.domain.usecase.ObserveScanPresetsUseCase
import com.vipuljha.dupedigger.domain.usecase.SaveScanPresetUseCase
import com.vipuljha.dupedigger.domain.usecase.SetNearDuplicatesEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SkipSemanticStageUseCase
import com.vipuljha.dupedigger.domain.usecase.StartScanUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/** The scan-start flow on the real Scan screen and ViewModel; storage, settings and the scheduler are faked. */
@RunWith(AndroidJUnit4::class)
class ScanStartFlowTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val started = Collections.synchronizedList(mutableListOf<Pair<ScanScope, Set<MediaKind>>>())
    private val presets = MutableStateFlow<List<ScanPreset>>(emptyList())

    @Before
    fun setUp() {
        val settings = object : SettingsRepository {
            private val flow = MutableStateFlow(AppSettings())
            override val settings: Flow<AppSettings> = flow
            override suspend fun update(transform: (AppSettings) -> AppSettings) = flow.update(transform)
        }
        val runs = object : ScanRunRepository {
            override suspend fun start(scope: ScanScope) = 1L
            override suspend fun finish(scanId: Long, status: ScanStatus, summary: ScanSummary?) = Unit
            override fun observeLatest(): Flow<ScanRunInfo?> = flowOf(null)
        }
        val scheduler = object : ScanScheduler {
            override fun start(scope: ScanScope, kinds: Set<MediaKind>) {
                started += scope to kinds
            }
            override fun cancel() = Unit
            override fun observeProgress(): Flow<ScanProgress?> = flowOf(null)
        }
        val access = object : StorageAccess {
            override fun current() = StorageAccessState(MediaAccess.FULL, allFiles = true)
            override val mediaPermissions = emptyList<String>()
        }
        val folders = object : ScanFolderRepository {
            override val folders: Flow<ImmutableList<ScanFolder>> = flowOf(persistentListOf())
            override suspend fun add(treeUri: String) = Outcome.Success(Unit)
            override suspend fun remove(treeUri: String) = Unit
        }
        val presetRepository = object : ScanPresetRepository {
            override val presets: Flow<ImmutableList<ScanPreset>> = flowOf(persistentListOf())
            override suspend fun save(preset: ScanPreset): Long {
                this@ScanStartFlowTest.presets.update { it + preset.copy(id = 1) }
                return 1
            }
            override suspend fun delete(id: Long) = Unit
        }
        val skip = InMemorySemanticStageSkip()
        val viewModel = ScanViewModel(
            settings, runs, scheduler, ObserveScanPresetsUseCase(presetRepository),
            GetAvailableScopesUseCase(access, folders), StartScanUseCase(access, folders, scheduler), CancelScanUseCase(scheduler),
            SetNearDuplicatesEnabledUseCase(settings), SetSemanticEnabledUseCase(settings),
            SaveScanPresetUseCase(presetRepository), DeleteScanPresetUseCase(presetRepository), AddScanFolderUseCase(folders),
            SkipSemanticStageUseCase(skip), skip,
        )
        rule.setContent {
            DupeDiggerTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ScanScreen(state, { null }, viewModel::onIntent, remember { SnackbarHostState() }, onBack = {})
            }
        }
    }

    private fun text(id: Int) = context.getString(id)

    private fun click(label: String) {
        rule.onNodeWithTag(ScanTestTags.setupList).performScrollToNode(hasText(label))
        rule.onNodeWithText(label).performClick()
    }

    @Test
    fun withNoPresetItAsksForFileTypesThenWhereToLook() {
        rule.onNodeWithText(text(R.string.scan_presets_title)).assertExists()
        rule.onNodeWithText(text(R.string.scan_preset_none)).assertExists()
        rule.onNodeWithTag(ScanTestTags.setupList).performScrollToNode(hasText(text(R.string.preset_whatsapp)))

        click(text(R.string.scan_action_customise))
        rule.onNodeWithText(text(R.string.scan_types_title)).assertExists()
        click(text(R.string.kind_videos))
        click(text(R.string.scan_action_next))

        rule.onNodeWithText(text(R.string.scan_where_title)).assertExists()
        click(text(R.string.scope_media))
        click(text(R.string.scan_action_start))

        rule.waitUntil { started.isNotEmpty() }
        assertEquals(ScanScope.MediaOnly to setOf(MediaKind.IMAGE, MediaKind.AUDIO, MediaKind.DOCUMENT, MediaKind.OTHER), started.single())
    }

    /** The common case is one tap: no preset, every file type, the widest scope the device allows. */
    @Test
    fun withNoPresetStartScansEverythingFromTheFirstStep() {
        rule.onNodeWithText(text(R.string.scan_preset_none)).assertExists()

        click(text(R.string.scan_action_start))

        rule.waitUntil { started.isNotEmpty() }
        assertEquals(ScanScope.WholeDevice to AllMediaKinds, started.single())
    }

    @Test
    fun aChosenPresetStartsStraightAway() {
        click(text(R.string.preset_screenshots))
        click(text(R.string.scan_action_start))

        rule.waitUntil { started.isNotEmpty() }
        assertEquals(ScanScope.Paths(persistentListOf("Pictures/Screenshots", "DCIM/Screenshots")), started.single().first)
    }

    @Test
    fun aNewPresetIsNamedAndSavedOnTheLastStep() {
        click(text(R.string.scan_action_new_preset))
        click(text(R.string.kind_images))
        click(text(R.string.scan_action_next))
        rule.onNode(hasSetTextAction()).performTextInput("Mine")
        click(text(R.string.scan_action_save_preset))

        rule.waitUntil { presets.value.isNotEmpty() }
        assertEquals("Mine", presets.value.single().name)
        assertEquals(setOf(MediaKind.VIDEO, MediaKind.AUDIO, MediaKind.DOCUMENT, MediaKind.OTHER), presets.value.single().kinds)
    }
}

package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.presentation.FakeFolders
import com.vipuljha.dupedigger.presentation.FakeScheduler
import com.vipuljha.dupedigger.presentation.FakeSettings
import com.vipuljha.dupedigger.presentation.FakeStorageAccess
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanUseCasesTest {
    private val access = FakeStorageAccess(StorageAccessState(MediaAccess.NONE, allFiles = false))
    private val folders = FakeFolders()
    private val scheduler = FakeScheduler()
    private val settings = FakeSettings()
    private val camera = ScanFolder("content://tree/camera", "Camera", "/storage/emulated/0/DCIM/Camera")

    @Test
    fun `available scopes follow access and picked folders, widest first`() = runTest {
        val scopes = GetAvailableScopesUseCase(access, folders)
        assertEquals(emptyList<ScanScope>(), scopes())

        access.state = StorageAccessState(MediaAccess.PARTIAL, allFiles = true)
        folders.folders.value = persistentListOf(camera)
        assertEquals(listOf(ScanScope.WholeDevice, ScanScope.MediaOnly, ScanScope.Folders(persistentListOf(camera))), scopes())
    }

    @Test
    fun `an unreadable scope is refused and never queued`() = runTest {
        val start = StartScanUseCase(access, folders, scheduler)

        assertEquals(Outcome.Failure(DomainError.PermissionDenied), start(ScanScope.WholeDevice))
        assertEquals(Outcome.Failure(DomainError.PermissionDenied), start(ScanScope.Folders(persistentListOf(camera))))
        assertTrue(scheduler.started.isEmpty())
    }

    @Test
    fun `a readable scope is queued`() = runTest {
        access.state = StorageAccessState(MediaAccess.FULL, allFiles = false)
        folders.folders.value = persistentListOf(camera)
        val start = StartScanUseCase(access, folders, scheduler)

        assertEquals(Outcome.Success(Unit), start(ScanScope.MediaOnly))
        assertEquals(Outcome.Success(Unit), start(ScanScope.Folders(persistentListOf(camera))))
        assertEquals(listOf(ScanScope.MediaOnly, ScanScope.Folders(persistentListOf(camera))), scheduler.started)
    }

    @Test
    fun `cancel stops the scan`() {
        CancelScanUseCase(scheduler)()
        assertTrue(scheduler.cancelled)
    }

    @Test
    fun `similar-file settings are saved, the threshold within its choices`() = runTest {
        SetNearDuplicatesEnabledUseCase(settings)(false)
        SetNearThresholdUseCase(settings)(99)
        assertFalse(settings.settings.value.nearDuplicatesEnabled)
        assertEquals(AppSettings.NearThresholdChoices.last, settings.settings.value.nearHammingThreshold)

        SetNearThresholdUseCase(settings)(6)
        assertEquals(6, settings.settings.value.nearHammingThreshold)
    }
}

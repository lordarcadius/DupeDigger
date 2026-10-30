package com.vipuljha.dupedigger.presentation.onboarding

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import com.vipuljha.dupedigger.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private class FakeStorageAccess(var state: StorageAccessState) : StorageAccess {
        override fun current() = state
        override val mediaPermissions = listOf("perm.images", "perm.video")
    }

    private class FakeSettings : SettingsRepository {
        val flow = MutableStateFlow(AppSettings())
        override val settings = flow
        override suspend fun update(transform: (AppSettings) -> AppSettings) = flow.update(transform)
    }

    private val access = FakeStorageAccess(StorageAccessState(MediaAccess.NONE, allFiles = false))
    private val settings = FakeSettings()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = OnboardingViewModel(access, CompleteOnboardingUseCase(settings))

    @Test
    fun `no access asks for the media permissions`() = runTest {
        val vm = viewModel()
        assertEquals(MediaAction.Allow, vm.state.value.mediaAction)
        assertEquals(UiText.res(R.string.onboarding_status_not_allowed), vm.state.value.mediaStatus)

        vm.onIntent(OnboardingIntent.MediaActionClicked)

        val effect = vm.effect.first() as OnboardingEffect.RequestMediaPermissions
        assertEquals(listOf("perm.images", "perm.video"), effect.permissions)
    }

    @Test
    fun `a denial that can be asked again keeps offering the prompt`() {
        val vm = viewModel()
        vm.onIntent(OnboardingIntent.MediaPermissionResult(canAskAgain = true))
        assertEquals(MediaAction.Allow, vm.state.value.mediaAction)
    }

    @Test
    fun `a permanent denial sends the user to app settings`() = runTest {
        val vm = viewModel()
        vm.onIntent(OnboardingIntent.MediaPermissionResult(canAskAgain = false))
        assertEquals(MediaAction.OpenSettings, vm.state.value.mediaAction)
        assertEquals(UiText.res(R.string.onboarding_status_blocked), vm.state.value.mediaStatus)

        vm.onIntent(OnboardingIntent.MediaActionClicked)
        assertEquals(OnboardingEffect.OpenAppSettings, vm.effect.first())
    }

    @Test
    fun `granting in settings clears the block on resume`() {
        val vm = viewModel()
        vm.onIntent(OnboardingIntent.MediaPermissionResult(canAskAgain = false))

        access.state = access.state.copy(media = MediaAccess.FULL)
        vm.onIntent(OnboardingIntent.Refresh)

        assertEquals(MediaAction.None, vm.state.value.mediaAction)
        assertFalse(vm.state.value.mediaBlocked)
        assertEquals(UiText.res(R.string.onboarding_status_allowed), vm.state.value.mediaStatus)
    }

    @Test
    fun `partial access offers allow all`() {
        access.state = access.state.copy(media = MediaAccess.PARTIAL)
        val vm = viewModel()
        assertEquals(MediaAction.AllowAll, vm.state.value.mediaAction)
        assertEquals(UiText.res(R.string.onboarding_status_partial), vm.state.value.mediaStatus)
    }

    @Test
    fun `deep scan opens all files settings until it is on`() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.value.allFilesActionVisible)
        vm.onIntent(OnboardingIntent.AllFilesClicked)
        assertEquals(OnboardingEffect.OpenAllFilesAccessSettings, vm.effect.first())

        access.state = access.state.copy(allFiles = true)
        vm.onIntent(OnboardingIntent.Refresh)
        assertFalse(vm.state.value.allFilesActionVisible)
        assertEquals(UiText.res(R.string.onboarding_status_on), vm.state.value.allFilesStatus)
    }

    @Test
    fun `continue completes onboarding even without access`() {
        val vm = viewModel()
        vm.onIntent(OnboardingIntent.ContinueClicked)
        assertTrue(vm.state.value.isFinishing)
        assertTrue(settings.flow.value.onboardingCompleted)
    }
}

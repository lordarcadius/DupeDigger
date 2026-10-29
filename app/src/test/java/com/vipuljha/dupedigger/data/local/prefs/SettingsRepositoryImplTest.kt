package com.vipuljha.dupedigger.data.local.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vipuljha.dupedigger.domain.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository(file: File = folder.newFile("settings.preferences_pb").also { it.delete() }) =
        SettingsRepositoryImpl(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })

    @Test
    fun `a fresh store reads the defaults`() = runTest {
        assertEquals(AppSettings(), repository().settings.first())
    }

    @Test
    fun `updates are persisted`() = runTest {
        val repo = repository()
        repo.update { it.copy(semanticEnabled = true, nearHammingThreshold = 6, minFileSizeBytes = 0) }

        assertEquals(AppSettings(semanticEnabled = true, nearHammingThreshold = 6, minFileSizeBytes = 0), repo.settings.first())
    }

    @Test
    fun `out-of-range values are clamped`() = runTest {
        val repo = repository()
        repo.update { it.copy(nearHammingThreshold = 99, semanticThreshold = 1.5f, minFileSizeBytes = -4) }

        val settings = repo.settings.first()
        assertEquals(64, settings.nearHammingThreshold)
        assertEquals(1f, settings.semanticThreshold)
        assertEquals(0L, settings.minFileSizeBytes)
    }
}

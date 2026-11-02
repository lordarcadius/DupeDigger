package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPresetTest {

    @Test
    fun `every requested app has a built-in preset that can't be deleted`() {
        assertEquals(
            setOf("WHATSAPP", "CAMERA", "TELEGRAM", "SCREENSHOTS", "FACEBOOK", "INSTAGRAM", "SNAPCHAT", "REDDIT"),
            BuiltInPreset.presets.map { it.builtIn!!.name }.toSet(),
        )
        assertTrue(BuiltInPreset.presets.none { it.deletable })
    }

    @Test
    fun `built-in presets have distinct negative ids and walk usable folders`() {
        val ids = BuiltInPreset.presets.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it < 0 })
        BuiltInPreset.presets.forEach { preset ->
            val scope = preset.scope as ScanScope.Paths
            assertEquals(preset.builtIn!!.paths.size, scope.roots().size)
            assertTrue(preset.kinds.isNotEmpty())
        }
    }

    @Test
    fun `the ticket's example folders are in their presets`() {
        fun pathsOf(preset: BuiltInPreset) = (preset.toPreset().scope as ScanScope.Paths).paths
        assertTrue("Android/media/com.whatsapp/WhatsApp/Media" in pathsOf(BuiltInPreset.WHATSAPP))
        assertTrue("DCIM/Camera" in pathsOf(BuiltInPreset.CAMERA))
        assertTrue("Pictures/Screenshots" in pathsOf(BuiltInPreset.SCREENSHOTS))
        assertTrue("Telegram" in pathsOf(BuiltInPreset.TELEGRAM))
    }

    @Test
    fun `a user preset can be deleted and needs a file type`() {
        val images = ScanPreset(1, "Images Only", ScanScope.WholeDevice, persistentSetOf(MediaKind.IMAGE))
        assertTrue(images.deletable)
        val error = runCatching { ScanPreset(2, "Nothing", ScanScope.Paths(persistentListOf("DCIM")), persistentSetOf()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

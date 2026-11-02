package com.vipuljha.dupedigger.data.repository

import com.vipuljha.dupedigger.data.local.db.entity.ScanPresetEntity
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanPreset
import com.vipuljha.dupedigger.domain.model.ScanScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanPresetMappingTest {
    private val camera = ScanFolder("content://tree/camera", "Camera", "/storage/emulated/0/DCIM/Camera")
    private val music = ScanFolder("content://tree/music", "Music", null)

    @Test
    fun `every scope and its file types survive a save and a read`() {
        listOf(
            ScanScope.WholeDevice,
            ScanScope.MediaOnly,
            ScanScope.Folders(persistentListOf(camera, music)),
            ScanScope.Paths(persistentListOf("DCIM/Camera", "WhatsApp Business/Media")),
        ).forEach { scope ->
            val preset = ScanPreset(7, "Mine", scope, persistentSetOf(MediaKind.VIDEO, MediaKind.DOCUMENT))
            assertEquals(preset, preset.toEntity(createdAt = 1).toDomain(listOf(camera, music)))
        }
    }

    @Test
    fun `a folder whose grant was released drops out of the preset`() {
        val preset = ScanPreset(7, "Mine", ScanScope.Folders(persistentListOf(camera, music)), AllMediaKinds)

        val read = preset.toEntity(createdAt = 1).toDomain(listOf(music))

        assertEquals(ScanScope.Folders(persistentListOf(music)), read?.scope)
    }

    @Test
    fun `unknown stored values read safely`() {
        val row = ScanPresetEntity(7, "Mine", scopeKind = "WHOLE_DEVICE", locations = "", mediaKinds = "HOLOGRAM", createdAt = 1)
        assertEquals(AllMediaKinds, row.toDomain(emptyList())?.kinds)
        assertNull(row.copy(scopeKind = "SOMETHING_NEW").toDomain(emptyList()))
    }
}

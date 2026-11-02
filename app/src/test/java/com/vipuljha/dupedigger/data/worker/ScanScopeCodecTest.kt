package com.vipuljha.dupedigger.data.worker

import androidx.work.workDataOf
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScopeKind
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanScopeCodecTest {
    private val camera = ScanFolder("content://tree/camera", "Camera", null)
    private val music = ScanFolder("content://tree/music", "Music", null)

    @Test
    fun `every scope and its file types survive the round trip`() {
        val scopes = listOf(
            ScanScope.WholeDevice,
            ScanScope.MediaOnly,
            ScanScope.Folders(persistentListOf(camera, music)),
            ScanScope.Paths(persistentListOf("DCIM/Camera", "Android/media/com.whatsapp/WhatsApp/Media")),
        )
        val kinds = setOf(MediaKind.IMAGE, MediaKind.VIDEO)
        scopes.forEach { scope ->
            val data = ScanScopeCodec.toData(scope, kinds)
            assertEquals(scope, ScanScopeCodec.fromData(data, listOf(camera, music)))
            assertEquals(kinds, ScanScopeCodec.mediaKindsFromData(data))
        }
    }

    @Test
    fun `work queued without file types searches every type`() {
        val data = workDataOf("scope_kind" to "WHOLE_DEVICE")
        assertEquals(ScanScope.WholeDevice, ScanScopeCodec.fromData(data, emptyList()))
        assertEquals(AllMediaKinds, ScanScopeCodec.mediaKindsFromData(data))
        assertEquals(AllMediaKinds, ScanScopeCodec.mediaKindsFromData(workDataOf("media_kinds" to arrayOf("NOT_A_KIND"))))
    }

    @Test
    fun `a released folder grant drops the folder and unknown input reads as nothing`() {
        val data = ScanScopeCodec.toData(ScanScope.Folders(persistentListOf(camera, music)), AllMediaKinds)
        assertEquals(ScanScope.Folders(persistentListOf(music)), ScanScopeCodec.fromData(data, listOf(music)))
        assertNull(ScanScopeCodec.fromData(workDataOf("scope_kind" to "SOMETHING_NEW"), emptyList()))
    }

    @Test
    fun `a paths scope keys its run by its cleaned folders`() {
        val key = ScanScopeCodec.key(ScanScope.Paths(persistentListOf("Pictures/Screenshots/", "DCIM/Camera", "dcim/camera/sub")))
        assertEquals("PATHS:DCIM/Camera|Pictures/Screenshots", key)
        assertEquals(ScopeKind.PATHS, ScanScopeCodec.kindOf(key))
    }
}

package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanScopeTest {
    private val folder = ScanFolder("content://tree/1", "Camera", "/storage/emulated/0/DCIM/Camera")

    @Test
    fun `nothing is offered without any access or folders`() {
        assertTrue(availableScopes(StorageAccessState(MediaAccess.NONE, allFiles = false), emptyList()).isEmpty())
    }

    @Test
    fun `every reachable scope is offered widest first`() {
        val scopes = availableScopes(StorageAccessState(MediaAccess.FULL, allFiles = true), listOf(folder))
        assertEquals(
            listOf(ScanScope.WholeDevice, ScanScope.MediaOnly, ScanScope.Folders(persistentListOf(folder))),
            scopes,
        )
    }

    @Test
    fun `partial media access still offers media and folders work without permissions`() {
        assertEquals(listOf(ScanScope.MediaOnly), availableScopes(StorageAccessState(MediaAccess.PARTIAL, false), emptyList()))
        assertEquals(
            listOf(ScanScope.Folders(persistentListOf(folder))),
            availableScopes(StorageAccessState(MediaAccess.NONE, false), listOf(folder)),
        )
    }

    @Test
    fun `a scope stops being reachable when its access or folder grant is gone`() {
        val none = StorageAccessState(MediaAccess.NONE, allFiles = false)
        assertTrue(ScanScope.MediaOnly.isReachable(StorageAccessState(MediaAccess.PARTIAL, false), emptyList()))
        assertFalse(ScanScope.MediaOnly.isReachable(none, emptyList()))
        assertFalse(ScanScope.WholeDevice.isReachable(StorageAccessState(MediaAccess.FULL, allFiles = false), emptyList()))
        assertTrue(ScanScope.Folders(persistentListOf(folder)).isReachable(none, listOf(folder)))
        assertFalse(ScanScope.Folders(persistentListOf(folder)).isReachable(none, emptyList()))
        assertFalse(ScanScope.Folders(persistentListOf()).isReachable(none, listOf(folder)))
    }

    @Test
    fun `paths need all files access and at least one usable folder`() {
        val camera = ScanScope.Paths(persistentListOf("DCIM/Camera"))
        assertTrue(camera.isReachable(StorageAccessState(MediaAccess.NONE, allFiles = true), emptyList()))
        assertFalse(camera.isReachable(StorageAccessState(MediaAccess.FULL, allFiles = false), emptyList()))
        assertFalse(ScanScope.Paths(persistentListOf("/", "..", "a/../b")).isReachable(StorageAccessState(MediaAccess.FULL, true), emptyList()))
    }

    @Test
    fun `path roots are cleaned and never overlap`() {
        val scope = ScanScope.Paths(
            persistentListOf("/DCIM//Camera/", "Pictures/Screenshots", "dcim/camera/sub", "DCIM/camera", "./x", "Android/../data", "Telegram"),
        )
        assertEquals(listOf("Telegram", "DCIM/Camera", "Pictures/Screenshots"), scope.roots())
    }
}

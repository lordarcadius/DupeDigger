package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
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
}

package com.vipuljha.dupedigger.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalStoragePathsTest {

    @Test
    fun `primary volume maps to emulated storage`() {
        assertEquals("/storage/emulated/0/DCIM/Camera", externalStorageDocIdToPath("primary:DCIM/Camera"))
        assertEquals("/storage/emulated/0", externalStorageDocIdToPath("primary:"))
    }

    @Test
    fun `other volumes map to their mount point`() {
        assertEquals("/storage/1A2B-3C4D/Music", externalStorageDocIdToPath("1A2B-3C4D:Music/"))
    }

    @Test
    fun `ids without a volume are rejected`() {
        assertNull(externalStorageDocIdToPath("raw/storage"))
        assertNull(externalStorageDocIdToPath(":DCIM"))
    }
}

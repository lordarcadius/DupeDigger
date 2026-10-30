package com.vipuljha.dupedigger.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFilterTest {

    @Test
    fun `other apps' private folders are always skipped`() {
        val filter = ScanFilter(emptyList(), skipHidden = false)
        assertTrue(filter.skips("/storage/emulated/0/Android/data", "data"))
        assertTrue(filter.skips("/storage/emulated/0/Android/obb/com.game/main.obb", "main.obb"))
        assertTrue(filter.skips("/storage/1A2B-3C4D/Android/data/x", "x"))
        assertFalse(filter.skips("/storage/emulated/0/Android/media/com.whatsapp/a.jpg", "a.jpg"))
        assertFalse(filter.skips("/storage/emulated/0/Pictures/Android/data/a.jpg", "a.jpg"))
    }

    @Test
    fun `whatsapp media under Android media is kept with the default filter`() {
        val filter = ScanFilter(emptyList(), skipHidden = true)
        val media = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media"
        assertFalse(filter.skips("/storage/emulated/0/Android/media", "media"))
        assertFalse(filter.skips("/storage/emulated/0/Android/media/com.whatsapp", "com.whatsapp"))
        assertFalse(filter.skips("$media/WhatsApp Images/IMG-20260101-WA0001.jpg", "IMG-20260101-WA0001.jpg"))
        assertFalse(filter.skips("$media/WhatsApp Video/Sent/VID-20260101-WA0002.mp4", "VID-20260101-WA0002.mp4"))
        assertFalse(filter.skips("/storage/1A2B-3C4D/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/a.pdf", "a.pdf"))
        assertTrue(filter.skips("/storage/emulated/0/Android/data/com.whatsapp/files/a.jpg", "a.jpg"))
        assertTrue(filter.skips("$media/.Statuses/a.jpg", "a.jpg"))
    }

    @Test
    fun `an exclusion covers the folder and its contents but not a sibling with the same prefix`() {
        val filter = ScanFilter(listOf("/storage/emulated/0/Private/"), skipHidden = false)
        assertTrue(filter.skips("/storage/emulated/0/Private", "Private"))
        assertTrue(filter.skips("/storage/emulated/0/Private/x/y.jpg", "y.jpg"))
        assertFalse(filter.skips("/storage/emulated/0/PrivateNotes/y.jpg", "y.jpg"))
    }

    @Test
    fun `hidden entries are skipped only when asked`() {
        assertTrue(ScanFilter(emptyList(), skipHidden = true).skips("/storage/emulated/0/.thumbs/a.jpg", "a.jpg"))
        assertTrue(ScanFilter(emptyList(), skipHidden = true).skips(null, ".nomedia"))
        assertFalse(ScanFilter(emptyList(), skipHidden = false).skips("/storage/emulated/0/.thumbs/a.jpg", "a.jpg"))
    }

    @Test
    fun `media kind comes from the mime type`() {
        assertEquals(MediaKind.IMAGE, mediaKindOf("image/jpeg"))
        assertEquals(MediaKind.VIDEO, mediaKindOf("video/mp4"))
        assertEquals(MediaKind.AUDIO, mediaKindOf("audio/mpeg"))
        assertEquals(MediaKind.DOCUMENT, mediaKindOf("application/pdf"))
        assertEquals(MediaKind.DOCUMENT, mediaKindOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(MediaKind.DOCUMENT, mediaKindOf("text/plain"))
        assertEquals(MediaKind.OTHER, mediaKindOf("application/zip"))
        assertEquals(MediaKind.OTHER, mediaKindOf(null))
    }
}

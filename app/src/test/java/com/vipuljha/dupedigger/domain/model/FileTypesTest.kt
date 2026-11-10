package com.vipuljha.dupedigger.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileTypesTest {

    @Test
    fun `the extension is the lowercase tail, and a dotfile has none`() {
        assertEquals("heic", extensionOf("IMG_0001.HEIC"))
        assertEquals("gz", extensionOf("archive.tar.gz"))
        assertEquals("", extensionOf("README"))
        assertEquals("", extensionOf(".gitignore"))
        assertEquals("", extensionOf("trailing."))
    }

    @Test
    fun `formats the platform doesn't know are still placed by name`() {
        assertEquals(MediaKind.IMAGE, mediaKindForName("IMG_0001.HEIC"))
        assertEquals(MediaKind.IMAGE, mediaKindForName("shot.avif"))
        assertEquals(MediaKind.IMAGE, mediaKindForName("raw.dng"))
        assertEquals(MediaKind.VIDEO, mediaKindForName("clip.mkv"))
        assertEquals(MediaKind.AUDIO, mediaKindForName("song.opus"))
        assertEquals(MediaKind.DOCUMENT, mediaKindForName("notes.md"))
        assertEquals(MediaKind.OTHER, mediaKindForName("backup.zip"))
        assertEquals(MediaKind.OTHER, mediaKindForName("README"))
    }

    @Test
    fun `a MIME type wins, and the name only fills in what it can't place`() {
        assertEquals(MediaKind.IMAGE, mediaKindOf("image/jpeg", "photo.jpg"))
        assertEquals(MediaKind.IMAGE, mediaKindOf(null, "IMG_0001.heic"))
        assertEquals(MediaKind.IMAGE, mediaKindOf("application/octet-stream", "IMG_0001.heic"))
        assertEquals(MediaKind.VIDEO, mediaKindOf("video/mp4", "clip.heic"))
        assertEquals(MediaKind.OTHER, mediaKindOf(null, "backup.zip"))
    }

    @Test
    fun `a MIME type is offered for the extensions a later stage needs`() {
        assertEquals("image/heic", mimeTypeForName("IMG_0001.HEIC"))
        assertEquals("video/x-matroska", mimeTypeForName("clip.mkv"))
        assertEquals("text/markdown", mimeTypeForName("notes.md"))
        assertNull(mimeTypeForName("photo.jpg"))
        assertNull(mimeTypeForName("README"))
    }
}

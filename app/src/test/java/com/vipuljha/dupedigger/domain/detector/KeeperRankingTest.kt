package com.vipuljha.dupedigger.domain.detector

import com.vipuljha.dupedigger.domain.model.GroupMember
import org.junit.Assert.assertEquals
import org.junit.Test

class KeeperRankingTest {

    private fun member(path: String?, mtime: Long = 1_000, quality: Long? = null, uri: String = "file:$path") =
        GroupMember(fileId = uri.hashCode().toLong(), sourceUri = uri, path = path, size = 10, mtime = mtime, quality = quality)

    private val root = "/storage/emulated/0"

    @Test
    fun `higher resolution beats a better location`() {
        val camera = member("$root/DCIM/Camera/a.jpg", quality = 1_000_000)
        val download = member("$root/Download/a.jpg", quality = 12_000_000)
        assertEquals(download, rankKeeper(listOf(camera, download)))
    }

    @Test
    fun `the camera folder beats messaging, download and app media copies`() {
        val camera = member("$root/DCIM/Camera/IMG_1.jpg", mtime = 9_000)
        val copies = listOf(
            member("$root/WhatsApp/Media/WhatsApp Images/IMG-1.jpg", mtime = 1),
            member("$root/Android/media/com.whatsapp/WhatsApp/Media/IMG-1.jpg", mtime = 2),
            member("$root/Download/IMG_1.jpg", mtime = 3),
            member("$root/Telegram/Telegram Images/1.jpg", mtime = 4),
        )
        assertEquals(camera, rankKeeper(copies + camera))
    }

    @Test
    fun `a copy folder anywhere above the file outranks a DCIM folder in it`() {
        val downloadedDcim = member("$root/Download/DCIM/a.jpg", mtime = 1)
        val elsewhere = member("$root/Pictures/a.jpg", mtime = 2)
        assertEquals(elsewhere, rankKeeper(listOf(downloadedDcim, elsewhere)))
    }

    @Test
    fun `a file name that looks like a folder doesn't count`() {
        val named = member("$root/Pictures/Download", mtime = 1)
        val other = member("$root/Music/b", mtime = 2)
        assertEquals(named, rankKeeper(listOf(other, named)))
    }

    @Test
    fun `the oldest wins among equals and an unknown time never does`() {
        val older = member("$root/Pictures/a.jpg", mtime = 100)
        val newer = member("$root/Documents/a.jpg", mtime = 200)
        val unknown = member("$root/Movies/a.jpg", mtime = 0)
        assertEquals(older, rankKeeper(listOf(newer, unknown, older)))
    }

    @Test
    fun `a file without a path is neutral`() {
        val noPath = member(null, mtime = 100, uri = "content://com.example.docs/tree/1/document/2")
        val copy = member("$root/Download/a.jpg", mtime = 50)
        val camera = member("$root/DCIM/a.jpg", mtime = 300)
        assertEquals(noPath, rankKeeper(listOf(copy, noPath)))
        assertEquals(camera, rankKeeper(listOf(noPath, camera)))
    }

    @Test
    fun `a full tie is broken the same way every time`() {
        val a = member("$root/Pictures/a.jpg")
        val b = member("$root/Pictures/b.jpg")
        assertEquals(a, rankKeeper(listOf(b, a)))
        assertEquals(a, rankKeeper(listOf(a, b)))
    }
}

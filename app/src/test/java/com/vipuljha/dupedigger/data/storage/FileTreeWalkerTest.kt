package com.vipuljha.dupedigger.data.storage

import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.roots
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class FileTreeWalkerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val walker = FileTreeWalker { name -> if (name.endsWith(".jpg")) "image/jpeg" else null }

    private fun File.touch(relative: String, bytes: Int = 1) =
        File(this, relative).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test
    fun `finds nested files with metadata and honours the filter`() = runBlocking {
        val root = tmp.newFolder("root")
        root.touch("a.jpg", 3)
        root.touch("deep/er/b.txt")
        root.touch("Private/c.jpg")
        root.touch(".hidden/d.jpg")

        val files = walker.walk(listOf(root), ScanFilter(listOf("${root.path}/Private"), skipHidden = true)).toList()

        assertEquals(setOf("a.jpg", "b.txt"), files.map { it.name }.toSet())
        val a = files.single { it.name == "a.jpg" }
        assertEquals(3L, a.size)
        assertEquals(MediaKind.IMAGE, a.mediaKind)
        assertEquals(File(root, "a.jpg").path, a.path)
    }

    @Test
    fun `a walk from a volume root reaches whatsapp media under Android media`() = runBlocking {
        val volume = tmp.newFolder("volume")
        val media = "Android/media/com.whatsapp/WhatsApp/Media"
        volume.touch("$media/WhatsApp Images/IMG-20260101-WA0001.jpg")
        volume.touch("$media/WhatsApp Images/Sent/IMG-20260101-WA0002.jpg")
        volume.touch("$media/WhatsApp Documents/notes.txt")
        volume.touch("DCIM/Camera/IMG_0001.jpg")

        val paths = walker.walk(listOf(volume), ScanFilter(emptyList(), skipHidden = true)).toList().map { it.path }

        assertEquals(
            setOf(
                "$media/WhatsApp Images/IMG-20260101-WA0001.jpg",
                "$media/WhatsApp Images/Sent/IMG-20260101-WA0002.jpg",
                "$media/WhatsApp Documents/notes.txt",
                "DCIM/Camera/IMG_0001.jpg",
            ).map { File(volume, it).path }.toSet(),
            paths.toSet(),
        )
    }

    @Test
    fun `a paths walk covers only its folders, once, with the same rows as a whole-device walk`() = runBlocking {
        val volume = tmp.newFolder("volume")
        volume.touch("DCIM/Camera/a.jpg")
        volume.touch("DCIM/Camera/sub/b.jpg")
        volume.touch("DCIM/Camera2/c.jpg")
        volume.touch("Pictures/Screenshots/d.jpg")
        val scope = ScanScope.Paths(persistentListOf("DCIM/Camera", "/Pictures//Screenshots/", "dcim/camera/sub", "../etc", "Movies/Missing"))
        val filter = ScanFilter(emptyList(), skipHidden = true)

        val files = walker.walk(listOf(volume), filter, scope.roots()).toList()

        assertEquals(listOf("a.jpg", "b.jpg", "d.jpg"), files.map { it.name }.sorted())
        val whole = walker.walk(listOf(volume), filter).toList().associateBy { it.sourceUri }
        files.forEach { assertEquals(whole[it.sourceUri], it) }
    }

    @Test
    fun `paths prune prefixes match their folders exactly, even once a folder is gone`() = runBlocking {
        val volume = tmp.newFolder("volume")
        volume.touch("DCIM/Camera/a.jpg")
        volume.touch("DCIM/Camera/sub/b.jpg")
        volume.touch("DCIM/Camera2/c.jpg")
        volume.touch("DCIM/Camera 3/d.jpg")
        val scope = ScanScope.Paths(persistentListOf("DCIM/Camera", "DCIM/Camera 3"))
        val uris = walker.walk(listOf(volume), ScanFilter(emptyList(), skipHidden = true)).toList().associate { it.name to it.sourceUri }

        val prefixes = sourceUriPrefixes(scope, listOf(volume))

        val covered = uris.filterValues { uri -> prefixes.any { uri.startsWith(it) } }.keys
        assertEquals(setOf("a.jpg", "b.jpg", "d.jpg"), covered)
        File(volume, "DCIM/Camera").deleteRecursively()
        assertEquals(prefixes, sourceUriPrefixes(scope, listOf(volume)))
    }

    @Test
    fun `symlinks are never followed out of the root`() = runBlocking {
        val outside = tmp.newFolder("outside").apply { touch("secret.jpg") }
        val root = tmp.newFolder("root2").apply { touch("own.jpg") }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())

        val names = walker.walk(listOf(root), ScanFilter(emptyList(), skipHidden = false)).toList().map { it.name }

        assertEquals(listOf("own.jpg"), names)
    }

    @Test
    fun `walks 50k files in 500 folders`() = runBlocking {
        val root = tmp.newFolder("big")
        repeat(500) { d -> repeat(100) { f -> root.touch("d$d/f$f.jpg", 0) } }

        val count = walker.walk(listOf(root), ScanFilter(emptyList(), skipHidden = false)).count()

        assertEquals(50_000, count)
    }

    @Test
    fun `cancelling stops the walk within a second`() = runBlocking {
        val root = tmp.newFolder("slow")
        repeat(20) { d -> repeat(100) { f -> root.touch("d$d/f$f.jpg", 0) } }
        val slowWalker = FileTreeWalker { Thread.sleep(5); null }

        var seen = 0
        val job = launch(Dispatchers.IO) {
            slowWalker.walk(listOf(root), ScanFilter(emptyList(), skipHidden = false)).flowOn(Dispatchers.IO).collect { seen++ }
        }
        delay(300)
        val started = System.nanoTime()
        job.cancelAndJoin()
        val stopMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue("stopped after $stopMillis ms", stopMillis < 1_000)
        assertTrue("walk had started but not finished: $seen", seen in 1 until 2_000)
    }
}

package com.vipuljha.dupedigger.data.storage

import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFilter
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

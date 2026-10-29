package com.vipuljha.dupedigger.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.local.db.entity.DuplicateGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.EmbeddingEntity
import com.vipuljha.dupedigger.data.local.db.entity.ExcludedPathEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileHashEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.local.db.entity.GroupMemberEntity
import com.vipuljha.dupedigger.data.local.db.entity.IgnoredGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.KeepChoiceEntity
import com.vipuljha.dupedigger.data.local.db.entity.QuarantineItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: DupeDiggerDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            DupeDiggerDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun file(uri: String, scanId: Long = 1, size: Long = 100) = FileIndexEntity(
        sourceUri = uri, path = "/sdcard/$uri", name = uri, mimeType = "image/jpeg",
        mediaKind = "IMAGE", size = size, mtime = 10, volume = "external_primary", lastSeenScanId = scanId,
    )

    @Test
    fun updatingAFileKeepsItsIdAndCachedHash() = runTest {
        val files = db.fileIndexDao()
        val id = files.insert(file("a.jpg"))
        db.fileHashDao().upsert(FileHashEntity(id, "p", "f", computedAtMtime = 10))

        files.update(files.findBySourceUri("a.jpg")!!.copy(lastSeenScanId = 2))

        assertEquals(id, files.findBySourceUri("a.jpg")!!.id)
        assertEquals("f", db.fileHashDao().get(id)?.fullHash)
    }

    @Test
    fun staleFilesAreRemovedWithTheirDerivedRows() = runTest {
        val files = db.fileIndexDao()
        val stale = files.insert(file("old.jpg", scanId = 1))
        val fresh = files.insert(file("new.jpg", scanId = 2))
        db.fileHashDao().upsert(FileHashEntity(stale, "p", "f", 10))
        db.embeddingDao().upsert(EmbeddingEntity(stale, "m", 2, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))

        assertEquals(1, files.deleteNotSeenSince(2))

        assertNull(files.findBySourceUri("old.jpg"))
        assertNull(db.fileHashDao().get(stale))
        assertTrue(db.embeddingDao().forModel("m").isEmpty())
        assertNotNull(files.findBySourceUri("new.jpg"))
        assertEquals(1, files.observeCount().first())
        assertEquals(fresh, files.findBySourceUri("new.jpg")!!.id)
    }

    @Test
    fun embeddingVectorsRoundTrip() = runTest {
        val id = db.fileIndexDao().insert(file("a.jpg"))
        val vector = ByteArray(16) { it.toByte() }
        db.embeddingDao().upsert(EmbeddingEntity(id, "m1", 4, vector))
        db.embeddingDao().upsert(EmbeddingEntity(id, "m2", 4, vector))

        db.embeddingDao().deleteOtherModels("m1")

        assertEquals(listOf(EmbeddingEntity(id, "m1", 4, vector)), db.embeddingDao().forModel("m1"))
        assertTrue(db.embeddingDao().forModel("m2").isEmpty())
    }

    @Test
    fun groupsAreStoredWithMembersAndClearedPerScan() = runTest {
        val a = db.fileIndexDao().insert(file("a.jpg"))
        val b = db.fileIndexDao().insert(file("b.jpg"))
        val groups = db.duplicateGroupDao()
        val old = groups.insertGroupWithMembers(
            DuplicateGroupEntity(tier = "EXACT", score = 1f, scanId = 1, fingerprint = "fp"),
            listOf(GroupMemberEntity(0, a, 1f), GroupMemberEntity(0, b, 1f)),
        )
        val current = groups.insertGroupWithMembers(
            DuplicateGroupEntity(tier = "EXACT", score = 1f, scanId = 2, fingerprint = "fp"),
            listOf(GroupMemberEntity(0, a, 1f), GroupMemberEntity(0, b, 1f)),
        )

        assertEquals(setOf(a, b), groups.membersOf(current).map { it.fileId }.toSet())

        groups.deleteOtherScans(2)
        assertTrue(groups.membersOf(old).isEmpty())
        assertEquals(listOf(current), groups.observeForScan(2).first().map { it.groupId })
    }

    @Test
    fun userDecisionsRoundTrip() = runTest {
        db.ignoredGroupDao().upsert(IgnoredGroupEntity("fp1", 1))
        db.keepChoiceDao().upsert(KeepChoiceEntity("fp2", "content://keep", 2))
        val qid = db.quarantineDao().insert(QuarantineItemEntity(originalPath = "/a.pdf", quarantinePath = "/q/a.pdf", size = 5, movedAt = 3))
        db.excludedPathDao().upsert(ExcludedPathEntity("/sdcard/Private", 4))

        assertEquals(listOf("fp1"), db.ignoredGroupDao().observeFingerprints().first())
        assertEquals("content://keep", db.keepChoiceDao().get("fp2")?.keeperSourceUri)
        assertEquals("/q/a.pdf", db.quarantineDao().get(qid)?.quarantinePath)
        assertEquals(listOf("/sdcard/Private"), db.excludedPathDao().observeAll().first().map { it.path })

        db.ignoredGroupDao().delete("fp1")
        db.quarantineDao().delete(qid)
        assertTrue(db.ignoredGroupDao().observeFingerprints().first().isEmpty())
        assertTrue(db.quarantineDao().observeAll().first().isEmpty())
    }
}

package com.vipuljha.dupedigger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.local.db.entity.IgnoredGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.KeepChoiceEntity
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.MatchTier
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** P3-03: saving groups replaces the previous scan's in one go, and user choices read back by fingerprint. */
@RunWith(AndroidJUnit4::class)
class DuplicateGroupRepositoryTest {
    private lateinit var db: DupeDiggerDatabase
    private lateinit var repository: DuplicateGroupRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DupeDiggerDatabase::class.java).build()
        repository = DuplicateGroupRepositoryImpl(db, db.duplicateGroupDao(), db.ignoredGroupDao(), db.keepChoiceDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun file(name: String) = db.fileIndexDao().insert(
        FileIndexEntity(
            sourceUri = "file:/$name", path = "/$name", name = name, mimeType = null, mediaKind = "OTHER",
            size = 10, mtime = 1, volume = null, lastSeenScanId = 1,
        ),
    )

    @Test
    fun replacingGroupsDropsThePreviousScansAndKeepsMembers() = runBlocking {
        val ids = (1..5).map { file("f$it") }
        repository.replaceGroups(1, listOf(FoundGroup("EXACT:10:old", MatchTier.EXACT, 1f, persistentListOf(ids[0], ids[1]))))

        repository.replaceGroups(
            2,
            listOf(
                FoundGroup("EXACT:10:a", MatchTier.EXACT, 1f, persistentListOf(ids[0], ids[1], ids[2])),
                FoundGroup("EXACT:10:b", MatchTier.EXACT, 1f, persistentListOf(ids[3], ids[4])),
            ),
        )

        assertEquals(emptyList<Any>(), db.duplicateGroupDao().observeForScan(1).first())
        val saved = db.duplicateGroupDao().observeForScan(2).first().sortedBy { it.fingerprint }
        assertEquals(listOf("EXACT:10:a", "EXACT:10:b"), saved.map { it.fingerprint })
        assertEquals(listOf(3, 2), saved.map { db.duplicateGroupDao().membersOf(it.groupId).size })
    }

    @Test
    fun userChoicesReadBackByFingerprint() = runBlocking {
        db.ignoredGroupDao().upsert(IgnoredGroupEntity("EXACT:10:a", ignoredAt = 1))
        db.keepChoiceDao().upsert(KeepChoiceEntity("EXACT:10:b", keeperSourceUri = "file:/f2", chosenAt = 1))

        assertEquals(setOf("EXACT:10:a"), repository.ignoredFingerprints())
        assertEquals("file:/f2", repository.keepChoice("EXACT:10:b"))
        assertEquals(null, repository.keepChoice("EXACT:10:a"))
    }
}

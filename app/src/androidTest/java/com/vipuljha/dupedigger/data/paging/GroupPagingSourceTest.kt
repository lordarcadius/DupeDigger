package com.vipuljha.dupedigger.data.paging

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.local.db.entity.KeepChoiceEntity
import com.vipuljha.dupedigger.data.repository.DuplicateGroupRepositoryImpl
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/** P4-03: paging order, filters and offsets over Room, invalidation, and page timing with 5k groups. */
@RunWith(AndroidJUnit4::class)
class GroupPagingSourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: DupeDiggerDatabase
    private lateinit var repo: DuplicateGroupRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DupeDiggerDatabase::class.java).build()
        repo = DuplicateGroupRepositoryImpl(db, db.duplicateGroupDao(), db.ignoredGroupDao(), db.keepChoiceDao())
    }

    @After
    fun tearDown() = db.close()

    /** Adds [count] groups of [copies] files each; group i holds files of size (i + 1) * [unit]. */
    private suspend fun seed(db: DupeDiggerDatabase, count: Int, copies: Int = 2, unit: Long = 1_000, kind: (Int) -> MediaKind = { MediaKind.IMAGE }) {
        val repo = DuplicateGroupRepositoryImpl(db, db.duplicateGroupDao(), db.ignoredGroupDao(), db.keepChoiceDao())
        val groups = (0 until count).map { i ->
            val ids = (0 until copies).map { c ->
                db.fileIndexDao().insert(
                    FileIndexEntity(
                        sourceUri = "file:/storage/emulated/0/Seed/g$i/c$c.bin", path = "/storage/emulated/0/Seed/g$i/c$c.bin",
                        name = "file-$i-$c.bin", mimeType = null, mediaKind = kind(i).name, size = (i + 1) * unit, mtime = 1,
                        volume = null, lastSeenScanId = 1,
                    ),
                )
            }
            FoundGroup("EXACT:$i", MatchTier.EXACT, 1f, ids.toImmutableList())
        }
        repo.replaceGroups(1, groups)
    }

    private suspend fun load(filter: GroupFilter, key: Int?, size: Int) =
        repo.groupPagingSource(filter).load(PagingSource.LoadParams.Refresh(key, size, false)) as PagingSource.LoadResult.Page<Int, GroupWithMembers>

    @Test
    fun pagesComeLargestFirstWithOffsetsAndAnEnd() = runBlocking {
        seed(db, count = 5)

        val first = load(GroupFilter(), null, 2)
        val last = load(GroupFilter(), 4, 2)

        assertEquals(listOf("EXACT:4", "EXACT:3"), first.data.map { it.fingerprint })
        assertEquals(2, first.nextKey)
        assertNull(first.prevKey)
        assertEquals(listOf("EXACT:0"), last.data.map { it.fingerprint })
        assertNull(last.nextKey)
        assertTrue(first.data.all { it.members.size == 2 })
    }

    @Test
    fun theKindFilterMatchesTheGroupsKind() = runBlocking {
        seed(db, count = 4, kind = { if (it % 2 == 0) MediaKind.IMAGE else MediaKind.VIDEO })

        val videos = load(GroupFilter(kind = MediaKind.VIDEO), null, 10)

        assertEquals(listOf("EXACT:3", "EXACT:1"), videos.data.map { it.fingerprint })
        assertTrue(videos.data.all { it.kind == MediaKind.VIDEO })
    }

    @Test
    fun aKeepChoiceIsReadAndChangesInvalidateTheSource() = runBlocking {
        seed(db, count = 2)
        val source = repo.groupPagingSource(GroupFilter())
        source.load(PagingSource.LoadParams.Refresh(null, 10, false))

        db.keepChoiceDao().upsert(KeepChoiceEntity("EXACT:1", "file:/storage/emulated/0/Seed/g1/c1.bin", 1))

        assertTrue("keep choice invalidates", awaitInvalid(source))
        assertEquals("file:/storage/emulated/0/Seed/g1/c1.bin", load(GroupFilter(), null, 10).data.first().keepChoice)
    }

    @Test
    fun deletingAFileInvalidatesThroughTheMembershipCascade() = runBlocking {
        seed(db, count = 2)
        val source = repo.groupPagingSource(GroupFilter())
        source.load(PagingSource.LoadParams.Refresh(null, 10, false))
        val fileId = db.fileIndexDao().findBySourceUri("file:/storage/emulated/0/Seed/g1/c0.bin")!!.id

        db.fileIndexDao().deleteByIds(listOf(fileId))

        assertTrue("file delete invalidates", awaitInvalid(source))
        assertEquals(listOf("EXACT:0"), load(GroupFilter(), null, 10).data.map { it.fingerprint })
    }

    @Test
    fun aPageOf50OutOf5000GroupsLoadsQuickly() = runBlocking {
        seed(db, count = 5_000, copies = 3)

        val started = System.nanoTime()
        val first = load(GroupFilter(), null, 50)
        val firstMillis = (System.nanoTime() - started) / 1_000_000
        val deepStarted = System.nanoTime()
        val deep = load(GroupFilter(), 4_900, 50)
        val deepMillis = (System.nanoTime() - deepStarted) / 1_000_000
        Timber.i("5000 groups: first page %d ms, page at offset 4900 %d ms", firstMillis, deepMillis)

        assertEquals(50, first.data.size)
        assertEquals(50, deep.data.size)
        assertTrue("first page took $firstMillis ms", firstMillis < 500)
        assertTrue("deep page took $deepMillis ms", deepMillis < 500)
    }

    /** Seeds the app's real database with 5k groups for a manual scroll check; runs only with `-e seedResults true`. */
    @Test
    fun seedTheAppDatabaseForAScrollCheck() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seedResults") == "true")
        val appDb = Room.databaseBuilder(context, DupeDiggerDatabase::class.java, DupeDiggerDatabase.NAME).build()
        try {
            seed(appDb, count = 5_000, copies = 3, kind = { MediaKind.entries[it % MediaKind.entries.size] })
        } finally {
            appDb.close()
        }
    }

    private suspend fun awaitInvalid(source: PagingSource<*, *>): Boolean {
        repeat(40) {
            if (source.invalid) return true
            delay(50)
        }
        return source.invalid
    }
}

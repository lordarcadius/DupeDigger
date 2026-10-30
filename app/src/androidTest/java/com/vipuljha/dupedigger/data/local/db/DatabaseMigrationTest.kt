package com.vipuljha.dupedigger.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration checks against the schemas exported to `app/schemas`:
 * - user decisions written in the oldest schema survive the migration to the current version;
 * - every exported version migrates to the latest and matches it exactly;
 * - the database the app builds is the latest exported version, so a version bump can't ship without its schema.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DupeDiggerDatabase::class.java)

    @Test
    fun userDecisionsSurviveEveryMigration() = runTest {
        helper.createDatabase(TEST_DB, FIRST_VERSION).apply {
            execSQL("INSERT INTO ignored_group (fingerprint, ignoredAt) VALUES ('fp', 1)")
            execSQL("INSERT INTO keep_choice (fingerprint, keeperSourceUri, chosenAt) VALUES ('fp', 'content://k', 2)")
            execSQL("INSERT INTO quarantine_item (sourceUri, originalPath, quarantinePath, size, movedAt) VALUES ('file:/a', '/a', '/q/a', 3, 4)")
            execSQL("INSERT INTO excluded_path (path, addedAt) VALUES ('/sdcard/Private', 5)")
            close()
        }

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            DupeDiggerDatabase::class.java,
            TEST_DB,
        ).addMigrations(*ALL_MIGRATIONS).build()
        helper.closeWhenFinished(db)

        assertEquals(listOf("fp"), db.ignoredGroupDao().observeFingerprints().first())
        assertEquals("content://k", db.keepChoiceDao().get("fp")?.keeperSourceUri)
        assertEquals(listOf("/q/a"), db.quarantineDao().observeAll().first().map { it.quarantinePath })
        assertEquals(listOf("/sdcard/Private"), db.excludedPathDao().observeAll().first().map { it.path })
    }

    @Test
    fun everyExportedVersionMigratesToTheLatestSchema() {
        exportedVersions.forEach { version ->
            val name = "$TEST_DB-$version"
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(name, exportedVersions.max(), true, *ALL_MIGRATIONS).close()
        }
    }

    @Test
    fun theBuiltDatabaseIsTheLatestExportedVersion() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DupeDiggerDatabase::class.java).build()
        try {
            assertEquals(exportedVersions.max(), db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    private val exportedVersions: List<Int> by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets
            .list(DupeDiggerDatabase::class.java.canonicalName!!)!!
            .map { it.removeSuffix(".json").toInt() }
            .also { check(FIRST_VERSION in it) }
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val FIRST_VERSION = 1
    }
}

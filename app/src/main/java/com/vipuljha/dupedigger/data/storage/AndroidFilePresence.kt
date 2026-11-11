package com.vipuljha.dupedigger.data.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.domain.repository.FilePresence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One stat or one metadata query per file, never a read of its contents.
 * A provider that can't answer counts as present, so a file is never dropped from the results on a provider's hiccup.
 */
@Singleton
class AndroidFilePresence @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FilePresence {

    override suspend fun missing(sourceUris: Collection<String>): Set<String> =
        withContext(ioDispatcher) {
            sourceUris.filterNotTo(HashSet()) {
                currentCoroutineContext().ensureActive()
                exists(it)
            }
        }

    private fun exists(sourceUri: String): Boolean = if (sourceUri.startsWith("file:")) {
        try {
            File(URI(sourceUri)).exists()
        } catch (_: URISyntaxException) {
            true
        } catch (_: IllegalArgumentException) {
            true
        }
    } else {
        context.contentResolver.hasRow(sourceUri)
    }

    // No projection: `_ID` isn't a column every documents provider has, and a bad one is an error, not an empty row.
    private fun ContentResolver.hasRow(sourceUri: String): Boolean = try {
        query(Uri.parse(sourceUri), null, null, null, null)?.use { it.moveToFirst() } ?: true
    } catch (_: SecurityException) {
        true
    } catch (_: IllegalArgumentException) {
        true
    }
}

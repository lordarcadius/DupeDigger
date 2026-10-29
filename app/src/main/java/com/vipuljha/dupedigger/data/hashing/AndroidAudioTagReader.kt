package com.vipuljha.dupedigger.data.hashing

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.domain.model.AudioTags
import com.vipuljha.dupedigger.domain.repository.AudioTagReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Duration, title and artist (album artist when there is no artist) through `MediaMetadataRetriever`. */
@Singleton
class AndroidAudioTagReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioTagReader {

    override suspend fun read(sourceUri: String): AudioTags? = withContext(ioDispatcher) {
        try {
            MediaMetadataRetriever().use { retriever ->
                if (sourceUri.startsWith("file:")) {
                    retriever.setDataSource(File(URI(sourceUri)).path)
                } else {
                    retriever.setDataSource(context, Uri.parse(sourceUri))
                }
                val tag = { key: Int -> retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() } }
                val duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@use null
                AudioTags(
                    durationMs = duration,
                    title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Couldn't read an audio file's tags")
            null
        } catch (e: RuntimeException) {
            // MediaMetadataRetriever reports unreadable or unsupported files as plain RuntimeExceptions.
            Timber.w(e, "Couldn't read an audio file's tags")
            null
        }
    }
}

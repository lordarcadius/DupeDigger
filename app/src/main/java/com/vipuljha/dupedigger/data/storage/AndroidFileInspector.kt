package com.vipuljha.dupedigger.data.storage

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.MediaInfo
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ViewTarget
import com.vipuljha.dupedigger.domain.repository.FileInspector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads image and video dimensions, and makes URIs other apps can open.
 * - Media gets its typed MediaStore URI; picked-folder documents keep theirs, which the app's grant lets it share.
 * - Any other file goes through the app's FileProvider, so a `file:` URI is never handed out.
 */
@Singleton
class AndroidFileInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FileInspector {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun mediaInfo(member: GroupMember, kind: MediaKind): MediaInfo? = withContext(ioDispatcher) {
        try {
            when (kind) {
                MediaKind.IMAGE -> imageInfo(member.sourceUri)
                MediaKind.VIDEO -> videoInfo(member.sourceUri)
                else -> null
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "Couldn't read media dimensions")
            null
        } catch (e: IOException) {
            Timber.w(e, "Couldn't read media dimensions")
            null
        }
    }

    override suspend fun viewTarget(member: GroupMember): ViewTarget? = withContext(ioDispatcher) {
        val uri = when {
            member.sourceUri.startsWith("file:") -> resolver.mediaCollectionUri(member.sourceUri, member.path) ?: fileProviderUri(member.sourceUri)
            member.sourceUri.startsWith("content://media/") -> resolver.mediaCollectionUri(member.sourceUri, member.path)
            else -> Uri.parse(member.sourceUri)
        } ?: return@withContext null
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(member.name.substringAfterLast('.', "").lowercase())
        ViewTarget(uri.toString(), mime)
    }

    private fun fileProviderUri(sourceUri: String): Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", File(URI(sourceUri)))
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "No FileProvider root covers this file")
        null
    }

    private fun imageInfo(sourceUri: String): MediaInfo {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openSource(sourceUri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val orientation = resolver.openSource(sourceUri).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val rotated = orientation in SIDEWAYS_ORIENTATIONS
        val (w, h) = bounds.outWidth.takeIf { it > 0 } to bounds.outHeight.takeIf { it > 0 }
        return if (rotated) MediaInfo(h, w) else MediaInfo(w, h)
    }

    private fun videoInfo(sourceUri: String): MediaInfo = MediaMetadataRetriever().use { retriever ->
        if (sourceUri.startsWith("file:")) retriever.setDataSource(File(URI(sourceUri)).path) else retriever.setDataSource(context, Uri.parse(sourceUri))
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation % 180 != 0) MediaInfo(h, w) else MediaInfo(w, h)
    }

    companion object {
        const val FILE_PROVIDER_SUFFIX = ".files"
        private val SIDEWAYS_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
        )
    }
}

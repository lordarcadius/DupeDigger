package com.vipuljha.dupedigger.data.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import timber.log.Timber

private const val MEDIA_URI_PREFIX = "content://media/"

/**
 * A file's row in its images, video or audio collection: by id for MediaStore scans, by path otherwise.
 * The trash prompt and other apps need this typed URI rather than a Files-collection one. Null when MediaStore has no media row.
 */
fun ContentResolver.mediaCollectionUri(sourceUri: String, path: String?): Uri? {
    val (selection, args) = when {
        sourceUri.startsWith(MEDIA_URI_PREFIX) -> "${MediaStore.Files.FileColumns._ID} = ?" to arrayOf(Uri.parse(sourceUri).lastPathSegment)
        path != null -> "${MediaStore.Files.FileColumns.DATA} = ?" to arrayOf(path)
        else -> return null
    }
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.VOLUME_NAME,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
    )
    return try {
        query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), projection, selection, args, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val collection = when (c.getInt(2)) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.getContentUri(c.getString(1))
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.getContentUri(c.getString(1))
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.getContentUri(c.getString(1))
                else -> return null
            }
            ContentUris.withAppendedId(collection, c.getLong(0))
        }
    } catch (e: SecurityException) {
        Timber.w(e, "Couldn't look up a file in MediaStore")
        null
    }
}

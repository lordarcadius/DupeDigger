package com.vipuljha.dupedigger.data.hashing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.repository.ImageHasher
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
 * Decodes an image at a power-of-two subsample whose short side stays at least [MIN_SIDE] pixels, then hashes its
 * luminance. `ImageDecoder` applies the EXIF orientation; transparent pixels count as white.
 */
@Singleton
class AndroidImageHasher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ImageHasher {

    override suspend fun hash(sourceUri: String): ImageHashes? = withContext(ioDispatcher) {
        try {
            val source = if (sourceUri.startsWith("file:")) {
                ImageDecoder.createSource(File(URI(sourceUri)))
            } else {
                ImageDecoder.createSource(context.contentResolver, Uri.parse(sourceUri))
            }
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sampleSize(minOf(info.size.width, info.size.height)))
            }
            try {
                hashOf(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: IOException) {
            Timber.w(e, "Couldn't decode an image for hashing")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Couldn't decode an image for hashing")
            null
        } catch (e: SecurityException) {
            Timber.w(e, "Couldn't read an image for hashing")
            null
        }
    }

    private fun hashOf(bitmap: Bitmap): ImageHashes {
        val (width, height) = bitmap.width to bitmap.height
        val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
        val luma = FloatArray(pixels.size) { i ->
            val p = pixels[i]
            val alpha = Color.alpha(p) / 255f
            val y = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
            y * alpha + 255f * (1 - alpha)
        }
        return perceptualHashes(luma, width, height)
    }

    private fun sampleSize(shortSide: Int): Int {
        var sample = 1
        while (shortSide / (sample * 2) >= MIN_SIDE) sample *= 2
        return sample
    }

    private companion object {
        const val MIN_SIDE = 64
    }
}

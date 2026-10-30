package com.vipuljha.dupedigger.data.hashing

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.data.storage.openSource
import com.vipuljha.dupedigger.domain.repository.DocumentTextReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain text of text files, .docx and PDFs (PDFs only on API 35+, where the platform renderer exposes page text).
 * Reading is capped (characters, XML bytes, PDF pages) so one huge file can't stall a scan or exhaust memory.
 */
@Singleton
class AndroidDocumentTextReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentTextReader {

    private enum class Format { TEXT, DOCX, PDF }

    override fun supports(name: String, mimeType: String?): Boolean = formatOf(name, mimeType) != null

    override suspend fun text(sourceUri: String, name: String, mimeType: String?): String? = withContext(ioDispatcher) {
        try {
            when (formatOf(name, mimeType)) {
                Format.TEXT -> context.contentResolver.openSource(sourceUri).bufferedReader().use { reader ->
                    val chars = CharArray(MAX_CHARS)
                    var read = 0
                    while (read < MAX_CHARS) {
                        val n = reader.read(chars, read, MAX_CHARS - read)
                        if (n < 0) break
                        read += n
                    }
                    String(chars, 0, read)
                }
                Format.DOCX -> context.contentResolver.openSource(sourceUri).use { docxText(it, MAX_XML_BYTES) }
                Format.PDF -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) pdfText(sourceUri) else null
                null -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Couldn't read a document's text")
            null
        } catch (e: RuntimeException) {
            // PdfRenderer reports damaged or password-protected files as runtime exceptions.
            Timber.w(e, "Couldn't read a document's text")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun pdfText(sourceUri: String): String {
        val descriptor = if (sourceUri.startsWith("file:")) {
            ParcelFileDescriptor.open(File(URI(sourceUri)), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r") ?: throw FileNotFoundException(sourceUri)
        }
        // PdfRenderer owns the descriptor only once it opens; a damaged or locked PDF throws before that.
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: Throwable) {
            descriptor.close()
            throw e
        }
        return renderer.use { renderer ->
            buildString {
                for (index in 0 until minOf(renderer.pageCount, MAX_PDF_PAGES)) {
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(index).use { page -> page.textContents.forEach { append(it.text).append('\n') } }
                }
            }
        }
    }

    private fun formatOf(name: String, mimeType: String?): Format? {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            extension == "docx" -> Format.DOCX
            extension == "pdf" || mimeType == "application/pdf" ->
                Format.PDF.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM }
            extension in TEXT_EXTENSIONS || mimeType?.startsWith("text/") == true -> Format.TEXT
            else -> null
        }
    }

    private companion object {
        const val MAX_CHARS = 1_000_000
        const val MAX_XML_BYTES = 20 * 1024 * 1024
        const val MAX_PDF_PAGES = 200
        val TEXT_EXTENSIONS = setOf("txt", "md", "markdown", "text", "log", "csv", "rst")
    }
}

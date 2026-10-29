package com.vipuljha.dupedigger.data.storage

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI

/** Opens an indexed file's `file:` or `content:` source URI for reading. */
fun ContentResolver.openSource(sourceUri: String): InputStream =
    if (sourceUri.startsWith("file:")) {
        FileInputStream(File(URI(sourceUri)))
    } else {
        openInputStream(Uri.parse(sourceUri)) ?: throw FileNotFoundException(sourceUri)
    }

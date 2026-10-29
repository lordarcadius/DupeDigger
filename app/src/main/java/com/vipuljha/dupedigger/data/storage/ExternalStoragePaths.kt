package com.vipuljha.dupedigger.data.storage

/** SAF authority of the platform's ExternalStorageProvider, whose document ids are `volume:relative/path`. */
const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

private const val PRIMARY_ROOT = "/storage/emulated/0"

/** Maps an ExternalStorageProvider document id to its absolute path, or null for an id it can't place. */
fun externalStorageDocIdToPath(docId: String): String? {
    val volume = docId.substringBefore(':', missingDelimiterValue = "")
    if (volume.isEmpty()) return null
    val relative = docId.substringAfter(':').trim('/')
    val root = if (volume == "primary") PRIMARY_ROOT else "/storage/$volume"
    return if (relative.isEmpty()) root else "$root/$relative"
}

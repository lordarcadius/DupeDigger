package com.vipuljha.dupedigger.data.hashing

import java.io.InputStream
import java.util.zip.ZipInputStream

private val docxRuns = Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|</w:p>|<w:tab/>|<w:br/>")
private val xmlEntity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

/**
 * The text of a .docx: the runs of `word/document.xml`, one line per paragraph.
 * At most [maxXmlBytes] of the XML are read, so a zip bomb can't exhaust memory; null when there is no document part.
 */
fun docxText(input: InputStream, maxXmlBytes: Int): String? {
    val zip = ZipInputStream(input)
    while (true) {
        val entry = zip.nextEntry ?: return null
        if (entry.name != "word/document.xml") continue
        val xml = readCapped(zip, maxXmlBytes).toString(Charsets.UTF_8)
        val text = StringBuilder()
        for (match in docxRuns.findAll(xml)) {
            when (match.value) {
                "</w:p>" -> text.append('\n')
                "<w:tab/>", "<w:br/>" -> text.append(' ')
                else -> text.append(decodeXmlEntities(match.groupValues[1]))
            }
        }
        return text.toString()
    }
}

internal fun decodeXmlEntities(text: String): String = xmlEntity.replace(text) { m ->
    when (val name = m.groupValues[1]) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        else -> {
            val code = if (name.startsWith("#x")) name.drop(2).toInt(16) else name.drop(1).toInt()
            if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else m.value
        }
    }
}

private fun readCapped(input: InputStream, max: Int): ByteArray {
    val buffer = ByteArray(minOf(max, 64 * 1024))
    val out = java.io.ByteArrayOutputStream()
    while (out.size() < max) {
        val n = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

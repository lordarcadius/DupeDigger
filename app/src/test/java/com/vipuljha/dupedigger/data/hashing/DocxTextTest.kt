package com.vipuljha.dupedigger.data.hashing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxTextTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, body) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    private val document = """
        <?xml version="1.0"?><w:document><w:body>
        <w:p><w:r><w:t>Fish &amp; chips</w:t></w:r><w:r><w:tab/><w:t xml:space="preserve"> for &#8364;9</w:t></w:r></w:p>
        <w:p><w:r><w:t>Second</w:t><w:br/><w:t>line &lt;b&gt;</w:t></w:r></w:p>
        </w:body></w:document>
    """.trimIndent()

    @Test
    fun `runs join into paragraphs with entities decoded`() {
        val text = docxText(zip("[Content_Types].xml" to "<Types/>", "word/document.xml" to document), maxXmlBytes = 1 shl 20)
        assertEquals("Fish & chips  for €9\nSecond line <b>\n", text)
    }

    @Test
    fun `a zip with no document part has no text`() {
        assertNull(docxText(zip("xl/workbook.xml" to "<workbook/>"), maxXmlBytes = 1 shl 20))
    }

    @Test
    fun `the XML read is capped`() {
        val text = docxText(zip("word/document.xml" to document), maxXmlBytes = 120)
        assertEquals("Fish & chips", text!!.trim())
    }
}

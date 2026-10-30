package com.vipuljha.dupedigger.domain.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSimilarityTest {
    private val source = """
        The quarterly review covers revenue, hiring and the roadmap for the next two quarters. Revenue grew in every
        region, led by the enterprise segment, while consumer sales stayed flat after the spring promotion ended.
        Hiring slowed as planned, with most new roles in support and infrastructure. The roadmap keeps the offline
        mode and the new billing pages, and moves the analytics rebuild to the autumn so the team can finish the
        migration of the reporting database before the holiday freeze begins in December.
    """.trimIndent()

    @Test
    fun `words ignore case, punctuation and layout, in any script`() {
        assertEquals(listOf("café", "2024", "report", "отчёт"), normalizeWords("Café — 2024\nREPORT, отчёт!"))
    }

    @Test
    fun `layout and punctuation changes keep the SimHash`() {
        val reflowed = source.replace("\n", " ").replace(",", "").uppercase()
        assertEquals(simHash(source), simHash(reflowed))
    }

    @Test
    fun `an edited sentence is past the threshold, so edited documents aren't matched`() {
        val edited = source.replace("Hiring slowed as planned", "Hiring paused for a month")
        assertTrue(hammingDistance(simHash(source)!!, simHash(edited)!!) > DOCUMENT_SIMHASH_THRESHOLD)
    }

    @Test
    fun `a different text is far away`() {
        val other = """
            Preheat the oven and butter a deep tin. Whisk the eggs with the sugar until pale, fold in the flour and the
            melted butter, then the lemon zest. Bake for forty minutes until a skewer comes out clean, cool on a rack and
            dust with icing sugar before serving with cream and fresh berries from the garden.
        """.trimIndent()
        assertTrue(hammingDistance(simHash(source)!!, simHash(other)!!) > 10)
    }

    @Test
    fun `too little text has no SimHash`() {
        assertNull(simHash("Meeting notes: call back on Monday."))
    }

    @Test
    fun `the hash is stable across runs`() {
        assertEquals(stableHash("night drive"), stableHash("night drive"))
        assertTrue(stableHash("night drive") != stableHash("night drives"))
    }
}

package com.vipuljha.dupedigger.presentation.navigation

import com.vipuljha.dupedigger.presentation.navigation.PaneRole.Detail
import com.vipuljha.dupedigger.presentation.navigation.PaneRole.List
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListDetailIndicesTest {
    @Test
    fun `a list on top pairs with no detail`() {
        assertEquals(1 to null, listDetailIndices(listOf(null, List)))
    }

    @Test
    fun `a detail right above its list pairs with it, after Home's entries`() {
        assertEquals(2 to 3, listDetailIndices(listOf(null, null, List, Detail)))
    }

    @Test
    fun `a screen pushed over the detail falls back to one pane`() {
        assertNull(listDetailIndices(listOf(null, List, Detail, null)))
    }

    @Test
    fun `a detail without its list falls back to one pane`() {
        assertNull(listDetailIndices(listOf(null, Detail)))
        assertNull(listDetailIndices(listOf(Detail)))
    }

    @Test
    fun `stacks without a list stay single pane`() {
        assertNull(listDetailIndices(emptyList()))
        assertNull(listDetailIndices(listOf(null, null)))
    }
}

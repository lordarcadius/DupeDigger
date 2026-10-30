package com.vipuljha.dupedigger.presentation.compare

import com.vipuljha.dupedigger.domain.detector.toItem
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompareUiModelTest {
    private fun member(id: Long, path: String) = GroupMember(id, "file:$path", path, size = 100, mtime = 1, name = path.substringAfterLast('/'))

    private val group = GroupWithMembers(
        1, "NEAR:x", MatchTier.NEAR, MediaKind.IMAGE,
        persistentListOf(member(1, "/storage/emulated/0/DCIM/a.jpg"), member(2, "/storage/emulated/0/Download/a-small.jpg")),
        keepChoice = null, score = 0.95f,
    ).toItem()

    @Test
    fun `the two chosen files fill the panes and a near group shows its similarity`() {
        val ui = group.toCompareUi(leftId = 1, rightId = 2)!!

        assertEquals("a.jpg" to "a-small.jpg", ui.left.name to ui.right.name)
        assertEquals("file:/storage/emulated/0/Download/a-small.jpg", ui.right.uri)
        assertNotNull(ui.similarity)
    }

    @Test
    fun `a file that left the group leaves nothing to compare`() {
        assertNull(group.toCompareUi(leftId = 1, rightId = 9))
    }
}

package com.vipuljha.dupedigger.core.ui

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.common.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ContentStateTest {

    @Test
    fun `reloading keeps loaded content on screen`() {
        val loaded = ContentState.Success(listOf(1, 2))
        assertSame(loaded, loaded.reloading())
    }

    @Test
    fun `reloading an error marks it as retrying`() {
        val error = ContentState.Error(UiText.res(R.string.error_io))
        assertEquals(error.copy(retrying = true), error.reloading())
    }

    @Test
    fun `outcome maps to content state with an error message`() {
        val success: Outcome<Int> = Outcome.Success(2)
        assertEquals(ContentState.Success(4), success.map { it * 2 }.toContentState())

        val failure: Outcome<Int> = Outcome.Failure(DomainError.PermissionDenied)
        assertEquals(
            ContentState.Error(UiText.Resource(R.string.error_permission_denied)),
            failure.map { it * 2 }.toContentState(),
        )
    }
}

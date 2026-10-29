package com.vipuljha.dupedigger.core.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

    private data class CounterState(val count: Int = 0) : UiState

    private sealed interface CounterIntent : UiIntent {
        data object Increment : CounterIntent
        data class Announce(val value: Int) : CounterIntent
    }

    private data class Announced(val value: Int) : UiEffect

    private class CounterViewModel : MviViewModel<CounterState, CounterIntent, Announced>(CounterState()) {
        override fun handleIntent(intent: CounterIntent) {
            when (intent) {
                CounterIntent.Increment -> setState { copy(count = count + 1) }
                is CounterIntent.Announce -> sendEffect(Announced(intent.value))
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setState is atomic under concurrent intents`() {
        val viewModel = CounterViewModel()
        runBlocking(Dispatchers.Default) {
            repeat(8) {
                launch { repeat(5_000) { viewModel.onIntent(CounterIntent.Increment) } }
            }
        }
        assertEquals(40_000, viewModel.state.value.count)
    }

    @Test
    fun `effects sent before collection are each delivered exactly once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = CounterViewModel()

        (1..3).forEach { viewModel.onIntent(CounterIntent.Announce(it)) }
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3).map(::Announced), viewModel.effect.take(3).toList())
        assertNull(withTimeoutOrNull(1_000) { viewModel.effect.first() })
    }
}

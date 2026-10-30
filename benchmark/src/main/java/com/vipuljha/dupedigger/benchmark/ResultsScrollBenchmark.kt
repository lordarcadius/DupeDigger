package com.vipuljha.dupedigger.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while flinging through Results. Needs groups in the app's database: run the app's
 * `GroupPagingSourceTest#seedTheAppDatabaseForAScrollCheck` with `-e seedResults true` first, then install the
 * benchmark build over it (same signing key, so the data stays).
 */
@RunWith(AndroidJUnit4::class)
class ResultsScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun scrollResults() = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            passOnboarding()
        },
    ) {
        scrollResults()
    }
}

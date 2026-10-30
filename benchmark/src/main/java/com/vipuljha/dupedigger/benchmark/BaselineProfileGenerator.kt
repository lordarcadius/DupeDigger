package com.vipuljha.dupedigger.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code the main journeys run: start, Results with a scroll, Settings. Run it against the
 * `nonMinifiedBenchmark` build so the profile keeps source class names, then copy the output to
 * `app/src/main/baseline-prof.txt`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        passOnboarding()
        scrollResults()
        device.findObject(By.text("Settings"))?.click()
        device.waitForIdle()
    }
}

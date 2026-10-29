package com.vipuljha.dupedigger.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** Taps through onboarding when a fresh install shows it, so every measurement starts on Home. */
fun MacrobenchmarkScope.passOnboarding() {
    device.wait(Until.findObject(By.text("Continue")), 2_000)?.click()
    device.wait(Until.hasObject(By.text("Results")), 5_000)
}

/** Opens Results from the navigation panel and flings its list down and back up. */
fun MacrobenchmarkScope.scrollResults() {
    device.findObject(By.text("Results"))?.click()
    val list = device.wait(Until.findObject(By.scrollable(true)), 5_000) ?: error("Results has no list; seed it first")
    list.setGestureMargin(device.displayWidth / 5)
    repeat(3) { list.fling(Direction.DOWN) }
    repeat(3) { list.fling(Direction.UP) }
}

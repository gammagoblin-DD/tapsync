package com.example.tapsyncwatch.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope

/**
 * Small, resilient interactions that tend to work even when UI structure evolves.
 * The goal is to exercise hot code paths (Compose, input, drawing) without being
 * fragile to layout changes.
 */
fun MacrobenchmarkScope.tapCenter(times: Int = 1) {
    val x = device.displayWidth / 2
    val y = device.displayHeight / 2
    repeat(times) {
        device.click(x, y)
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.smallScroll() {
    val x = device.displayWidth / 2
    val yStart = (device.displayHeight * 0.75f).toInt()
    val yEnd = (device.displayHeight * 0.25f).toInt()
    device.swipe(x, yStart, x, yEnd, /*steps*/ 20)
    device.waitForIdle()
}

package com.example.tapsyncwatch.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = "com.example.tapsyncwatch") {
            // Start default activity for the app.
            pressHome()
            startActivityAndWait()

            // Exercise a tiny bit more than just first frame:
            // - Input path
            // - Compose recomposition
            // - A couple of frames of event/ring animation
            tapCenter(times = 3)

            // Touch a scrollable path (helps warm LazyColumn/scroll code paths)
            // This is intentionally generic: if the UI isn't scrollable yet, it should
            // still be harmless.
            smallScroll()
        }
    }
}

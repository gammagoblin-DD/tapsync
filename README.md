# Hotfix: '@Composable invocations can only happen from the context of a @Composable function'

Cause: MaterialTheme.colors is @Composable (Material2). It cannot be called inside Canvas draw scope.

Fix: read MaterialTheme.colors in the composable scope (outside Canvas) and capture the values.

Apply:
- Option A: overwrite FftGainKnob.kt from this zip
- Option B: git apply fft_gain_hotfix_composable_context.patch

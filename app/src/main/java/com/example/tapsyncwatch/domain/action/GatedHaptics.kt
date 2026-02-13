package com.example.tapsyncwatch.domain.action

/**
 * Simple gate so we can enable/disable transport haptics from settings without rebuilding ActionEngine.
 * Downbeat haptics are handled separately in the UI (TapScreen).
 */
class GatedHaptics(
    private val delegate: HapticEventListener
) : HapticEventListener {

    @Volatile
    var enabled: Boolean = true

    private inline fun runIfEnabled(block: () -> Unit) {
        if (enabled) block()
    }

    override fun onTap() = runIfEnabled { delegate.onTap() }
    override fun onMultiply() = runIfEnabled { delegate.onMultiply() }
    override fun onDivide() = runIfEnabled { delegate.onDivide() }
    override fun onResync() = runIfEnabled { delegate.onResync() }
    override fun onNudge() = runIfEnabled { delegate.onNudge() }
}

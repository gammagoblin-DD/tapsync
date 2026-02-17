package com.example.tapsyncwatch.domain.action

/**
 * Runtime gate so we can enable/disable haptics from settings without rebuilding ActionEngine.
 *
 * Semantics:
 * - masterEnabled controls ALL haptics.
 * - transportEnabled additionally controls transport-style events (resync / nudge).
 */
class GatedHaptics(
    private val delegate: HapticEventListener
) : HapticEventListener {

    @Volatile
    var masterEnabled: Boolean = true

    @Volatile
    var transportEnabled: Boolean = true

    private inline fun runIfMaster(block: () -> Unit) {
        if (masterEnabled) block()
    }

    private inline fun runIfTransport(block: () -> Unit) {
        if (masterEnabled && transportEnabled) block()
    }

    override fun onTap() = runIfMaster { delegate.onTap() }
    override fun onMultiply() = runIfMaster { delegate.onMultiply() }
    override fun onDivide() = runIfMaster { delegate.onDivide() }

    override fun onResync() = runIfTransport { delegate.onResync() }
    override fun onNudge() = runIfTransport { delegate.onNudge() }
}

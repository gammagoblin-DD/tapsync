package com.example.tapsyncwatch.presentation.ui

/**
 * Shared timeline primitives used by TapScreen + DebugScreen.
 * Keep these in one place to avoid redeclaration / visibility traps.
 */
internal enum class TimelineKind {
    LOCAL_TAP, LOCAL_MULTIPLY, LOCAL_DIVIDE, LOCAL_RESYNC, LOCAL_NUDGE,
    REMOTE_TAP, REMOTE_MULTIPLY, REMOTE_DIVIDE, REMOTE_RESYNC, REMOTE_NUDGE,
    PONG, OSC_OUT, OSC_ERR
}

internal data class TimelineEvent(
    val atMs: Long,
    val kind: TimelineKind
)

internal enum class TimelineLaneType { LOCAL, REMOTE, HEALTH }

internal data class TimelineLaneSpec(
    val type: TimelineLaneType,
    val alpha: Float
)

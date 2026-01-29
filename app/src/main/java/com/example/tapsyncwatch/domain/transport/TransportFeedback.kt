package com.example.tapsyncwatch.domain.transport

sealed interface TransportFeedback {
    object Tap : TransportFeedback
    object Resync : TransportFeedback
    object Multiply : TransportFeedback
    object Divide : TransportFeedback
    object NudgeStart : TransportFeedback
    object NudgeStop : TransportFeedback
}

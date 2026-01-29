package com.example.tapsyncwatch.osc

sealed interface OscHealth {
    object Idle : OscHealth
    data class Sending(val atMs: Long) : OscHealth
    data class Error(val error: Throwable?) : OscHealth
}

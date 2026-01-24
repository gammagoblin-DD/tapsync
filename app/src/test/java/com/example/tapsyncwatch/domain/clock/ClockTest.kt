package com.example.tapsyncwatch.domain.clock

import org.junit.Assert.*
import org.junit.Test

class ClockTest {

    @Test
    fun `phase advances linearly`() {
        val clock = Clock(initialBpm = 120.0)

        // 120 BPM → 500 ms pro Beat
        clock.handle(ClockEvent.Tick(deltaMs = 250))

        assertEquals(0.5, clock.state.phase, 0.0001)
    }

    @Test
    fun `phase wraps correctly`() {
        val clock = Clock(
            initialBpm = 120.0,
            initialPhase = 0.9
        )

        clock.handle(ClockEvent.Tick(deltaMs = 200)) // +0.4

        assertEquals(0.3, clock.state.phase, 0.0001)
    }

    @Test
    fun `multiply doubles bpm exactly`() {
        val clock = Clock(initialBpm = 120.0)

        clock.handle(ClockEvent.Multiply)

        assertEquals(240.0, clock.state.bpm, 0.0)
    }

    @Test
    fun `divide halves bpm exactly`() {
        val clock = Clock(initialBpm = 120.0)

        clock.handle(ClockEvent.Divide)

        assertEquals(60.0, clock.state.bpm, 0.0)
    }

    @Test
    fun `resync resets phase`() {
        val clock = Clock(initialPhase = 0.73)

        clock.handle(ClockEvent.Resync)

        assertEquals(0.0, clock.state.phase, 0.0)
    }

    @Test
    fun `external bpm does not change phase`() {
        val clock = Clock(
            initialBpm = 120.0,
            initialPhase = 0.42
        )

        clock.handle(ClockEvent.ExternalBpm(140.0))

        assertEquals(0.42, clock.state.phase, 0.0)
    }
}

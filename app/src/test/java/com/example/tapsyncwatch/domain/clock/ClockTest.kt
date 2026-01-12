package com.example.tapsyncwatch.domain.clock

import org.junit.Assert.*
import org.junit.Test

class ClockTest {

    private fun ns(ms: Long): Long = ms * 1_000_000

    @Test
    fun `BPM locks after 3 taps using median interval`() {
        val clock = Clock()

        // 120 BPM → 500 ms pro Beat
        clock.handle(ClockEvent.Tap(ns(0)))
        clock.handle(ClockEvent.Tap(ns(500)))
        clock.handle(ClockEvent.Tap(ns(1000)))

        val state = clock.state
        assertTrue(state.isLocked)
        assertEquals(120.0, state.bpm, 0.01)
        assertEquals(0.0, state.phase, 0.0)
    }

    @Test
    fun `BPM does not lock before 3 taps`() {
        val clock = Clock()

        clock.handle(ClockEvent.Tap(ns(0)))
        clock.handle(ClockEvent.Tap(ns(500)))

        assertFalse(clock.state.isLocked)
    }

    @Test
    fun `Multiply doubles BPM exactly`() {
        val clock = Clock()

        clock.handle(ClockEvent.Tap(ns(0)))
        clock.handle(ClockEvent.Tap(ns(500)))
        clock.handle(ClockEvent.Tap(ns(1000))) // 120 BPM

        clock.handle(ClockEvent.Multiply)

        assertEquals(240.0, clock.state.bpm, 0.01)
    }

    @Test
    fun `Divide halves BPM exactly`() {
        val clock = Clock()

        clock.handle(ClockEvent.Tap(ns(0)))
        clock.handle(ClockEvent.Tap(ns(500)))
        clock.handle(ClockEvent.Tap(ns(1000))) // 120 BPM

        clock.handle(ClockEvent.Divide)

        assertEquals(60.0, clock.state.bpm, 0.01)
    }

    @Test
    fun `Phase progresses correctly with Tick`() {
        val clock = Clock()

        clock.handle(ClockEvent.Tap(ns(0)))
        clock.handle(ClockEvent.Tap(ns(500)))
        clock.handle(ClockEvent.Tap(ns(1000))) // 120 BPM

        // 250 ms → half beat → phase ~0.5
        clock.handle(ClockEvent.Tick(ns(1250)))

        assertEquals(0.5, clock.state.phase, 0.05)
    }

    @Test
    fun `Resync resets phase to zero`() {
        val clock = Clock()

        clock.handle(ClockEvent.Tap(ns(0)))
        clock.handle(ClockEvent.Tap(ns(500)))
        clock.handle(ClockEvent.Tap(ns(1000)))

        clock.handle(ClockEvent.Tick(ns(1250)))
        assertTrue(clock.state.phase > 0.0)

        clock.handle(ClockEvent.Resync(ns(2000)))

        assertEquals(0.0, clock.state.phase, 0.0)
    }

    @Test
    fun `Nudge shifts phase by plus ten percent`() {
        val clock = Clock()

        clock.handle(ClockEvent.Tap(ns(0)))
        clock.handle(ClockEvent.Tap(ns(500)))
        clock.handle(ClockEvent.Tap(ns(1000)))

        clock.handle(ClockEvent.Tick(ns(1250))) // ~0.5
        val before = clock.state.phase

        clock.handle(ClockEvent.NudgeStart(+1))
        clock.handle(ClockEvent.Tick(ns(1250)))
        clock.handle(ClockEvent.NudgeEnd)

        val after = clock.state.phase
        assertEquals(before + 0.1, after, 0.05)
    }

    @Test
    fun `Nudge does not affect BPM`() {
        val clock = Clock()

        clock.handle(ClockEvent.Tap(ns(0)))
        clock.handle(ClockEvent.Tap(ns(500)))
        clock.handle(ClockEvent.Tap(ns(1000)))

        val bpmBefore = clock.state.bpm

        clock.handle(ClockEvent.NudgeStart(+1))
        clock.handle(ClockEvent.Tick(ns(1500)))
        clock.handle(ClockEvent.NudgeEnd)

        assertEquals(bpmBefore, clock.state.bpm, 0.0)
    }
}

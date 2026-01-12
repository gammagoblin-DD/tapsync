package com.example.tapsyncwatch.domain.clock

class TapHistory {

    private val taps = ArrayDeque<Long>(3)

    fun addTap(nowNs: Long) {
        taps.addLast(nowNs)
        if (taps.size > 3) taps.removeFirst()
    }

    fun isLocked(): Boolean = taps.size == 3

    fun medianIntervalNs(): Long {
        require(isLocked())

        val intervals = listOf(
            taps[1] - taps[0],
            taps[2] - taps[1]
        ).sorted()

        return intervals[intervals.size / 2]
    }

    fun clear() {
        taps.clear()
    }
}

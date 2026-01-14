package com.example.tapsyncwatch.domain.clock

class TapHistory {

    private val taps = ArrayDeque<Long>(5)

    fun addTap(nowNs: Long) {
        taps.addLast(nowNs)
        if (taps.size > 5) taps.removeFirst()
    }

    fun isLocked(): Boolean = taps.size >= 4

    fun averageIntervalNs(): Long {
        require(isLocked())

        val intervals = taps
            .zipWithNext { a, b -> b - a }
            .takeLast(4)

        return intervals.sum() / intervals.size
    }

    fun clear() {
        taps.clear()
    }
}

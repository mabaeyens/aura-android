package com.mab.aura.core.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide sliding-window limiter for outbound AEMET calls.
 *
 * AEMET caps a key at 50 requests/minute. A single cold refresh spends ~13 calls for one location and +4
 * per extra location, plus radar on demand — comfortably under the ceiling, but with no spacing a burst
 * could approach it. This allows bursts up to [limit] within [windowMillis] at full speed, then blocks the
 * next caller only until the oldest in-window request expires, so a normal refresh is never slowed and the
 * limit cannot be tripped. [limit] sits below 50 to leave headroom.
 *
 * Direct port of the Swift `RequestPacer` actor. Swift's actor gives mutual exclusion for free; the Kotlin
 * equivalent is a coroutine [Mutex] guarding the timestamp window. Like the Swift version this uses wall-clock
 * time ([System.currentTimeMillis]); a monotonic clock would be sturdier against clock changes, but parity with
 * the original is the priority and a clock jump only ever over- or under-paces briefly.
 */
class RequestPacer(
    private val limit: Int = 45,
    private val windowMillis: Long = 60_000,
) {
    companion object {
        /** The shared limiter every [AemetClient] funnels through, matching Swift's `RequestPacer.shared`. */
        val shared = RequestPacer()
    }

    private val mutex = Mutex()
    private val recent = ArrayDeque<Long>()

    /** Reserves the next slot, suspending only if [limit] requests already fired inside [windowMillis]. */
    suspend fun waitForSlot() {
        while (true) {
            val sleepMillis = mutex.withLock {
                val now = System.currentTimeMillis()
                while (recent.isNotEmpty() && now - recent.first() >= windowMillis) recent.removeFirst()
                if (recent.size < limit) {
                    recent.addLast(now)
                    return // slot reserved; non-local return out of waitForSlot (withLock is inline)
                }
                // Otherwise report how long until the oldest in-window request ages out, then retry.
                windowMillis - (now - recent.first())
            }
            if (sleepMillis > 0) delay(sleepMillis)
        }
    }
}

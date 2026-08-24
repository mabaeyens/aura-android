package com.mab.aura.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.Instant
import org.junit.Test

/**
 * Pins the ported `UVHourSlot` model and its list helpers (`current`, `todaySlots`, `todayMax`) from
 * `UVHourly.swift`. No Swift `UVHourSlotTests` exists, so these pin the window arithmetic directly. Also
 * round-trips one slot through JSON to prove the shared [com.mab.aura.core.serialization.InstantEpochMillisSerializer]
 * wired onto the `Instant` field works — the first persisted-instant type in the port.
 */
class UVHourSlotTest {

    private val base = Instant.ofEpochSecond(1_700_000_000)
    private fun slot(hour: Long, uv: Double) = UVHourSlot(base.plusSeconds(hour * 3600), uv, uv)

    @Test
    fun index_roundsToWholeWhoIndex() {
        assertEquals(3, UVHourSlot(base, uv = 3.4, clearSky = 3.4).index)
        assertEquals(4, UVHourSlot(base, uv = 3.5, clearSky = 3.5).index)
        assertEquals(0, UVHourSlot(base, uv = 0.0, clearSky = 0.0).index)
    }

    @Test
    fun current_isTheHourWindowContainingNow() {
        val slots = listOf(slot(0, 1.0), slot(1, 2.0), slot(2, 3.0))
        // Halfway through hour 0 → slot 0; exactly at hour 1's start → slot 1.
        assertEquals(slots[0], slots.current(base.plusSeconds(1800)))
        assertEquals(slots[1], slots.current(base.plusSeconds(3600)))
        // Before the feed starts, and past its end → null.
        assertNull(slots.current(base.minusSeconds(1)))
        assertNull(slots.current(base.plusSeconds(3 * 3600)))
    }

    @Test
    fun todaySlots_keepsTheFirst24HoursAnchoredOnTheFeed() {
        // 26 hourly slots; today is the first 24 (the run up to tomorrow's midnight boundary).
        val slots = (0 until 26L).map { slot(it, 1.0) }
        val today = slots.todaySlots()
        assertEquals(24, today.size)
        assertEquals(slots[0], today.first())
        assertEquals(slots[23], today.last())
    }

    @Test
    fun todaySlots_emptyListIsEmpty() {
        assertEquals(emptyList<UVHourSlot>(), emptyList<UVHourSlot>().todaySlots())
    }

    @Test
    fun todayMax_isThePeakUvAmongTodaySlots() {
        val slots = listOf(slot(0, 1.0), slot(5, 7.5), slot(10, 3.0), slot(30, 11.0)) // hour 30 is tomorrow
        val peak = slots.todayMax()
        assertEquals(7.5, peak?.uv)
    }

    @Test
    fun serialization_roundTripsThroughEpochMillis() {
        val original = UVHourSlot(base, uv = 6.2, clearSky = 8.1)
        val json = Json.encodeToString(UVHourSlot.serializer(), original)
        // The Instant serializes as epoch millis (a plain number), not an object.
        assertEquals(true, json.contains("${base.toEpochMilli()}"))
        assertEquals(original, Json.decodeFromString(UVHourSlot.serializer(), json))
    }
}

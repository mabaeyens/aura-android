package com.mab.aura.core.hero

import com.mab.aura.core.sky.SkyCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Parity port of `HeroBackgroundTests.swift`. */
class HeroBackgroundTest {

    // region The 8×6 grid, across families

    @Test
    fun gridIsCanonicalNames() {
        // Two families × 8 conditions × 6 times = 96, all unique; 48 per family.
        assertEquals(96, HeroBackground.allAssetNames.size)
        assertEquals("names must be unique", 96, HeroBackground.allAssetNames.toSet().size)
        assertEquals(48, HeroBackground.assetNames(HeroBackground.Family.LANDSCAPE).size)
        assertEquals(48, HeroBackground.assetNames(HeroBackground.Family.CITYSCAPE).size)
        assertTrue(HeroBackground.allAssetNames.contains("few_clouds_dawn"))
        assertTrue(HeroBackground.allAssetNames.contains("clear_night"))
        assertTrue(HeroBackground.allAssetNames.contains("city_stormy_afternoon"))
        assertFalse(HeroBackground.assetNames(HeroBackground.Family.LANDSCAPE).any { it.startsWith("city_") })
        assertTrue(HeroBackground.assetNames(HeroBackground.Family.CITYSCAPE).all { it.startsWith("city_") })
    }

    // region Family axis

    @Test
    fun cityscapeResolvesWithinItsFamily() {
        val have = setOf("city_clear_noon", "clear_noon")
        assertEquals(
            "city_clear_noon",
            HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NOON, HeroBackground.Family.CITYSCAPE, have),
        )
        assertEquals(
            "clear_noon",
            HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NOON, HeroBackground.Family.LANDSCAPE, have),
        )
    }

    @Test
    fun familyNeverLeaksAcross() {
        // Only landscape art exists; selecting cityscape must fall to procedural, not borrow landscape.
        val have = HeroBackground.assetNames(HeroBackground.Family.LANDSCAPE).toSet()
        assertNull(HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NOON, HeroBackground.Family.CITYSCAPE, have))
    }

    @Test
    fun cityscapeNearestTimeStaysInFamily() {
        // city noon missing; nearest city time wins, never the landscape noon that does exist.
        val have = setOf("city_clear_morning", "clear_noon")
        assertEquals(
            "city_clear_morning",
            HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NOON, HeroBackground.Family.CITYSCAPE, have),
        )
    }

    @Test
    fun familyStorageDecodeFallsBackToLandscape() {
        assertEquals(HeroBackground.Family.CITYSCAPE, HeroBackground.Family.from("cityscape"))
        assertEquals(HeroBackground.Family.LANDSCAPE, HeroBackground.Family.from("landscape"))
        assertEquals(HeroBackground.Family.LANDSCAPE, HeroBackground.Family.from(null))
        assertEquals(HeroBackground.Family.LANDSCAPE, HeroBackground.Family.from("bogus"))
    }

    // region Resolver chain

    @Test
    fun exactMatchWins() {
        val have = setOf("clear_noon", "clear_dawn")
        assertEquals("clear_noon", HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NOON, available = have))
    }

    @Test
    fun nearestTimeSameCondition() {
        // noon missing; morning (distance 1) beats night (distance 3).
        val have = setOf("clear_morning", "clear_night")
        assertEquals("clear_morning", HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NOON, available = have))
    }

    @Test
    fun nearestWrapsAroundTheDay() {
        // night missing; dawn is a neighbour of night across the cycle.
        val have = setOf("clear_dawn")
        assertEquals("clear_dawn", HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NIGHT, available = have))
    }

    @Test
    fun neverBorrowsAnotherCondition() {
        // Only rainy art exists; a clear sky must not use it — it falls to procedural (null).
        val have = setOf("rainy_noon", "rainy_dawn")
        assertNull(HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NOON, available = have))
    }

    @Test
    fun unknownSkyIsProcedural() {
        val have = HeroBackground.allAssetNames.toSet()
        assertNull(HeroBackground.resolve(SkyCategory.UNKNOWN, HeroBackground.Time.NOON, available = have))
    }

    @Test
    fun emptyBundleIsProcedural() {
        assertNull(HeroBackground.resolve(SkyCategory.CLEAR, HeroBackground.Time.NOON, available = emptySet()))
    }

    @Test
    fun conditionTokens() {
        assertEquals("cloudy", HeroBackground.Condition.from(SkyCategory.CLOUDS)?.token)
        assertEquals("few_clouds", HeroBackground.Condition.from(SkyCategory.FEW_CLOUDS)?.token)
        assertEquals("rainy", HeroBackground.Condition.from(SkyCategory.RAIN)?.token)
        assertNull(HeroBackground.Condition.from(SkyCategory.UNKNOWN))
    }

    // region Wide per-condition grid

    @Test
    fun wideGridNamesAndSceneToken() {
        assertEquals("wide_landscape_clear_dawn", HeroBackground.wideAssetName(HeroBackground.Family.LANDSCAPE, HeroBackground.Condition.CLEAR, HeroBackground.Time.DAWN))
        assertEquals("wide_city_stormy_night", HeroBackground.wideAssetName(HeroBackground.Family.CITYSCAPE, HeroBackground.Condition.STORMY, HeroBackground.Time.NIGHT))
        assertEquals(48, HeroBackground.wideAssetNames(HeroBackground.Family.LANDSCAPE).size)
        assertEquals(48, HeroBackground.wideAssetNames(HeroBackground.Family.CITYSCAPE).toSet().size)
        assertTrue(HeroBackground.wideAssetNames(HeroBackground.Family.CITYSCAPE).all { it.startsWith("wide_city_") })
    }

    // region Time buckets from the sun path

    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0): Instant =
        LocalDateTime.of(2026, 8, 24, h, m).toInstant(ZoneOffset.UTC)

    private fun time(now: Instant, sunrise: Instant?, sunset: Instant?): HeroBackground.Time =
        HeroBackground.Time.from(now, sunrise, sunset, zone)

    @Test
    fun timeBucketsTrackTheSun() {
        val sunrise = at(7)
        val sunset = at(21) // a 14-hour day
        assertEquals(HeroBackground.Time.DAWN, time(at(7, 20), sunrise, sunset))
        assertEquals(HeroBackground.Time.MORNING, time(at(10), sunrise, sunset))
        assertEquals(HeroBackground.Time.NOON, time(at(14), sunrise, sunset))
        assertEquals(HeroBackground.Time.AFTERNOON, time(at(18, 30), sunrise, sunset))
        assertEquals(HeroBackground.Time.DUSK, time(at(20, 40), sunrise, sunset))
        assertEquals(HeroBackground.Time.NIGHT, time(at(23), sunrise, sunset))
    }

    @Test
    fun timeBucketWithoutSunTimesFallsBackToClockHour() {
        assertEquals(HeroBackground.Time.NIGHT, time(at(3), null, null))
        assertEquals(HeroBackground.Time.DAWN, time(at(7), null, null))
        assertEquals(HeroBackground.Time.MORNING, time(at(10), null, null))
        assertEquals(HeroBackground.Time.NOON, time(at(13), null, null))
        assertEquals(HeroBackground.Time.AFTERNOON, time(at(17, 17), null, null))
        assertEquals(HeroBackground.Time.DUSK, time(at(20), null, null))
        assertEquals(HeroBackground.Time.NIGHT, time(at(23), null, null))
    }
}

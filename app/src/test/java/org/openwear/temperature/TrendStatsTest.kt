package org.openwear.temperature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendStatsTest {

    @Test
    fun summarizeEmptyHistory() {
        val summary = TrendStats.summarize(emptyList())
        assertEquals(0, summary.count)
        assertEquals(null, summary.mean)
        assertEquals(null, summary.min)
        assertEquals(null, summary.max)
    }

    @Test
    fun summarizeComputesMeanAndRange() {
        val records = listOf(
            TemperatureRecord(1_000L, 33.0f),
            TemperatureRecord(2_000L, 35.0f),
            TemperatureRecord(3_000L, 34.0f)
        )
        val summary = TrendStats.summarize(records)
        assertEquals(3, summary.count)
        assertEquals(34.0f, summary.mean!!, 0.001f)
        assertEquals(33.0f, summary.min!!, 0.001f)
        assertEquals(35.0f, summary.max!!, 0.001f)
    }

    @Test
    fun bucketKeepsEveryRecordWhenBelowLimit() {
        val records = (1..5).map { TemperatureRecord(it * 1000L, 30f + it) }
        val points = TrendStats.bucket(records, 24)
        assertEquals(5, points.size)
        assertEquals(records.map { it.timeMillis }, points.map { it.startMillis })
    }

    @Test
    fun bucketAggregatesLongHistory() {
        val records = (1..100).map { TemperatureRecord(it * 60_000L, 30f) }
        val points = TrendStats.bucket(records, 10)
        assertTrue("期望不超过 10 个点，实际 ${points.size}", points.size <= 10)
        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.sampleCount > 0 })
    }

    @Test
    fun bucketSortsByTime() {
        val records = listOf(
            TemperatureRecord(3_000L, 30f),
            TemperatureRecord(1_000L, 31f),
            TemperatureRecord(2_000L, 32f)
        )
        val points = TrendStats.bucket(records, 24)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), points.map { it.startMillis })
    }
}

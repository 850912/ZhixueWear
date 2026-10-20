package org.openwear.temperature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureQualityTest {
    @Test
    fun acceptsFiniteInRangeSamplesWithPositiveAccuracy() {
        assertTrue(TemperatureQuality.isValidSample(34.2f, 24.0f))
        assertFalse(TemperatureQuality.isValidSample(Float.NaN, 24.0f))
        assertFalse(TemperatureQuality.isValidSample(46.0f, 24.0f))
    }

    @Test
    fun requiresFiveSamplesAndReportsStableSkinTemperatureOnly() {
        val four = samples(listOf(34.0f, 34.1f, 34.1f, 34.0f))
        assertNull(TemperatureQuality.summarize(four))

        val five = samples(listOf(34.0f, 34.1f, 34.1f, 34.0f, 34.2f))
        val result = TemperatureQuality.summarize(five)
        assertNotNull(result)
        assertEquals(34.08f, result!!.meanCelsius!!, 0.001f)
        assertEquals(0.2f, result.spreadCelsius!!, 0.001f)
        assertTrue(result.detail.contains("不代表核心体温"))
    }

    @Test
    fun unstableMeasurementNeverReturnsAValueAsIfItWereReliable() {
        val readings = (1..TemperatureQuality.MAX_SAMPLES)
            .map { if (it % 2 == 0) 33.0f else 34.0f }
        val result = TemperatureQuality.summarize(samples(readings))
        assertNotNull(result)
        assertNull(result!!.meanCelsius)
        assertTrue(result.detail.contains("不稳定"))
    }

    private fun samples(values: List<Float>) = values.mapIndexed { index, value ->
        TemperatureSample(value, 24.0f, index.toLong())
    }
}

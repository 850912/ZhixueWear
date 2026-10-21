package org.openwear.temperature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureQualityTest {
    @Test
    fun acceptsFiniteInRangeSamples() {
        assertTrue(TemperatureQuality.isValidSample(34.2f, 24.0f, 34.0f))
        assertFalse(TemperatureQuality.isValidSample(Float.NaN, 24.0f, 34.0f))
        assertFalse(TemperatureQuality.isValidSample(46.0f, 24.0f, 34.0f))
        assertFalse(TemperatureQuality.isValidSample(34.2f, 24.0f, 99.0f))
    }

    @Test
    fun rejectsSampleWhenSkinIsNotAboveAmbient() {
        assertFalse(TemperatureQuality.isValidSample(23.0f, 24.0f, 23.0f))
    }

    @Test
    fun quickModeReturnsFirstValidSample() {
        val one = samples(listOf(34.0f))
        val result = TemperatureQuality.summarize(one, MeasurementMode.QUICK)
        assertNotNull(result)
        assertEquals(34.0f, result!!.meanCelsius!!, 0.001f)
        assertTrue(result.detail.contains("快速测量"))
    }

    @Test
    fun accurateModeRequiresFiveStableSamples() {
        val four = samples(listOf(34.0f, 34.1f, 34.2f, 34.1f))
        assertNull(TemperatureQuality.summarize(four, MeasurementMode.ACCURATE))

        val five = samples(listOf(34.0f, 34.1f, 34.2f, 34.1f, 34.0f))
        val result = TemperatureQuality.summarize(five, MeasurementMode.ACCURATE)
        assertNotNull(result)
        assertEquals(34.08f, result!!.meanCelsius!!, 0.01f)
        assertEquals(0.2f, result.spreadCelsius!!, 0.001f)
        assertTrue(result.detail.contains("准确测量"))
    }

    @Test
    fun unstableAccurateMeasurementNeverReturnsValue() {
        val readings = (1..TemperatureQuality.MAX_SAMPLES)
            .map { if (it % 2 == 0) 33.0f else 34.0f }
        val result = TemperatureQuality.summarize(samples(readings), MeasurementMode.ACCURATE)
        assertNotNull(result)
        assertNull(result!!.meanCelsius)
        assertTrue(result.detail.contains("不稳定"))
    }

    private fun samples(values: List<Float>) = values.mapIndexed { index, value ->
        TemperatureSample(value, 24.0f, value, index.toLong())
    }
}

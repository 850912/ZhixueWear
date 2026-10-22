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
        assertTrue(TemperatureQuality.isValidSample(34.2f, 24.0f))
        assertFalse(TemperatureQuality.isValidSample(Float.NaN, 24.0f))
        assertFalse(TemperatureQuality.isValidSample(46.0f, 24.0f))
        assertFalse(TemperatureQuality.isValidSample(34.2f, Float.NaN))
    }

    @Test
    fun rejectsSampleWhenSkinIsNotAboveAmbient() {
        assertFalse(TemperatureQuality.isValidSample(23.0f, 24.0f))
        assertTrue(TemperatureQuality.isValidSample(25.5f, 24.0f))
    }

    @Test
    fun acceptsColdAmbientEnvironment() {
        // 冬天室内 10°C 属于正常环境，不应该把所有样本都判为无效。
        assertTrue(TemperatureQuality.isValidSample(30.5f, 10.0f))
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
        assertNull(result)
    }

    @Test
    fun wearStateDetectsOffWristReading() {
        // 皮肤温度与环境温度接近 -> 手表没有贴在手腕上。
        val offWrist = TemperatureQuality.wearState(24.5f, 24.0f, isMoving = false)
        assertFalse(offWrist.isWearing)

        val onWrist = TemperatureQuality.wearState(33.5f, 24.0f, isMoving = false)
        assertTrue(onWrist.isWearing)
    }

    @Test
    fun wearStateReportsMotion() {
        val moving = TemperatureQuality.wearState(33.5f, 24.0f, isMoving = true)
        assertTrue(moving.isWearing)
        assertTrue(moving.isMoving)
        assertTrue(moving.reason.contains("运动"))
    }

    @Test
    fun timeoutsAreConsistentWithUiEstimate() {
        assertTrue(TemperatureQuality.timeoutMs(MeasurementMode.QUICK) > 0)
        assertTrue(
            TemperatureQuality.unstableAfterMs(MeasurementMode.ACCURATE) <
                TemperatureQuality.timeoutMs(MeasurementMode.ACCURATE)
        )
    }

    private fun samples(values: List<Float>) = values.mapIndexed { index, value ->
        TemperatureSample(value, 24.0f, index.toLong())
    }
}
